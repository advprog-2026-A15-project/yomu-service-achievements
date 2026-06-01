package id.ac.ui.cs.advprog.yomu.achievements.internal.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AchievementProgressModelTest {

    private Achievement buildAchievement() {
        return new Achievement(
            UUID.randomUUID(), "CODE", "Name", "Desc",
            AchievementMetric.QUIZ_COMPLETED, 1, true, Instant.now()
        );
    }

    @Test
    void achievementProgress_unlocked_returnsTrueWhenUnlockedAtNotNull() {
        AchievementProgress progress = new AchievementProgress(buildAchievement(), 1, Instant.now(), false);
        assertThat(progress.unlocked()).isTrue();
    }

    @Test
    void achievementProgress_unlocked_returnsFalseWhenUnlockedAtNull() {
        AchievementProgress progress = new AchievementProgress(buildAchievement(), 0, null, false);
        assertThat(progress.unlocked()).isFalse();
    }

    @Test
    void dailyMissionProgress_completed_returnsFalseWhenProgressBelowTarget() {
        DailyMission mission = new DailyMission(
            UUID.randomUUID(), "CODE", "Name", "Desc",
            AchievementMetric.QUIZ_COMPLETED, 3, 10,
            LocalDate.now(), LocalDate.now().plusDays(1), Instant.now()
        );
        DailyMissionProgress progress = new DailyMissionProgress(mission, 2, null);
        assertThat(progress.completed()).isFalse();
        assertThat(progress.claimed()).isFalse();
    }

    @Test
    void dailyMissionProgress_completed_returnsTrueWhenProgressMeetsTarget() {
        DailyMission mission = new DailyMission(
            UUID.randomUUID(), "CODE", "Name", "Desc",
            AchievementMetric.QUIZ_COMPLETED, 3, 10,
            LocalDate.now(), LocalDate.now().plusDays(1), Instant.now()
        );
        DailyMissionProgress completedAndClaimed = new DailyMissionProgress(mission, 3, Instant.now());
        assertThat(completedAndClaimed.completed()).isTrue();
        assertThat(completedAndClaimed.claimed()).isTrue();
    }
}
