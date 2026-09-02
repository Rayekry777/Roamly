package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "新增普通优惠券请求")
public record CreateVoucherDTO(
        @NotBlank
                @Pattern(regexp = "^[1-9]\\d*$")
                @Schema(
                        description = "商户 ID",
                        example = "1",
                        pattern = "^[1-9]\\d*$",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String shopId,
        @NotBlank
                @Size(max = 255)
                @Schema(
                        description = "优惠券标题",
                        example = "100 元代金券",
                        maxLength = 255,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String title,
        @Size(max = 255) @Schema(description = "副标题", example = "周末通用", maxLength = 255) String subTitle,
        @Size(max = 1024) @Schema(description = "使用规则", maxLength = 1024) String rules,
        @NotNull
                @Positive
                @Schema(
                        description = "支付金额，单位分",
                        example = "8000",
                        minimum = "1",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long payValue,
        @NotNull
                @Positive
                @Schema(
                        description = "抵扣金额，单位分",
                        example = "10000",
                        minimum = "1",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long actualValue) {}
