package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/** 支付结果。 */
@Schema(name = "VoucherPaymentVO", description = "团购支付结果")
public record VoucherPaymentVO(
        @Schema(description = "服务端选择的支付模式", allowableValues = {"MOCK", "WECHAT", "DISABLED"})
        String paymentMode,
        @Schema(description = "当前支付渠道是否可用") boolean paymentAvailable,
        @Schema(description = "渠道不可用时的用户提示") String unavailableMessage,
        @Schema(description = "微信 JSAPI 参数；未配置时为空") PaymentParams paymentParams,
        @Schema(type = "string") String transactionId,
        @Schema(type = "string") String orderId,
        String status,
        Long amount,
        LocalDateTime paidTime,
        LocalDateTime paymentExpireTime) {

    /** 微信小程序 requestPayment 所需的服务端签名参数。 */
    @Schema(name = "VoucherPaymentParams", description = "微信支付参数")
    public record PaymentParams(
            String timeStamp,
            String nonceStr,
            @JsonProperty("package") String packageValue,
            String signType,
            String paySign) {}
}
