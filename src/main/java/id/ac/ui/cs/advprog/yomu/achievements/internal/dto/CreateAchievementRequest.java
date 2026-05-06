package id.ac.ui.cs.advprog.yomu.achievements.internal.dto;

import id.ac.ui.cs.advprog.yomu.achievements.internal.model.AchievementMetric;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAchievementRequest(
    String code,

    @NotBlank(message = "Nama achievement tidak boleh kosong")
    String name,

    String description,

    AchievementMetric metric,

    @NotNull(message = "Milestone wajib diisi")
    @Min(value = 1, message = "Milestone minimal 1")
    Integer milestone
) {
}
