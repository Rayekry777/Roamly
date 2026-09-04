package com.ray.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 支付渠道模式：MOCK模拟、DISABLED禁用、WECHAT微信（预留）。 */
@Component
@ConfigurationProperties(prefix = "ray.payment")
public class PaymentProperties {
    private String mode = "MOCK";
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode == null ? "DISABLED" : mode.trim().toUpperCase(); }
}
