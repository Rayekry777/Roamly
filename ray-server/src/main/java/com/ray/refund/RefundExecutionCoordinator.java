package com.ray.refund;

import com.ray.mapper.VoucherRefundAttemptMapper;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 扫描可执行退款任务，并将每个任务交给独立事务 Worker。 */
@Component
public class RefundExecutionCoordinator {
    private final VoucherRefundAttemptMapper attempts;
    private final RefundExecutionWorker worker;
    private final String workerId = UUID.randomUUID().toString();

    public RefundExecutionCoordinator(VoucherRefundAttemptMapper attempts, RefundExecutionWorker worker) {
        this.attempts = attempts;
        this.worker = worker;
    }

    /** 周期扫描最多二十个可领取任务。 */
    @Scheduled(fixedDelayString = "${ray.refund.scan-interval-ms:5000}")
    public void scan() {
        attempts.findRunnableIds(20).forEach(id -> worker.execute(id, workerId));
    }
}
