package id.ac.ui.cs.advprog.yomu.achievements.internal.model;

import java.time.Instant;

public record AchievementProgress(
    Achievement achievement,
    int progressCount,
    Instant unlockedAt
) {
    public boolean unlocked() {
        return unlockedAt != null;
    }
}
