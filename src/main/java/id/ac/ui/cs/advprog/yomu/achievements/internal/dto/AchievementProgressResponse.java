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
<<<<<<< HEAD
    boolean unlocked,
    Instant unlockedAt
=======
    Instant unlockedAt,
    boolean isPinned
>>>>>>> 7041e58ccda4e11d52ef656ca3bfcb0f79ef32cc
) {
}
