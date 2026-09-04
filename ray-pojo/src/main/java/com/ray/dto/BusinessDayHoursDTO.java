package com.ray.dto;

import com.ray.enums.BusinessDayOfWeek;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "某一天的营业安排")
public record BusinessDayHoursDTO(
        @NotNull @Schema(description = "星期枚举") BusinessDayOfWeek dayOfWeek,
        @NotNull @Schema(description = "是否休息") Boolean closed,
        @NotNull @Size(max = 3) @Valid @Schema(description = "营业时段，最多三段") List<BusinessPeriodDTO> periods) {
    public BusinessDayHoursDTO {
        periods = periods == null ? null : List.copyOf(periods);
    }
}
