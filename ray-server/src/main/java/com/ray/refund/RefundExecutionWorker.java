package com.ray.refund;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 编排短事务租约、事务外 Mock 渠道调用和独立结果事务。 */
@Slf4j
@Service
public class RefundExecutionWorker {
    private final RefundExecutionLeaseService leases;
    private final RefundExecutionResultService results;
    private final RefundGateway gateway;

    public RefundExecutionWorker(RefundExecutionLeaseService leases,
            RefundExecutionResultService results, RefundGateway gateway) {
        this.leases = leases;
        this.results = results;
        this.gateway = gateway;
    }

    /** 领取一次任务，在事务外调用 Mock 渠道，再由独立事务提交结果。 */
    public boolean execute(Long attemptId, String workerId) {
        RefundExecutionTask task = leases.claim(attemptId, workerId);
        if (task == null) {
            return false;
        }
        RefundGatewayResult result;
        try {
            result = gateway.refund(task.idempotencyKey(), task.requestAmount(),
                    task.attemptNumber(), task.mockScenario());
        } catch (RuntimeException exception) {
            log.warn("[退款执行] Mock 渠道调用异常，attemptId={}，attemptNumber={}",
                    task.attemptId(), task.attemptNumber(), exception);
            result = new RefundGatewayResult(RefundGatewayResult.Status.RETRYABLE_FAILURE,
                    null, "MOCK_GATEWAY_EXCEPTION", exception.getMessage());
        }
        try {
            results.apply(task.attemptId(), workerId, result);
        } catch (RuntimeException exception) {
            // 租约与渠道结果已经独立提交；本地事务失败后由租约超时重放同一幂等键恢复。
            log.error("[退款执行] 渠道结果本地提交失败，等待租约恢复，attemptId={}",
                    task.attemptId(), exception);
        }
        return true;
    }
}
