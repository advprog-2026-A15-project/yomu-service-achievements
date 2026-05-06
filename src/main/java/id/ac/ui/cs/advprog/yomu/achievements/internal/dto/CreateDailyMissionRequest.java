package id.ac.ui.cs.advprog.yomu.achievements.internal.dto;

import id.ac.ui.cs.advprog.yomu.achievements.internal.model.AchievementMetric;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateDailyMissionRequest(
    String code,

    @NotBlank(message = "Nama daily mission tidak boleh kosong")
    String name,

    String description,

    AchievementMetric metric,

    @NotNull(message = "Target wajib diisi")
    @Min(value = 1, message = "Target minimal 1")
    Integer targetCount,

    @Min(value = 0, message = "Reward tidak boleh negatif")
    Integer rewardPoints,

    LocalDate activeFrom,

    LocalDate activeUntil
) {
}
