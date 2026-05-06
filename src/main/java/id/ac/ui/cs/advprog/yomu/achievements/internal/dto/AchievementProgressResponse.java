package id.ac.ui.cs.advprog.yomu.achievements.internal.dto;

import java.time.Instant;
import java.util.UUID;

public record AchievementProgressResponse(
    UUID achievementId,
    String code,
    String name,
    String description,
    String metric,
    int milestone,
    int progress,
    Instant unlockedAt
) {
}
