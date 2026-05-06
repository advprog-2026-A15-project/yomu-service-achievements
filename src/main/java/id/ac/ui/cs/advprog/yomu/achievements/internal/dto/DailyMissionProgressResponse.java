package id.ac.ui.cs.advprog.yomu.achievements.internal.dto;

import java.time.Instant;
import java.util.UUID;

public record DailyMissionProgressResponse(
    UUID missionId,
    String code,
    String name,
    String description,
    String metric,
    int targetCount,
    int rewardPoints,
    int progress,
    Instant claimedAt
) {
}
