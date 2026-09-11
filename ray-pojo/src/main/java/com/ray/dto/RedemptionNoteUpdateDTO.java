package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 更新核销商家备注。 */
@Schema(name = "RedemptionNoteUpdateDTO", description = "核销商家备注")
public record RedemptionNoteUpdateDTO(
        @Size(max = 500) @Schema(description = "商家备注，清空时传空字符串") String note) {}
