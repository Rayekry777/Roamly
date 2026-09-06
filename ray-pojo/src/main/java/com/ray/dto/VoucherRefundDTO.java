package com.ray.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 消费者单券退款申请。 */
public record VoucherRefundDTO(
        @NotBlank @Pattern(regexp = "PLAN_CHANGED|BOUGHT_WRONG|SAFETY_CONCERN|REGRET|MISTOOK_DELIVERY|RULES_UNCLEAR|QUEUE_TOO_LONG|CANNOT_CONTACT_SHOP|SHOP_NOT_SERVING|OTHER") String reasonCode,
        @Size(max = 100) String description,
        @NotNull @Min(1) @Max(1) Integer quantity) {}
