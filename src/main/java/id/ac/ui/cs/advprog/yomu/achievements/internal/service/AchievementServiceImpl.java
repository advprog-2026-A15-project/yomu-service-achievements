package id.ac.ui.cs.advprog.yomu.achievements.internal.service;

import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.*;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.*;
import id.ac.ui.cs.advprog.yomu.achievements.internal.repository.AchievementRepository;
import id.ac.ui.cs.advprog.yomu.shared.event.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Implementasi AchievementService yang menangani logika bisnis
 * untuk achievements dan daily missions.
 * Mengikuti prinsip Single Responsibility: hanya mengelola
 * achievement progress dan daily mission progress.
 */
@Service
public class AchievementServiceImpl implements AchievementService {
    private static final int DEFAULT_DAILY_MISSION_REWARD = 10;

    private final AchievementRepository repository;
    private final RabbitTemplate rabbitTemplate;

    public AchievementServiceImpl(
            AchievementRepository repository,
            RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional
    public AchievementResponse createAchievement(CreateAchievementRequest request) {
        String code = resolveCode(request.code(), request.name());
        AchievementMetric metric = requireMetric(request.metric());
        String description = normalizeNullableText(request.description());

        if (repository.existsByAchievementCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Achievement dengan kode '" + code + "' sudah ada");
        }

        Achievement achievement = new Achievement(
            UUID.randomUUID(),
            code,
            request.name().trim(),
            description,
            metric,
            request.milestone(),
            true,
            Instant.now()
        );

        repository.saveAchievement(achievement);
        return toResponse(achievement);
    }

    @Override
    public List<AchievementProgressResponse> listAchievementProgress(UUID userId) {
        return repository.findAchievementProgressForUser(userId).stream()
            .map(this::toProgressResponse)
            .toList();
    }

    @Override
    @Transactional
    public DailyMissionResponse createDailyMission(CreateDailyMissionRequest request) {
        String code = resolveCode(request.code(), request.name());
        AchievementMetric metric = requireMetric(request.metric());
        String description = normalizeNullableText(request.description());
        int rewardPoints = request.rewardPoints() == null ? 0 : request.rewardPoints();
        LocalDate activeFrom = request.activeFrom() != null ? request.activeFrom() : LocalDate.now();
        LocalDate activeUntil = request.activeUntil() != null ? request.activeUntil() : activeFrom.plusDays(1);

        if (!activeUntil.isAfter(activeFrom)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Tanggal selesai daily mission harus setelah tanggal mulai");
        }

        if (repository.existsByDailyMissionCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Daily mission dengan kode '" + code + "' sudah ada");
        }

        DailyMission mission = new DailyMission(
            UUID.randomUUID(),
            code,
            request.name().trim(),
            description,
            metric,
            request.targetCount(),
            rewardPoints,
            activeFrom,
            activeUntil,
            Instant.now()
        );

        repository.saveDailyMission(mission);
        return toMissionResponse(mission);
    }

    @Override
    public List<DailyMissionProgressResponse> listActiveDailyMissions(UUID userId) {
        LocalDate today = LocalDate.now();
        return repository.findActiveDailyMissionProgressForUser(userId, today).stream()
            .map(this::toMissionProgressResponse)
            .toList();
    }

    @Override
    @Transactional
    public ClaimRewardResponse claimDailyMissionReward(UUID missionId, UUID userId) {
        DailyMission mission = repository.findDailyMissionById(missionId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Daily mission tidak ditemukan"));

        DailyMissionProgressState state = repository
            .findDailyMissionProgressState(userId, missionId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Belum ada progres untuk misi ini"));

        if (state.claimedAt() != null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Reward sudah diklaim");
        }

        if (state.progressCount() < mission.targetCount()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Misi belum selesai");
        }

        Instant now = Instant.now();
        repository.saveDailyMissionProgress(userId, missionId, state.progressCount(), now);

        return new ClaimRewardResponse(
            missionId,
            mission.name(),
            mission.rewardPoints(),
            true,
            now
        );
    }

    @Override
    @Transactional
    public void pinAchievement(UUID userId, UUID achievementId, boolean pin) {
        AchievementProgressState state = repository.findAchievementProgressState(userId, achievementId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Progres achievement tidak ditemukan"));

        if (state.unlockedAt() == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Hanya achievement yang sudah terbuka yang dapat dipajang");
        }

        repository.pinAchievement(userId, achievementId, pin);
    }

    @Override
    @Transactional
    public void recordReadingCompleted(LearningCompletedEvent event) {
        String sourceId = event.bacaanId().toString();
        boolean isNewEvent = repository.saveActivityEvent(
            event.userId(), AchievementMetric.READING_COMPLETED,
            sourceId, event.occurredAt()
        );

        if (isNewEvent) {
            updateAchievementAndMissionProgress(
                event.userId(), AchievementMetric.READING_COMPLETED
            );
        }
    }

    @Override
    @Transactional
    public void recordQuizCompleted(QuizCompletedEvent event) {
        String sourceId = event.quizId().toString();
        boolean isNewEvent = repository.saveActivityEvent(
            event.userId(), AchievementMetric.QUIZ_COMPLETED,
            sourceId, event.occurredAt()
        );

        if (isNewEvent) {
            updateAchievementAndMissionProgress(
                event.userId(), AchievementMetric.QUIZ_COMPLETED
            );
        }
    }

    @Override
    @Transactional
    public void recordLeagueActivity(LeagueActivityEvent event) {
        String sourceId = event.activityId().toString();
        boolean isNewEvent = repository.saveActivityEvent(
            event.userId(), AchievementMetric.LEAGUE_ACTIVITY,
            sourceId, event.occurredAt()
        );

        if (isNewEvent) {
            updateAchievementAndMissionProgress(
                event.userId(), AchievementMetric.LEAGUE_ACTIVITY
            );
        }
    }

    @Override
    @Transactional
    public void recordCommentCreated(CommentCreatedEvent event) {
        UUID userId = UUID.fromString(event.userId());
        boolean isNewEvent = repository.saveActivityEvent(
            userId,
            AchievementMetric.COMMENT_CREATED,
            event.commentId(),
            event.timestamp()
        );

        if (isNewEvent) {
            updateAchievementAndMissionProgress(userId, AchievementMetric.COMMENT_CREATED);
        }
    }

    @Override
    @Transactional
    public void recordClanPromoted(ClanPromotedEvent event) {
        String sourceId = event.seasonId() + ":" + event.clanId() + ":" + event.userId() + ":" + event.newTier();
        boolean isNewPromotion = repository.saveActivityEvent(
            event.userId(), AchievementMetric.CLAN_PROMOTED,
            sourceId, event.occurredAt()
        );

        if (isNewPromotion) {
            updateAchievementAndMissionProgress(event.userId(), AchievementMetric.CLAN_PROMOTED);
        }

        if ("DIAMOND".equalsIgnoreCase(event.newTier())) {
            boolean isNewDiamondEvent = repository.saveActivityEvent(
                event.userId(), AchievementMetric.CLAN_REACHED_DIAMOND,
                sourceId, event.occurredAt()
            );

            if (isNewDiamondEvent) {
                updateAchievementAndMissionProgress(event.userId(), AchievementMetric.CLAN_REACHED_DIAMOND);
            }
        }
    }

    @Override
    @Transactional
    public void rotateDailyMissions() {
        LocalDate today = LocalDate.now();
        if (repository.hasActiveDailyMissionOn(today)) {
            return;
        }

        DailyMission mission = new DailyMission(
            UUID.randomUUID(),
            "DAILY_QUIZ_" + today.format(DateTimeFormatter.BASIC_ISO_DATE),
            "Kuis Harian",
            "Selesaikan satu kuis hari ini.",
            AchievementMetric.QUIZ_COMPLETED,
            1,
            DEFAULT_DAILY_MISSION_REWARD,
            today,
            today.plusDays(1),
            Instant.now()
        );

        repository.saveDailyMission(mission);
    }

    // ─── Private Helpers ──────────────────────────────────────────────

    private void updateAchievementAndMissionProgress(UUID userId, AchievementMetric metric) {
        // Update achievement progress
        List<Achievement> achievements = repository.findActiveAchievementsByMetric(metric);
        for (Achievement achievement : achievements) {
            AchievementProgressState state = repository
                .findAchievementProgressState(userId, achievement.id())
                .orElse(new AchievementProgressState(0, null, false));

            if (state.unlockedAt() != null) {
                continue; // sudah tercapai
            }

            int newCount = state.progressCount() + 1;
            Instant unlockedAt = newCount >= achievement.milestone() ? Instant.now() : null;

            repository.saveAchievementProgress(userId, achievement.id(), newCount, unlockedAt);

            if (unlockedAt != null) {
                rabbitTemplate.convertAndSend("yomu.achievement.unlocked", new AchievementUnlockedEvent(
                    userId, achievement.code(), achievement.name(), unlockedAt
                ));
            }
        }

        // Update daily mission progress
        LocalDate today = LocalDate.now();
        List<DailyMission> missions = repository.findActiveDailyMissionsByMetric(metric, today);
        for (DailyMission mission : missions) {
            DailyMissionProgressState state = repository
                .findDailyMissionProgressState(userId, mission.id())
                .orElse(new DailyMissionProgressState(0, null));

            if (state.claimedAt() != null) {
                continue; // sudah diklaim
            }

            int newCount = state.progressCount() + 1;
            repository.saveDailyMissionProgress(userId, mission.id(), newCount, null);

            if (state.progressCount() < mission.targetCount()
                && newCount >= mission.targetCount()) {
                rabbitTemplate.convertAndSend("yomu.daily.mission.completed", new DailyMissionCompletedEvent(
                    userId, mission.id(), mission.name(), Instant.now()
                ));
            }
        }
    }

    private AchievementResponse toResponse(Achievement a) {
        return new AchievementResponse(
            a.id(), a.code(), a.name(), a.description(),
            a.metric().name(), a.milestone(), a.createdAt()
        );
    }

    private AchievementProgressResponse toProgressResponse(AchievementProgress p) {
        Achievement a = p.achievement();
        return new AchievementProgressResponse(
            a.id(), a.code(), a.name(), a.description(),
            a.metric().name(), a.milestone(),
<<<<<<< HEAD
            p.progressCount(), p.unlocked(), p.unlockedAt()
=======
            p.progressCount(), p.unlockedAt(), p.isPinned()
>>>>>>> 7041e58ccda4e11d52ef656ca3bfcb0f79ef32cc
        );
    }

    private DailyMissionResponse toMissionResponse(DailyMission m) {
        return new DailyMissionResponse(
            m.id(), m.code(), m.name(), m.description(),
            m.metric().name(), m.targetCount(), m.rewardPoints(),
            m.activeFrom(), m.activeUntil()
        );
    }

    private DailyMissionProgressResponse toMissionProgressResponse(DailyMissionProgress p) {
        DailyMission m = p.mission();
        return new DailyMissionProgressResponse(
            m.id(), m.code(), m.name(), m.description(),
            m.metric().name(), m.targetCount(), m.rewardPoints(),
            p.progressCount(), p.completed(), p.claimed(), p.claimedAt()
        );
    }

    private AchievementMetric requireMetric(AchievementMetric metric) {
        if (metric == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Metric wajib diisi");
        }
        return metric;
    }

    private String normalizeNullableText(String value) {
        return value == null ? "" : value.trim();
    }

    private String resolveCode(String providedCode, String fallbackName) {
        String rawCode = hasText(providedCode) ? providedCode : fallbackName;
        if (!hasText(rawCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Kode wajib diisi");
        }

        String normalized = rawCode.trim()
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("^_+|_+$", "");

        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Kode tidak valid");
        }
        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
