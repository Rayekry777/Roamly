package com.ray.refund;

import com.ray.entity.VoucherRefund;
import com.ray.entity.VoucherRefundAttempt;
import com.ray.enums.RefundExecutionStatus;
import com.ray.enums.VoucherRefundStatus;
import com.ray.mapper.VoucherRefundAttemptMapper;
import com.ray.mapper.VoucherRefundItemMapper;
import com.ray.mapper.VoucherRefundMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 在短事务内领取退款任务并提交租约，避免渠道调用占用数据库事务。 */
@Service
public class RefundExecutionLeaseService {
    private static final int LEASE_SECONDS = 30;
    private final VoucherRefundAttemptMapper attempts;
    private final VoucherRefundItemMapper items;
    private final VoucherRefundMapper refunds;

    public RefundExecutionLeaseService(VoucherRefundAttemptMapper attempts,
            VoucherRefundItemMapper items, VoucherRefundMapper refunds) {
        this.attempts = attempts;
        this.items = items;
        this.refunds = refunds;
    }

    /** 原子领取指定任务；未取得执行权时返回空。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RefundExecutionTask claim(Long attemptId, String workerId) {
        LocalDateTime now = LocalDateTime.now();
        if (attempts.claim(attemptId, workerId, now.plusSeconds(LEASE_SECONDS)) != 1) {
            return null;
        }
        VoucherRefundAttempt attempt = attempts.selectById(attemptId);
        VoucherRefund refund = attempt == null ? null : refunds.selectById(attempt.getRefundId());
        if (attempt == null) {
            return null;
        }
        if (refund == null) {
            attempt.setStatus("MANUAL_REQUIRED").setFailureCode("REFUND_APPLICATION_MISSING")
                    .setFailureMessage("退款执行任务缺少申请主记录")
                    .setLeaseOwner(null).setLeaseUntil(null).setFinishedTime(now);
            attempts.updateById(attempt);
            return null;
        }
        refund.setStatus(VoucherRefundStatus.PROCESSING.name())
                .setExecutionStatus(RefundExecutionStatus.PROCESSING.name())
                .setExecutionStartedTime(now)
                .setRetryCount(attempt.getRetryCount())
                .setVersion(refund.getVersion() == null ? 1 : refund.getVersion() + 1);
        refunds.updateById(refund);
        var refundItems = items.findByRefundId(refund.getId());
        if (refundItems != null) {
            refundItems.forEach(item -> {
                item.setStatus("PROCESSING");
                items.updateById(item);
            });
        }
        return new RefundExecutionTask(attempt.getId(), attempt.getIdempotencyKey(),
                attempt.getRequestAmount() == null ? 0L : attempt.getRequestAmount(),
                attempt.getRetryCount() == null ? 1 : attempt.getRetryCount(), attempt.getMockScenario());
    }
}
