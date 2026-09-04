package com.ray.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.ray.service.VoucherOrderExpiryService;

/** 启用临时媒体等后台维护任务。 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    @Component
    static class VoucherOrderExpiryJob {
        private final VoucherOrderExpiryService service;
        VoucherOrderExpiryJob(VoucherOrderExpiryService service) { this.service = service; }
        @Scheduled(fixedDelayString = "${ray.order.expiry.scan-interval-ms:30000}")
        public void scan() { service.closeExpiredOrders(); }
    }
}
