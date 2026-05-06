package id.ac.ui.cs.advprog.yomu.achievements.internal.service;

import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.*;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.*;
import id.ac.ui.cs.advprog.yomu.achievements.internal.repository.AchievementRepository;
import id.ac.ui.cs.advprog.yomu.shared.event.AchievementUnlockedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.LeagueActivityEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.LearningCompletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.QuizCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Implementasi AchievementService yang menangani logika bisnis
 * untuk achievements dan daily missions.
 * Mengikuti prinsip Single Responsibility: hanya mengelola
 * achievement progress dan daily mission progress.
 */
@Service
public class AchievementServiceImpl implements AchievementService {

    private final AchievementRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public AchievementServiceImpl(
            AchievementRepository repository,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public AchievementResponse createAchievement(CreateAchievementRequest request) {
        if (repository.existsByAchievementCode(request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Achievement dengan kode '" + request.code() + "' sudah ada");
        }

        Achievement achievement = new Achievement(
            UUID.randomUUID(),
            request.code(),
            request.name(),
            request.description(),
            request.metric(),
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
        if (repository.existsByDailyMissionCode(request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Daily mission dengan kode '" + request.code() + "' sudah ada");
        }

        DailyMission mission = new DailyMission(
            UUID.randomUUID(),
            request.code(),
            request.name(),
            request.description(),
            request.metric(),
            request.targetCount(),
            request.rewardPoints(),
            request.activeFrom() != null ? request.activeFrom() : LocalDate.now(),
            request.activeUntil() != null ? request.activeUntil() : LocalDate.now().plusDays(1),
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
    public void rotateDailyMissions() {
        // Rotasi dilakukan oleh scheduler; logic seed di sini jika diperlukan
    }

    // ─── Private Helpers ──────────────────────────────────────────────

    private void updateAchievementAndMissionProgress(UUID userId, AchievementMetric metric) {
        // Update achievement progress
        List<Achievement> achievements = repository.findActiveAchievementsByMetric(metric);
        for (Achievement achievement : achievements) {
            AchievementProgressState state = repository
                .findAchievementProgressState(userId, achievement.id())
                .orElse(new AchievementProgressState(0, null));

            if (state.unlockedAt() != null) {
                continue; // sudah tercapai
            }

            int newCount = state.progressCount() + 1;
            Instant unlockedAt = newCount >= achievement.milestone() ? Instant.now() : null;

            repository.saveAchievementProgress(userId, achievement.id(), newCount, unlockedAt);

            if (unlockedAt != null) {
                eventPublisher.publishEvent(new AchievementUnlockedEvent(
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
            p.progressCount(), p.unlockedAt()
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
            p.progressCount(), p.claimedAt()
        );
    }
}
