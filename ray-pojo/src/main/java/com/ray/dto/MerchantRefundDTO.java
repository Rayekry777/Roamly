package com.ray.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 商户发起售后退款申请；金额由服务端根据订单券明细计算。 */
public record MerchantRefundDTO(
        @NotNull Long orderId,
        @NotNull @Size(min = 1, max = 50) List<Long> voucherIds,
        @NotBlank @Size(max = 64) String reasonCode,
        @Size(max = 500) String description) {}
