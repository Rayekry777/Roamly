package com.ray.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Mock 支付请求。生产接入真实渠道时仍由服务端核验金额。 */
public record VoucherPaymentRequest(
        @NotBlank @Pattern(regexp = "MOCK_SUCCESS|MOCK_FAILURE") String scenario) {}
