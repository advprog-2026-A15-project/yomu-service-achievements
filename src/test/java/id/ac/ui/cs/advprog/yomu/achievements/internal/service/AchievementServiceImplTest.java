package id.ac.ui.cs.advprog.yomu.achievements.internal.service;

import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.AchievementProgressResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.ClaimRewardResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.DailyMissionProgressResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.Achievement;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.AchievementMetric;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.AchievementProgress;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.AchievementProgressState;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.DailyMission;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.DailyMissionProgress;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.DailyMissionProgressState;
import id.ac.ui.cs.advprog.yomu.achievements.internal.repository.AchievementRepository;
import id.ac.ui.cs.advprog.yomu.shared.event.AchievementUnlockedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.ClanPromotedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentCreatedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.DailyMissionCompletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.LeagueActivityEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.LearningCompletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.QuizCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AchievementServiceImplTest {

    private InMemoryAchievementRepository repository;
    private CapturingRabbitTemplate rabbitTemplate;
    private AchievementServiceImpl service;
    private Achievement firstRead;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAchievementRepository();
        rabbitTemplate = new CapturingRabbitTemplate();
        service = new AchievementServiceImpl(repository, rabbitTemplate);

        firstRead = new Achievement(
            UUID.randomUUID(),
            "FIRST_READ",
            "First Read",
            "Menyelesaikan kuis pertama.",
            AchievementMetric.QUIZ_COMPLETED,
            1,
            true,
            Instant.now()
        );
        repository.saveAchievement(firstRead);
    }

    @Test
    void quizCompletedUnlocksFirstReadAndPublishesEvent() {
        UUID userId = UUID.randomUUID();

        service.recordQuizCompleted(new QuizCompletedEvent(
            userId,
            UUID.randomUUID(),
            "quiz-1",
            80,
            5,
            Instant.now()
        ));

        AchievementProgressResponse progress = service.listAchievementProgress(userId).getFirst();

        assertThat(progress.code()).isEqualTo("FIRST_READ");
        assertThat(progress.progress()).isEqualTo(1);
        assertThat(progress.unlockedAt()).isNotNull();

        assertThat(rabbitTemplate.publishedEvents)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.routingKey()).isEqualTo("yomu.achievement.unlocked");
                assertThat(event.message()).isInstanceOf(AchievementUnlockedEvent.class);
                AchievementUnlockedEvent unlockedEvent = (AchievementUnlockedEvent) event.message();
                assertThat(unlockedEvent.userId()).isEqualTo(userId);
                assertThat(unlockedEvent.achievementCode()).isEqualTo("FIRST_READ");
            });
    }

    @Test
    void duplicateQuizCompletedEventIsIdempotent() {
        UUID userId = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        QuizCompletedEvent event = new QuizCompletedEvent(
            userId,
            quizId,
            "quiz-1",
            80,
            5,
            Instant.now()
        );

        service.recordQuizCompleted(event);
        service.recordQuizCompleted(event);

        AchievementProgressResponse progress = service.listAchievementProgress(userId).getFirst();

        assertThat(progress.progress()).isEqualTo(1);
        assertThat(rabbitTemplate.publishedEvents).hasSize(1);
    }

    @Test
    void completedDailyMissionPublishesCompletionEvent() {
        UUID userId = UUID.randomUUID();
        DailyMission mission = new DailyMission(
            UUID.randomUUID(),
            "DAILY_QUIZ",
            "Kuis Harian",
            "Selesaikan satu kuis.",
            AchievementMetric.QUIZ_COMPLETED,
            1,
            10,
            LocalDate.now(),
            LocalDate.now().plusDays(1),
            Instant.now()
        );
        repository.saveDailyMission(mission);

        service.recordQuizCompleted(new QuizCompletedEvent(
            userId,
            UUID.randomUUID(),
            "quiz-1",
            90,
            5,
            Instant.now()
        ));

        DailyMissionProgressResponse missionProgress = service
            .listActiveDailyMissions(userId)
            .getFirst();

        assertThat(missionProgress.completed()).isTrue();
        assertThat(missionProgress.claimed()).isFalse();
        assertThat(rabbitTemplate.publishedEvents)
            .anySatisfy(event -> {
                assertThat(event.routingKey()).isEqualTo("yomu.daily.mission.completed");
                assertThat(event.message()).isInstanceOf(DailyMissionCompletedEvent.class);
            });
    }

    @Test
    void rotateDailyMissionsCreatesOneDefaultMissionWhenNoMissionIsActive() {
        UUID userId = UUID.randomUUID();

        service.rotateDailyMissions();
        service.rotateDailyMissions();

        List<DailyMissionProgressResponse> activeMissions = service.listActiveDailyMissions(userId);

        assertThat(activeMissions).hasSize(1);
        assertThat(activeMissions.getFirst().metric()).isEqualTo(AchievementMetric.QUIZ_COMPLETED.name());
        assertThat(activeMissions.getFirst().targetCount()).isEqualTo(1);
    }

    @Test
    void quizCompleted_incrementsProgressWithoutUnlock() {
        UUID userId = UUID.randomUUID();
        repository.saveAchievement(new Achievement(
            UUID.randomUUID(),
            "DOUBLE_QUIZ",
            "Double Quiz",
            "Selesaikan dua kuis.",
            AchievementMetric.QUIZ_COMPLETED,
            2,
            true,
            Instant.now()
        ));

        service.recordQuizCompleted(new QuizCompletedEvent(
            userId, UUID.randomUUID(), "quiz-1", 5, 5, Instant.now()));

        AchievementProgressResponse progress = service.listAchievementProgress(userId).stream()
            .filter(p -> "DOUBLE_QUIZ".equals(p.code()))
            .findFirst()
            .orElseThrow();

        assertThat(progress.progress()).isEqualTo(1);
        assertThat(progress.unlockedAt()).isNull();
        assertThat(rabbitTemplate.publishedEvents.stream()
            .filter(e -> "yomu.achievement.unlocked".equals(e.routingKey()))
            .map(e -> ((AchievementUnlockedEvent) e.message()).achievementCode())
            .noneMatch("DOUBLE_QUIZ"::equals)).isTrue();
    }

    @Test
    void quizCompleted_secondQuizUnlocks() {
        UUID userId = UUID.randomUUID();
        repository.saveAchievement(new Achievement(
            UUID.randomUUID(),
            "DOUBLE_QUIZ",
            "Double Quiz",
            "Selesaikan dua kuis.",
            AchievementMetric.QUIZ_COMPLETED,
            2,
            true,
            Instant.now()
        ));

        service.recordQuizCompleted(new QuizCompletedEvent(
            userId, UUID.randomUUID(), "quiz-1", 5, 5, Instant.now()));
        service.recordQuizCompleted(new QuizCompletedEvent(
            userId, UUID.randomUUID(), "quiz-2", 4, 5, Instant.now()));

        AchievementProgressResponse progress = service.listAchievementProgress(userId).stream()
            .filter(p -> "DOUBLE_QUIZ".equals(p.code()))
            .findFirst()
            .orElseThrow();

        assertThat(progress.progress()).isEqualTo(2);
        assertThat(progress.unlockedAt()).isNotNull();
        assertThat(rabbitTemplate.publishedEvents.stream()
            .filter(e -> "yomu.achievement.unlocked".equals(e.routingKey()))
            .map(e -> ((AchievementUnlockedEvent) e.message()).achievementCode())
            .filter("DOUBLE_QUIZ"::equals)
            .count()).isEqualTo(1);
    }

    @Test
    void readingCompleted_unlocksReadingAchievement() {
        UUID userId = UUID.randomUUID();
        UUID bacaanId = UUID.randomUUID();
        repository.saveAchievement(new Achievement(
            UUID.randomUUID(),
            "FIRST_READING",
            "First Reading",
            "Selesaikan satu bacaan.",
            AchievementMetric.READING_COMPLETED,
            1,
            true,
            Instant.now()
        ));

        service.recordReadingCompleted(new LearningCompletedEvent(
            userId, bacaanId, "bacaan-1", Instant.now()));

        AchievementProgressResponse progress = service.listAchievementProgress(userId).stream()
            .filter(p -> "FIRST_READING".equals(p.code()))
            .findFirst()
            .orElseThrow();

        assertThat(progress.progress()).isEqualTo(1);
        assertThat(progress.unlockedAt()).isNotNull();
    }

    @Test
    void readingCompleted_duplicateEventIdempotent() {
        UUID userId = UUID.randomUUID();
        UUID bacaanId = UUID.randomUUID();
        repository.saveAchievement(new Achievement(
            UUID.randomUUID(),
            "FIRST_READING",
            "First Reading",
            "Selesaikan satu bacaan.",
            AchievementMetric.READING_COMPLETED,
            1,
            true,
            Instant.now()
        ));
        LearningCompletedEvent event = new LearningCompletedEvent(
            userId, bacaanId, "bacaan-1", Instant.now());

        service.recordReadingCompleted(event);
        service.recordReadingCompleted(event);

        AchievementProgressResponse progress = service.listAchievementProgress(userId).stream()
            .filter(p -> "FIRST_READING".equals(p.code()))
            .findFirst()
            .orElseThrow();

        assertThat(progress.progress()).isEqualTo(1);
    }

    @Test
    void claimDailyMission_success_returnsReward() {
        UUID userId = UUID.randomUUID();
        DailyMission mission = new DailyMission(
            UUID.randomUUID(),
            "DAILY_QUIZ",
            "Kuis Harian",
            "Selesaikan satu kuis.",
            AchievementMetric.QUIZ_COMPLETED,
            1,
            25,
            LocalDate.now(),
            LocalDate.now().plusDays(1),
            Instant.now()
        );
        repository.saveDailyMission(mission);

        service.recordQuizCompleted(new QuizCompletedEvent(
            userId, UUID.randomUUID(), "quiz-1", 5, 5, Instant.now()));

        ClaimRewardResponse response = service.claimDailyMissionReward(mission.id(), userId);

        assertThat(response.rewardPoints()).isEqualTo(25);
        assertThat(response.claimed()).isTrue();
        assertThat(service.listActiveDailyMissions(userId).getFirst().claimed()).isTrue();
    }

    @Test
    void claimDailyMission_notComplete_throws400() {
        UUID userId = UUID.randomUUID();
        DailyMission mission = new DailyMission(
            UUID.randomUUID(),
            "DAILY_QUIZ",
            "Kuis Harian",
            "Selesaikan satu kuis.",
            AchievementMetric.QUIZ_COMPLETED,
            1,
            10,
            LocalDate.now(),
            LocalDate.now().plusDays(1),
            Instant.now()
        );
        repository.saveDailyMission(mission);

        assertThatThrownBy(() -> service.claimDailyMissionReward(mission.id(), userId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void claimDailyMission_alreadyClaimed_throws400() {
        UUID userId = UUID.randomUUID();
        DailyMission mission = new DailyMission(
            UUID.randomUUID(),
            "DAILY_QUIZ",
            "Kuis Harian",
            "Selesaikan satu kuis.",
            AchievementMetric.QUIZ_COMPLETED,
            1,
            10,
            LocalDate.now(),
            LocalDate.now().plusDays(1),
            Instant.now()
        );
        repository.saveDailyMission(mission);
        service.recordQuizCompleted(new QuizCompletedEvent(
            userId, UUID.randomUUID(), "quiz-1", 5, 5, Instant.now()));
        service.claimDailyMissionReward(mission.id(), userId);

        assertThatThrownBy(() -> service.claimDailyMissionReward(mission.id(), userId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("sudah diklaim");
    }

    @Test
    void clanPromoted_unlocksClanPromotedAchievement() {
        UUID userId = UUID.randomUUID();
        repository.saveAchievement(new Achievement(
            UUID.randomUUID(),
            "CLAN_PROMOTED_BADGE",
            "Clan Promoted",
            "Naik tier clan.",
            AchievementMetric.CLAN_PROMOTED,
            1,
            true,
            Instant.now()
        ));

        service.recordClanPromoted(new ClanPromotedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            userId,
            "Test Clan",
            "BRONZE",
            "SILVER",
            Instant.now()
        ));

        AchievementProgressResponse progress = service.listAchievementProgress(userId).stream()
            .filter(p -> "CLAN_PROMOTED_BADGE".equals(p.code()))
            .findFirst()
            .orElseThrow();

        assertThat(progress.progress()).isEqualTo(1);
        assertThat(progress.unlockedAt()).isNotNull();
    }

    @Test
    void clanPromoted_toDiamond_alsoTriggersDiamondMetric() {
        UUID userId = UUID.randomUUID();
        repository.saveAchievement(new Achievement(
            UUID.randomUUID(),
            "CLAN_PROMOTED_BADGE",
            "Clan Promoted",
            "Naik tier clan.",
            AchievementMetric.CLAN_PROMOTED,
            1,
            true,
            Instant.now()
        ));
        repository.saveAchievement(new Achievement(
            UUID.randomUUID(),
            "DIAMOND_BADGE",
            "Diamond Tier",
            "Capai diamond.",
            AchievementMetric.CLAN_REACHED_DIAMOND,
            1,
            true,
            Instant.now()
        ));

        service.recordClanPromoted(new ClanPromotedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            userId,
            "Test Clan",
            "GOLD",
            "DIAMOND",
            Instant.now()
        ));

        assertThat(service.listAchievementProgress(userId).stream()
            .filter(p -> "CLAN_PROMOTED_BADGE".equals(p.code()))
            .findFirst().orElseThrow().unlockedAt()).isNotNull();
        assertThat(service.listAchievementProgress(userId).stream()
            .filter(p -> "DIAMOND_BADGE".equals(p.code()))
            .findFirst().orElseThrow().unlockedAt()).isNotNull();
    }

    @Test
    void clanPromoted_duplicateIdempotent() {
        UUID userId = UUID.randomUUID();
        repository.saveAchievement(new Achievement(
            UUID.randomUUID(),
            "CLAN_PROMOTED_BADGE",
            "Clan Promoted",
            "Naik tier clan.",
            AchievementMetric.CLAN_PROMOTED,
            1,
            true,
            Instant.now()
        ));
        ClanPromotedEvent event = new ClanPromotedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            userId,
            "Test Clan",
            "BRONZE",
            "SILVER",
            Instant.now()
        );

        service.recordClanPromoted(event);
        service.recordClanPromoted(event);

        assertThat(service.listAchievementProgress(userId).stream()
            .filter(p -> "CLAN_PROMOTED_BADGE".equals(p.code()))
            .findFirst().orElseThrow().progress()).isEqualTo(1);
    }

    @Test
    void leagueActivity_recordsProgress() {
        UUID userId = UUID.randomUUID();
        repository.saveAchievement(new Achievement(
            UUID.randomUUID(),
            "LEAGUE_ACTIVE",
            "League Active",
            "Aktivitas liga.",
            AchievementMetric.LEAGUE_ACTIVITY,
            1,
            true,
            Instant.now()
        ));

        service.recordLeagueActivity(new LeagueActivityEvent(
            userId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "QUIZ_COMPLETED",
            Instant.now()
        ));

        AchievementProgressResponse progress = service.listAchievementProgress(userId).stream()
            .filter(p -> "LEAGUE_ACTIVE".equals(p.code()))
            .findFirst()
            .orElseThrow();

        assertThat(progress.progress()).isEqualTo(1);
    }

    @Test
    void commentCreated_invalidUserId_skipsSilently() {
        UUID userId = UUID.randomUUID();
        repository.saveAchievement(new Achievement(
            UUID.randomUUID(),
            "COMMENTER",
            "Commenter",
            "Buat komentar.",
            AchievementMetric.COMMENT_CREATED,
            1,
            true,
            Instant.now()
        ));

        service.recordCommentCreated(new CommentCreatedEvent(
            "not-a-valid-uuid",
            "bacaan-1",
            null,
            "comment-1",
            "hello",
            Instant.now()
        ));

        assertThat(service.listAchievementProgress(userId).stream()
            .filter(p -> "COMMENTER".equals(p.code()))
            .findFirst()
            .orElseThrow()
            .progress()).isZero();
        assertThat(rabbitTemplate.publishedEvents).isEmpty();
    }

    @Test
    void commentCreated_validUserId_incrementsProgress() {
        UUID userId = UUID.randomUUID();
        repository.saveAchievement(new Achievement(
            UUID.randomUUID(),
            "COMMENTER",
            "Commenter",
            "Buat komentar.",
            AchievementMetric.COMMENT_CREATED,
            1,
            true,
            Instant.now()
        ));

        service.recordCommentCreated(new CommentCreatedEvent(
            userId.toString(),
            "bacaan-1",
            null,
            "comment-1",
            "hello",
            Instant.now()
        ));

        AchievementProgressResponse progress = service.listAchievementProgress(userId).stream()
            .filter(p -> "COMMENTER".equals(p.code()))
            .findFirst()
            .orElseThrow();

        assertThat(progress.progress()).isEqualTo(1);
    }

    private record PublishedEvent(String routingKey, Object message) {
    }

    private static class CapturingRabbitTemplate extends RabbitTemplate {
        private final List<PublishedEvent> publishedEvents = new ArrayList<>();

        @Override
        public void convertAndSend(String routingKey, Object message) {
            publishedEvents.add(new PublishedEvent(routingKey, message));
        }
    }

    private static class InMemoryAchievementRepository implements AchievementRepository {
        private final Map<UUID, Achievement> achievementsById = new HashMap<>();
        private final Map<String, Achievement> achievementsByCode = new HashMap<>();
        private final Map<UUID, DailyMission> missionsById = new HashMap<>();
        private final Map<String, DailyMission> missionsByCode = new HashMap<>();
        private final Map<ProgressKey, AchievementProgressState> achievementProgress = new HashMap<>();
        private final Map<ProgressKey, DailyMissionProgressState> missionProgress = new HashMap<>();
        private final Set<ActivityKey> activityEvents = new HashSet<>();

        @Override
        public Achievement saveAchievement(Achievement achievement) {
            achievementsById.put(achievement.id(), achievement);
            achievementsByCode.put(achievement.code(), achievement);
            return achievement;
        }

        @Override
        public boolean existsByAchievementCode(String code) {
            return achievementsByCode.containsKey(code);
        }

        @Override
        public List<Achievement> findActiveAchievementsByMetric(AchievementMetric metric) {
            return achievementsById.values().stream()
                .filter(Achievement::active)
                .filter(achievement -> achievement.metric() == metric)
                .sorted(Comparator.comparing(Achievement::createdAt))
                .toList();
        }

        @Override
        public List<AchievementProgress> findAchievementProgressForUser(UUID userId) {
            return achievementsById.values().stream()
                .filter(Achievement::active)
                .sorted(Comparator.comparing(Achievement::createdAt))
                .map(achievement -> {
                    AchievementProgressState state = achievementProgress.getOrDefault(
                        new ProgressKey(userId, achievement.id()),
                        new AchievementProgressState(0, null, false)
                    );
                    return new AchievementProgress(
                        achievement,
                        state.progressCount(),
                        state.unlockedAt(),
                        state.isPinned()
                    );
                })
                .toList();
        }

        @Override
        public Optional<AchievementProgressState> findAchievementProgressState(
            UUID userId,
            UUID achievementId
        ) {
            return Optional.ofNullable(achievementProgress.get(new ProgressKey(userId, achievementId)));
        }

        @Override
        public void saveAchievementProgress(
            UUID userId,
            UUID achievementId,
            int progressCount,
            Instant unlockedAt
        ) {
            achievementProgress.put(
                new ProgressKey(userId, achievementId),
                new AchievementProgressState(
                    progressCount,
                    unlockedAt,
                    findAchievementProgressState(userId, achievementId)
                        .map(AchievementProgressState::isPinned)
                        .orElse(false)
                )
            );
        }

        @Override
        public void pinAchievement(UUID userId, UUID achievementId, boolean pin) {
            ProgressKey key = new ProgressKey(userId, achievementId);
            AchievementProgressState state = achievementProgress.get(key);
            if (state != null) {
                achievementProgress.put(
                    key,
                    new AchievementProgressState(state.progressCount(), state.unlockedAt(), pin)
                );
            }
        }

        @Override
        public DailyMission saveDailyMission(DailyMission mission) {
            missionsById.put(mission.id(), mission);
            missionsByCode.put(mission.code(), mission);
            return mission;
        }

        @Override
        public boolean existsByDailyMissionCode(String code) {
            return missionsByCode.containsKey(code);
        }

        @Override
        public Optional<DailyMission> findDailyMissionById(UUID missionId) {
            return Optional.ofNullable(missionsById.get(missionId));
        }

        @Override
        public List<DailyMission> findActiveDailyMissionsByMetric(
            AchievementMetric metric,
            LocalDate activeOn
        ) {
            return missionsById.values().stream()
                .filter(mission -> mission.metric() == metric)
                .filter(mission -> isActiveOn(mission, activeOn))
                .sorted(Comparator.comparing(DailyMission::createdAt))
                .toList();
        }

        @Override
        public List<DailyMissionProgress> findActiveDailyMissionProgressForUser(
            UUID userId,
            LocalDate activeOn
        ) {
            return missionsById.values().stream()
                .filter(mission -> isActiveOn(mission, activeOn))
                .sorted(Comparator.comparing(DailyMission::createdAt))
                .map(mission -> {
                    DailyMissionProgressState state = missionProgress.getOrDefault(
                        new ProgressKey(userId, mission.id()),
                        new DailyMissionProgressState(0, null)
                    );
                    return new DailyMissionProgress(
                        mission,
                        state.progressCount(),
                        state.claimedAt()
                    );
                })
                .toList();
        }

        @Override
        public boolean hasActiveDailyMissionOn(LocalDate activeOn) {
            return missionsById.values().stream()
                .anyMatch(mission -> isActiveOn(mission, activeOn));
        }

        @Override
        public Optional<DailyMissionProgressState> findDailyMissionProgressState(
            UUID userId,
            UUID missionId
        ) {
            return Optional.ofNullable(missionProgress.get(new ProgressKey(userId, missionId)));
        }

        @Override
        public void saveDailyMissionProgress(
            UUID userId,
            UUID missionId,
            int progressCount,
            Instant claimedAt
        ) {
            missionProgress.put(
                new ProgressKey(userId, missionId),
                new DailyMissionProgressState(progressCount, claimedAt)
            );
        }

        @Override
        public boolean saveActivityEvent(
            UUID userId,
            AchievementMetric metric,
            String sourceId,
            Instant occurredAt
        ) {
            return activityEvents.add(new ActivityKey(userId, metric, sourceId));
        }

        @Override
        public int sumClaimedRewardPoints(UUID userId) {
            return missionProgress.entrySet().stream()
                .filter(entry -> entry.getKey().userId().equals(userId))
                .filter(entry -> entry.getValue().claimedAt() != null)
                .mapToInt(entry -> missionsById.get(entry.getKey().targetId()).rewardPoints())
                .sum();
        }

        private boolean isActiveOn(DailyMission mission, LocalDate activeOn) {
            return !mission.activeFrom().isAfter(activeOn)
                && mission.activeUntil().isAfter(activeOn);
        }
    }

    private record ProgressKey(UUID userId, UUID targetId) {
    }

    private record ActivityKey(UUID userId, AchievementMetric metric, String sourceId) {
    }
}
