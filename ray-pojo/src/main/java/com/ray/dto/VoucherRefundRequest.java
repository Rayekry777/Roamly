package com.ray.dto;

import jakarta.validation.constraints.Size;

/** 消费者单券退款申请。 */
public record VoucherRefundRequest(@Size(max = 255) String reason) {}
