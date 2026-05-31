package id.ac.ui.cs.advprog.yomu.achievements.internal.service;

import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.AchievementProgressResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.AchievementResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.ClaimRewardResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.CreateAchievementRequest;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.CreateDailyMissionRequest;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.DailyMissionProgressResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.DailyMissionResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.Achievement;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.AchievementMetric;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.AchievementProgress;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.AchievementProgressState;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.DailyMission;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.DailyMissionProgress;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.DailyMissionProgressState;
import id.ac.ui.cs.advprog.yomu.achievements.internal.monitoring.AchievementMetrics;
import id.ac.ui.cs.advprog.yomu.achievements.internal.repository.AchievementRepository;
import id.ac.ui.cs.advprog.yomu.shared.event.AchievementUnlockedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.ClanPromotedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentCreatedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.DailyMissionCompletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.LeagueActivityEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.LearningCompletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.QuizCompletedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
    private SimpleMeterRegistry meterRegistry;
    private AchievementServiceImpl service;
    private Achievement firstRead;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAchievementRepository();
        rabbitTemplate = new CapturingRabbitTemplate();
        meterRegistry = new SimpleMeterRegistry();
        service = new AchievementServiceImpl(repository, rabbitTemplate, new AchievementMetrics(meterRegistry));

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
    void quizCompleted_recordsPrometheusMetrics() {
        UUID userId = UUID.randomUUID();

        service.recordQuizCompleted(new QuizCompletedEvent(
            userId,
            UUID.randomUUID(),
            "quiz-1",
            80,
            5,
            Instant.now()
        ));

        assertThat(counterValue(
            AchievementMetrics.ACTIVITY_EVENT_COUNTER_METRIC,
            "metric", AchievementMetric.QUIZ_COMPLETED.name(),
            "outcome", "new"
        )).isEqualTo(1.0);
        assertThat(counterValue(
            AchievementMetrics.UNLOCK_COUNTER_METRIC,
            "metric", AchievementMetric.QUIZ_COMPLETED.name()
        )).isEqualTo(1.0);
        assertThat(counterValue(
            AchievementMetrics.ACTION_COUNTER_METRIC,
            "action", "record_quiz_completed",
            "outcome", "success"
        )).isEqualTo(1.0);
        assertThat(timerCount(
            AchievementMetrics.ACTION_TIMER_METRIC,
            "action", "record_quiz_completed",
            "outcome", "success"
        )).isEqualTo(1);
    }

    @Test
    void duplicateQuizCompleted_recordsDuplicateActivityMetricOnlyOnce() {
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

        assertThat(counterValue(
            AchievementMetrics.ACTIVITY_EVENT_COUNTER_METRIC,
            "metric", AchievementMetric.QUIZ_COMPLETED.name(),
            "outcome", "new"
        )).isEqualTo(1.0);
        assertThat(counterValue(
            AchievementMetrics.ACTIVITY_EVENT_COUNTER_METRIC,
            "metric", AchievementMetric.QUIZ_COMPLETED.name(),
            "outcome", "duplicate"
        )).isEqualTo(1.0);
        assertThat(counterValue(
            AchievementMetrics.UNLOCK_COUNTER_METRIC,
            "metric", AchievementMetric.QUIZ_COMPLETED.name()
        )).isEqualTo(1.0);
    }

    @Test
    void invalidCommentCreated_recordsInvalidUserMetric() {
        service.recordCommentCreated(new CommentCreatedEvent(
            "not-a-valid-uuid",
            "bacaan-1",
            null,
            "comment-1",
            "hello",
            Instant.now()
        ));

        assertThat(counterValue(
            AchievementMetrics.ACTIVITY_EVENT_COUNTER_METRIC,
            "metric", AchievementMetric.COMMENT_CREATED.name(),
            "outcome", "invalid_user_id"
        )).isEqualTo(1.0);
        assertThat(counterValue(
            AchievementMetrics.ACTION_COUNTER_METRIC,
            "action", "record_comment_created",
            "outcome", "success"
        )).isEqualTo(1.0);
    }

    @Test
    void failedClaimDailyMissionReward_recordsFailureMetric() {
        assertThatThrownBy(() -> service.claimDailyMissionReward(UUID.randomUUID(), UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class);

        assertThat(counterValue(
            AchievementMetrics.ACTION_COUNTER_METRIC,
            "action", "claim_daily_mission_reward",
            "outcome", "failure"
        )).isEqualTo(1.0);
        assertThat(timerCount(
            AchievementMetrics.ACTION_TIMER_METRIC,
            "action", "claim_daily_mission_reward",
            "outcome", "failure"
        )).isEqualTo(1);
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

    @Test
    void createAchievement_success_persistsAndReturnsResponse() {
        AchievementResponse response = service.createAchievement(new CreateAchievementRequest(
            "BOOKWORM",
            "Bookworm",
            "Read ten articles",
            AchievementMetric.READING_COMPLETED,
            10
        ));

        assertThat(response.code()).isEqualTo("BOOKWORM");
        assertThat(response.name()).isEqualTo("Bookworm");
        assertThat(response.metric()).isEqualTo(AchievementMetric.READING_COMPLETED.name());
        assertThat(repository.existsByAchievementCode("BOOKWORM")).isTrue();
    }

    @Test
    void createAchievement_duplicateCode_throws409() {
        service.createAchievement(new CreateAchievementRequest(
            null,
            "Duplicate",
            null,
            AchievementMetric.QUIZ_COMPLETED,
            1
        ));

        assertThatThrownBy(() -> service.createAchievement(new CreateAchievementRequest(
            "DUPLICATE",
            "Other",
            null,
            AchievementMetric.QUIZ_COMPLETED,
            2
        )))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void createAchievement_nullMetric_throws400() {
        assertThatThrownBy(() -> service.createAchievement(new CreateAchievementRequest(
            "NO_METRIC",
            "No Metric",
            null,
            null,
            1
        )))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createDailyMission_success_persistsMission() {
        LocalDate from = LocalDate.now();
        DailyMissionResponse response = service.createDailyMission(new CreateDailyMissionRequest(
            "WEEKLY_QUIZ",
            "Weekly Quiz",
            "Finish three quizzes",
            AchievementMetric.QUIZ_COMPLETED,
            3,
            50,
            from,
            from.plusDays(7)
        ));

        assertThat(response.code()).isEqualTo("WEEKLY_QUIZ");
        assertThat(response.targetCount()).isEqualTo(3);
        assertThat(response.rewardPoints()).isEqualTo(50);
        assertThat(repository.existsByDailyMissionCode("WEEKLY_QUIZ")).isTrue();
    }

    @Test
    void createDailyMission_invalidDateRange_throws400() {
        LocalDate from = LocalDate.now();
        assertThatThrownBy(() -> service.createDailyMission(new CreateDailyMissionRequest(
            "BAD_DATES",
            "Bad Dates",
            null,
            AchievementMetric.QUIZ_COMPLETED,
            1,
            10,
            from,
            from
        )))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createDailyMission_duplicateCode_throws409() {
        LocalDate from = LocalDate.now();
        service.createDailyMission(new CreateDailyMissionRequest(
            null,
            "Daily One",
            null,
            AchievementMetric.QUIZ_COMPLETED,
            1,
            5,
            from,
            from.plusDays(1)
        ));

        assertThatThrownBy(() -> service.createDailyMission(new CreateDailyMissionRequest(
            "DAILY_ONE",
            "Daily Two",
            null,
            AchievementMetric.QUIZ_COMPLETED,
            1,
            5,
            from,
            from.plusDays(1)
        )))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void listCompletedAchievementProgress_returnsOnlyUnlockedAchievements() {
        UUID userId = UUID.randomUUID();
        repository.saveAchievement(new Achievement(
            UUID.randomUUID(),
            "LOCKED_READING",
            "Locked Reading",
            "Belum selesai.",
            AchievementMetric.READING_COMPLETED,
            5,
            true,
            Instant.now()
        ));
        service.recordQuizCompleted(new QuizCompletedEvent(
            userId, UUID.randomUUID(), "quiz-1", 5, 5, Instant.now()));

        List<AchievementProgressResponse> completed = service.listCompletedAchievementProgress(userId);

        assertThat(completed)
            .extracting(AchievementProgressResponse::code)
            .containsExactly("FIRST_READ");
        assertThat(completed.getFirst().unlocked()).isTrue();
    }

    @Test
    void listDailyMissions_returnsInactiveAndActiveMissionsForAdmin() {
        LocalDate today = LocalDate.now();
        DailyMission expired = new DailyMission(
            UUID.randomUUID(),
            "EXPIRED",
            "Expired Mission",
            "Sudah selesai.",
            AchievementMetric.READING_COMPLETED,
            1,
            5,
            today.minusDays(3),
            today.minusDays(2),
            Instant.now().minusSeconds(30)
        );
        DailyMission active = new DailyMission(
            UUID.randomUUID(),
            "ACTIVE",
            "Active Mission",
            "Sedang aktif.",
            AchievementMetric.QUIZ_COMPLETED,
            1,
            10,
            today,
            today.plusDays(1),
            Instant.now()
        );
        repository.saveDailyMission(expired);
        repository.saveDailyMission(active);

        assertThat(service.listDailyMissions())
            .extracting(DailyMissionResponse::code)
            .containsExactly("ACTIVE", "EXPIRED");
    }

    @Test
    void updateDailyMission_success_updatesEditableFields() {
        LocalDate today = LocalDate.now();
        DailyMission mission = new DailyMission(
            UUID.randomUUID(),
            "OLD_CODE",
            "Old Mission",
            "Old description",
            AchievementMetric.READING_COMPLETED,
            1,
            5,
            today,
            today.plusDays(1),
            Instant.now()
        );
        repository.saveDailyMission(mission);

        DailyMissionResponse response = service.updateDailyMission(mission.id(), new CreateDailyMissionRequest(
            "UPDATED_CODE",
            "Updated Mission",
            "Updated description",
            AchievementMetric.QUIZ_COMPLETED,
            3,
            25,
            today.plusDays(1),
            today.plusDays(4)
        ));

        assertThat(response.code()).isEqualTo("UPDATED_CODE");
        assertThat(response.name()).isEqualTo("Updated Mission");
        assertThat(response.metric()).isEqualTo(AchievementMetric.QUIZ_COMPLETED.name());
        assertThat(response.targetCount()).isEqualTo(3);
        assertThat(response.rewardPoints()).isEqualTo(25);
        assertThat(repository.findDailyMissionById(mission.id()))
            .get()
            .extracting(DailyMission::description)
            .isEqualTo("Updated description");
    }

    @Test
    void updateDailyMission_blankCode_keepsExistingCode() {
        LocalDate today = LocalDate.now();
        DailyMission mission = new DailyMission(
            UUID.randomUUID(),
            "KEEP_CODE",
            "Old Mission",
            "",
            AchievementMetric.READING_COMPLETED,
            1,
            5,
            today,
            today.plusDays(1),
            Instant.now()
        );
        repository.saveDailyMission(mission);

        DailyMissionResponse response = service.updateDailyMission(mission.id(), new CreateDailyMissionRequest(
            " ",
            "New Name",
            null,
            AchievementMetric.READING_COMPLETED,
            2,
            null,
            today,
            today.plusDays(2)
        ));

        assertThat(response.code()).isEqualTo("KEEP_CODE");
        assertThat(response.rewardPoints()).isZero();
    }

    @Test
    void updateDailyMission_duplicateCode_throws409() {
        LocalDate today = LocalDate.now();
        DailyMission first = new DailyMission(
            UUID.randomUUID(),
            "FIRST",
            "First Mission",
            "",
            AchievementMetric.READING_COMPLETED,
            1,
            5,
            today,
            today.plusDays(1),
            Instant.now()
        );
        DailyMission second = new DailyMission(
            UUID.randomUUID(),
            "SECOND",
            "Second Mission",
            "",
            AchievementMetric.QUIZ_COMPLETED,
            1,
            5,
            today,
            today.plusDays(1),
            Instant.now()
        );
        repository.saveDailyMission(first);
        repository.saveDailyMission(second);

        assertThatThrownBy(() -> service.updateDailyMission(second.id(), new CreateDailyMissionRequest(
            "FIRST",
            "Second Mission",
            null,
            AchievementMetric.QUIZ_COMPLETED,
            1,
            5,
            today,
            today.plusDays(1)
        )))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateDailyMission_missingMission_throws404() {
        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> service.updateDailyMission(UUID.randomUUID(), new CreateDailyMissionRequest(
            "MISSING",
            "Missing",
            null,
            AchievementMetric.READING_COMPLETED,
            1,
            5,
            today,
            today.plusDays(1)
        )))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteDailyMission_success_removesMission() {
        LocalDate today = LocalDate.now();
        DailyMission mission = new DailyMission(
            UUID.randomUUID(),
            "DELETE_ME",
            "Delete Me",
            "",
            AchievementMetric.READING_COMPLETED,
            1,
            5,
            today,
            today.plusDays(1),
            Instant.now()
        );
        repository.saveDailyMission(mission);

        service.deleteDailyMission(mission.id());

        assertThat(repository.findDailyMissionById(mission.id())).isEmpty();
        assertThat(service.listDailyMissions()).isEmpty();
    }

    @Test
    void deleteDailyMission_missingMission_throws404() {
        assertThatThrownBy(() -> service.deleteDailyMission(UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void listAchievementProgress_returnsAllActiveAchievements() {
        UUID userId = UUID.randomUUID();

        List<AchievementProgressResponse> progress = service.listAchievementProgress(userId);

        assertThat(progress).extracting(AchievementProgressResponse::code)
            .contains("FIRST_READ");
    }

    @Test
    void claimDailyMission_missionNotFound_throws404() {
        assertThatThrownBy(() -> service.claimDailyMissionReward(UUID.randomUUID(), UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void pinAchievement_success_whenUnlocked() {
        UUID userId = UUID.randomUUID();
        service.recordQuizCompleted(new QuizCompletedEvent(
            userId, UUID.randomUUID(), "quiz-1", 5, 5, Instant.now()));

        service.pinAchievement(userId, firstRead.id(), true);

        AchievementProgressResponse progress = service.listAchievementProgress(userId).stream()
            .filter(p -> "FIRST_READ".equals(p.code()))
            .findFirst()
            .orElseThrow();

        assertThat(progress.pinned()).isTrue();
    }

    @Test
    void pinAchievement_notUnlocked_throws400() {
        UUID userId = UUID.randomUUID();
        repository.saveAchievementProgress(userId, firstRead.id(), 1, null);

        assertThatThrownBy(() -> service.pinAchievement(userId, firstRead.id(), true))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void pinAchievement_progressNotFound_throws404() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.pinAchievement(userId, UUID.randomUUID(), true))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getTotalClaimedPoints_sumsClaimedMissionRewards() {
        UUID userId = UUID.randomUUID();
        DailyMission mission = new DailyMission(
            UUID.randomUUID(),
            "DAILY_QUIZ",
            "Kuis Harian",
            "Selesaikan satu kuis.",
            AchievementMetric.QUIZ_COMPLETED,
            1,
            30,
            LocalDate.now(),
            LocalDate.now().plusDays(1),
            Instant.now()
        );
        repository.saveDailyMission(mission);
        service.recordQuizCompleted(new QuizCompletedEvent(
            userId, UUID.randomUUID(), "quiz-1", 5, 5, Instant.now()));
        service.claimDailyMissionReward(mission.id(), userId);

        assertThat(service.getTotalClaimedPoints(userId)).isEqualTo(30);
    }

    private double counterValue(String metricName, String... tags) {
        return Optional.ofNullable(meterRegistry.find(metricName).tags(tags).counter())
            .map(counter -> counter.count())
            .orElse(0.0);
    }

    private long timerCount(String metricName, String... tags) {
        return Optional.ofNullable(meterRegistry.find(metricName).tags(tags).timer())
            .map(timer -> timer.count())
            .orElse(0L);
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
        public List<AchievementProgress> findUnlockedAchievementProgressForUser(UUID userId) {
            return findAchievementProgressForUser(userId).stream()
                .filter(progress -> progress.unlockedAt() != null)
                .sorted(Comparator
                    .comparing(AchievementProgress::isPinned)
                    .reversed()
                    .thenComparing(AchievementProgress::unlockedAt, Comparator.reverseOrder())
                    .thenComparing(progress -> progress.achievement().name()))
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
        public DailyMission updateDailyMission(DailyMission mission) {
            DailyMission existing = missionsById.get(mission.id());
            if (existing != null) {
                missionsByCode.remove(existing.code());
            }
            missionsById.put(mission.id(), mission);
            missionsByCode.put(mission.code(), mission);
            return mission;
        }

        @Override
        public void deleteDailyMission(UUID missionId) {
            DailyMission removed = missionsById.remove(missionId);
            if (removed != null) {
                missionsByCode.remove(removed.code());
            }
            missionProgress.keySet().removeIf(key -> key.targetId().equals(missionId));
        }

        @Override
        public List<DailyMission> findAllDailyMissions() {
            return missionsById.values().stream()
                .sorted(Comparator
                    .comparing(DailyMission::activeFrom)
                    .reversed()
                    .thenComparing(DailyMission::createdAt, Comparator.reverseOrder())
                    .thenComparing(DailyMission::name))
                .toList();
        }

        @Override
        public boolean existsByDailyMissionCode(String code) {
            return missionsByCode.containsKey(code);
        }

        @Override
        public boolean existsByDailyMissionCodeForDifferentId(String code, UUID missionId) {
            return Optional.ofNullable(missionsByCode.get(code))
                .map(mission -> !mission.id().equals(missionId))
                .orElse(false);
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
