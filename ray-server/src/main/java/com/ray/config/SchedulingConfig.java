package com.ray.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.ray.service.VoucherOrderExpiryService;
import com.ray.service.SettlementService;

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

    @Component
    static class SettlementJob {
        private final SettlementService service;

        SettlementJob(SettlementService service) { this.service = service; }

        @Scheduled(cron = "${ray.settlement.cron:0 0 2 * * *}", zone = "${ray.settlement.zone:Asia/Shanghai}")
        public void generate() {
            service.generateForDate(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
        }
    }
}
