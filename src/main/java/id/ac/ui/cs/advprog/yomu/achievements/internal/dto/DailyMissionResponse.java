package id.ac.ui.cs.advprog.yomu.achievements.internal.dto;

import java.time.LocalDate;
import java.util.UUID;

public record DailyMissionResponse(
    UUID missionId,
    String code,
    String name,
    String description,
    String metric,
    int targetCount,
    int rewardPoints,
    LocalDate activeFrom,
    LocalDate activeUntil
) {
}
