package id.ac.ui.cs.advprog.yomu.achievements.internal.service;

import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.AchievementProgressResponse;
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
import id.ac.ui.cs.advprog.yomu.shared.event.DailyMissionCompletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.QuizCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

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
