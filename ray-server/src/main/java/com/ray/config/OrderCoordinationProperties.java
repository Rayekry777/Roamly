package com.ray.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 团购订单并发协调参数。 */
@Component
@ConfigurationProperties(prefix = "ray.order.coordination")
public class OrderCoordinationProperties {
    private Duration lockWait = Duration.ofSeconds(1);

    public Duration getLockWait() {
        return lockWait;
    }

    public void setLockWait(Duration lockWait) {
        if (lockWait == null || lockWait.isNegative()) {
            throw new IllegalArgumentException("ray.order.coordination.lock-wait 不能为负数");
        }
        this.lockWait = lockWait;
    }
}
