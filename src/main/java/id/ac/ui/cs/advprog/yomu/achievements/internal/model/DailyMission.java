package id.ac.ui.cs.advprog.yomu.achievements.internal.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DailyMission(
    UUID id,
    String code,
    String name,
    String description,
    AchievementMetric metric,
    int targetCount,
    int rewardPoints,
    LocalDate activeFrom,
    LocalDate activeUntil,
    Instant createdAt
) {
}
