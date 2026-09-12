package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.VoucherRefundAttempt;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 可租约领取的退款执行尝试持久化访问。 */
@Mapper
public interface VoucherRefundAttemptMapper extends BaseMapper<VoucherRefundAttempt> {
    /** 查询当前可执行或租约已过期的任务。 */
    @Select("SELECT id FROM voucher_refund_attempt WHERE "
            + "((status IN ('WAITING','RETRY_WAITING') AND (next_retry_at IS NULL OR next_retry_at<=NOW())) "
            + "OR (status='PROCESSING' AND lease_until<NOW())) ORDER BY id LIMIT #{limit}")
    List<Long> findRunnableIds(@Param("limit") int limit);

    /** 以条件更新原子领取任务，返回 1 表示当前实例取得执行权。 */
    @Update("UPDATE voucher_refund_attempt SET status='PROCESSING', lease_owner=#{owner}, lease_until=#{leaseUntil}, "
            + "started_time=COALESCE(started_time,NOW()), retry_count=retry_count+1 "
            + "WHERE id=#{id} AND (((status IN ('WAITING','RETRY_WAITING')) "
            + "AND (next_retry_at IS NULL OR next_retry_at<=NOW()) AND (lease_until IS NULL OR lease_until<NOW())) "
            + "OR (status='PROCESSING' AND lease_until<NOW()))")
    int claim(@Param("id") Long id, @Param("owner") String owner,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    /** 按幂等键返回已有执行尝试。 */
    @Select("SELECT * FROM voucher_refund_attempt WHERE idempotency_key=#{key} LIMIT 1")
    VoucherRefundAttempt findByIdempotencyKey(@Param("key") String key);

    /** 锁定执行尝试，保证租约所有者校验与结果提交不可被重新领取穿透。 */
    @Select("SELECT * FROM voucher_refund_attempt WHERE id=#{id} FOR UPDATE")
    VoucherRefundAttempt findByIdForUpdate(@Param("id") Long id);

    /** 查询退款申请最近创建的执行尝试。 */
    @Select("SELECT * FROM voucher_refund_attempt WHERE refund_id=#{refundId} ORDER BY created_time DESC, id DESC LIMIT 1")
    VoucherRefundAttempt findLatestByRefundId(@Param("refundId") Long refundId);

    /** 按时间顺序读取退款申请的全部执行尝试。 */
    @Select("SELECT * FROM voucher_refund_attempt WHERE refund_id=#{refundId} ORDER BY created_time, id")
    List<VoucherRefundAttempt> findByRefundId(@Param("refundId") Long refundId);
}
