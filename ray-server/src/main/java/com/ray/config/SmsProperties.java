package com.ray.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 短信验证码在不同运行环境下的发送策略。 */
@Component
@ConfigurationProperties(prefix = "ray.auth.sms")
public class SmsProperties {
    private Mode mode = Mode.DISABLED;
    private String mockCode;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getMockCode() {
        return mockCode;
    }

    public void setMockCode(String mockCode) {
        this.mockCode = mockCode;
    }

    /** 返回已校验的六位模拟验证码。 */
    public String requireMockCode() {
        if (mockCode == null || !mockCode.matches("^\\d{6}$")) {
            throw new IllegalStateException("ray.auth.sms.mock-code 必须是六位数字");
        }
        return mockCode;
    }

    public enum Mode {
        MOCK,
        DISABLED
    }
}
