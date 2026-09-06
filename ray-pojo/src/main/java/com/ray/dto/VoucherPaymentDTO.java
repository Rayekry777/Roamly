package com.ray.dto;

import jakarta.validation.constraints.Pattern;

/** 支付请求；Mock 渠道可指定成功或失败，真实渠道由服务端决定结果。 */
public record VoucherPaymentDTO(
        @Pattern(regexp = "MOCK_SUCCESS|MOCK_FAILURE") String scenario) {
    public VoucherPaymentDTO {
        if (scenario == null || scenario.isBlank()) scenario = "MOCK_SUCCESS";
    }
}
