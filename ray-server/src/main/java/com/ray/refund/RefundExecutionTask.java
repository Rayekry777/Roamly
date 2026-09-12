package com.ray.refund;

/** 已提交租约后可在数据库事务外调用退款渠道的任务快照。 */
public record RefundExecutionTask(
        Long attemptId,
        String idempotencyKey,
        long requestAmount,
        int attemptNumber,
        String mockScenario) {
}
