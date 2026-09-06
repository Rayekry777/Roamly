package com.ray.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 管理端异常退款申请；禁止客户端直接指定退款金额。 */
public record AdminRefundDTO(
        @NotNull Long orderId,
        @NotNull @Size(min = 1, max = 50) List<Long> voucherIds,
        @NotBlank @Size(max = 64) String reasonCode,
        @Size(max = 500) String description) {}
