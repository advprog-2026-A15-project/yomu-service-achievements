package id.ac.ui.cs.advprog.yomu.achievements.internal.model;

import java.time.Instant;
import java.util.UUID;

public record Achievement(
    UUID id,
    String code,
    String name,
    String description,
    AchievementMetric metric,
    int milestone,
    boolean active,
    Instant createdAt
) {
}
