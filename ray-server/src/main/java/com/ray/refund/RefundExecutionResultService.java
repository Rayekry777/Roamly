package com.ray.refund;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ray.entity.PaymentTransaction;
import com.ray.entity.UserVoucher;
import com.ray.entity.VoucherOrder;
import com.ray.entity.VoucherRefund;
import com.ray.entity.VoucherRefundAttempt;
import com.ray.entity.VoucherRefundItem;
import com.ray.enums.OrderAfterSaleStatus;
import com.ray.enums.RefundExecutionStatus;
import com.ray.enums.UserVoucherStatus;
import com.ray.enums.VoucherRefundStatus;
import com.ray.mapper.PaymentTransactionMapper;
import com.ray.mapper.UserVoucherMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherRefundAttemptMapper;
import com.ray.mapper.VoucherRefundItemMapper;
import com.ray.mapper.VoucherRefundMapper;
import com.ray.realtime.RealtimeEventPublisher;
import com.ray.service.FinanceService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 在独立事务中落地退款渠道结果，并原子维护逐券、售后、支付和账本状态。 */
@Slf4j
@Service
public class RefundExecutionResultService {
    private final VoucherRefundAttemptMapper attempts;
    private final VoucherRefundItemMapper items;
    private final VoucherRefundMapper refunds;
    private final UserVoucherMapper vouchers;
    private final VoucherOrderMapper orders;
    private final PaymentTransactionMapper payments;
    private final FinanceService finance;
    private final int maxAttempts;
    private RealtimeEventPublisher realtimeEvents;

    public RefundExecutionResultService(VoucherRefundAttemptMapper attempts, VoucherRefundItemMapper items,
            VoucherRefundMapper refunds, UserVoucherMapper vouchers, VoucherOrderMapper orders,
            PaymentTransactionMapper payments, FinanceService finance,
            @Value("${ray.refund.max-attempts:3}") int maxAttempts) {
        this.attempts = attempts;
        this.items = items;
        this.refunds = refunds;
        this.vouchers = vouchers;
        this.orders = orders;
        this.payments = payments;
        this.finance = finance;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Autowired(required = false)
    void setRealtimeEvents(RealtimeEventPublisher realtimeEvents) {
        this.realtimeEvents = realtimeEvents;
    }

    /** 仅允许当前租约所有者提交渠道结果，防止过期 Worker 覆盖新结果。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean apply(Long attemptId, String workerId, RefundGatewayResult result) {
        VoucherRefundAttempt attempt = attempts.findByIdForUpdate(attemptId);
        if (attempt == null || !"PROCESSING".equals(attempt.getStatus())
                || !Objects.equals(workerId, attempt.getLeaseOwner())) {
            return false;
        }
        VoucherRefund refund = refunds.selectById(attempt.getRefundId());
        if (refund == null) {
            throw new IllegalStateException("退款任务缺少申请主记录: " + attempt.getRefundId());
        }
        LocalDateTime now = LocalDateTime.now();
        switch (result.status()) {
            case SUCCESS -> complete(refund, attempt, result, now);
            case PENDING, RETRYABLE_FAILURE -> postpone(refund, attempt, result, now);
        }
        publishAfterCommit(refund);
        return true;
    }

    private void complete(VoucherRefund refund, VoucherRefundAttempt attempt,
            RefundGatewayResult result, LocalDateTime now) {
        List<VoucherRefundItem> refundItems = items.findByRefundId(refund.getId());
        if (refundItems == null || refundItems.isEmpty()) {
            throw new IllegalStateException("退款申请缺少逐券明细: " + refund.getId());
        }
        for (VoucherRefundItem item : refundItems) {
            UserVoucher voucher = vouchers.selectById(item.getVoucherId());
            if (voucher == null) {
                throw new IllegalStateException("退款明细关联券不存在: " + item.getVoucherId());
            }
            if (!UserVoucherStatus.REFUNDED.name().equals(voucher.getStatus())) {
                int changed = vouchers.update(null, new UpdateWrapper<UserVoucher>()
                        .eq("id", item.getVoucherId()).eq("status", UserVoucherStatus.REFUNDING.name())
                        .set("status", UserVoucherStatus.REFUNDED.name()).set("refund_time", now));
                if (changed != 1) {
                    throw new IllegalStateException("退款券本地状态冲突: " + item.getVoucherId());
                }
            }
            item.setStatus("SUCCESS").setRefundAmount(item.getRefundableAmount());
            items.updateById(item);
        }
        attempt.setStatus("SUCCESS").setProviderRefundNo(result.providerRefundNo())
                .setFailureCode(null).setFailureMessage(null).setLeaseOwner(null).setLeaseUntil(null)
                .setNextRetryAt(null).setFinishedTime(now);
        attempts.updateById(attempt);
        refund.setStatus(VoucherRefundStatus.SUCCEEDED.name())
                .setExecutionStatus(RefundExecutionStatus.SUCCESS.name())
                .setProviderRefundNo(result.providerRefundNo()).setFailureCode(null).setFailureMessage(null)
                .setProcessedTime(now).setVersion(nextVersion(refund.getVersion()));
        PaymentTransaction payment = payments.findLatestByOrder(refund.getOrderId());
        refund.setPaymentProvider(payment == null ? null : payment.getProvider());
        refunds.updateById(refund);
        finance.recordRefundSuccess(refund, now);
        long totalVouchers = vouchers.selectCount(new QueryWrapper<UserVoucher>()
                .eq("order_id", refund.getOrderId()));
        long refundedVouchers = vouchers.selectCount(new QueryWrapper<UserVoucher>()
                .eq("order_id", refund.getOrderId()).eq("status", UserVoucherStatus.REFUNDED.name()));
        boolean fullyRefunded = totalVouchers > 0 && refundedVouchers >= totalVouchers;
        orders.update(null, new UpdateWrapper<VoucherOrder>().eq("id", refund.getOrderId())
                .set("after_sale_status", fullyRefunded
                        ? OrderAfterSaleStatus.REFUNDED.name() : OrderAfterSaleStatus.PARTIALLY_REFUNDED.name())
                .set(fullyRefunded, "refund_time", now));
        payments.update(null, new UpdateWrapper<PaymentTransaction>().eq("order_id", refund.getOrderId())
                .in("status", "SUCCEEDED", "PARTIALLY_REFUNDED")
                .set("status", fullyRefunded ? "REFUNDED" : "PARTIALLY_REFUNDED"));
        log.info("[退款执行] Mock 退款成功，refundId={}，attemptId={}，voucherCount={}",
                refund.getId(), attempt.getId(), refundItems.size());
    }

    private void postpone(VoucherRefund refund, VoucherRefundAttempt attempt,
            RefundGatewayResult result, LocalDateTime now) {
        int attemptNumber = attempt.getRetryCount() == null ? 1 : attempt.getRetryCount();
        boolean exhausted = result.status() == RefundGatewayResult.Status.RETRYABLE_FAILURE
                && attemptNumber >= maxAttempts;
        attempt.setStatus(exhausted ? "MANUAL_REQUIRED" : "RETRY_WAITING")
                .setProviderRefundNo(result.providerRefundNo()).setFailureCode(result.failureCode())
                .setFailureMessage(result.failureMessage()).setLeaseOwner(null).setLeaseUntil(null)
                .setNextRetryAt(exhausted ? null : now.plusSeconds(10)).setFinishedTime(exhausted ? now : null);
        attempts.updateById(attempt);
        refund.setStatus(exhausted ? VoucherRefundStatus.FAILED.name() : VoucherRefundStatus.PROCESSING.name())
                .setExecutionStatus(exhausted
                        ? RefundExecutionStatus.MANUAL_REQUIRED.name() : RefundExecutionStatus.RETRY_WAITING.name())
                .setFailureCode(result.failureCode()).setFailureMessage(result.failureMessage())
                .setLastFailureTime(result.status() == RefundGatewayResult.Status.RETRYABLE_FAILURE ? now : null)
                .setRetryCount(attemptNumber).setVersion(nextVersion(refund.getVersion()));
        refunds.updateById(refund);
        if (exhausted) {
            for (VoucherRefundItem item : items.findByRefundId(refund.getId())) {
                item.setStatus("FAILED");
                items.updateById(item);
            }
        }
        orders.update(null, new UpdateWrapper<VoucherOrder>().eq("id", refund.getOrderId())
                .set("after_sale_status", exhausted
                        ? OrderAfterSaleStatus.REFUND_FAILED.name() : OrderAfterSaleStatus.REFUNDING.name()));
        log.warn("[退款执行] Mock 退款未完成，refundId={}，attemptId={}，attemptNumber={}，status={}",
                refund.getId(), attempt.getId(), attemptNumber, attempt.getStatus());
    }

    private void publishAfterCommit(VoucherRefund refund) {
        if (realtimeEvents == null) {
            return;
        }
        Runnable publish = () -> realtimeEvents.publish(
                "REFUND_UPDATED", refund.getId().toString(), refund.getShopId());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish.run();
            }
        });
    }

    private int nextVersion(Integer version) {
        return version == null ? 1 : version + 1;
    }
}
