package id.ac.ui.cs.advprog.yomu.achievements.internal.model;

import java.time.Instant;

public record AchievementProgressState(
    int progressCount,
    Instant unlockedAt
) {
}
