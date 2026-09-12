package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.VoucherRefund;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 退款申请聚合的查询与审核锁定访问。 */
public interface VoucherRefundMapper extends BaseMapper<VoucherRefund> {
    /** 通过逐券明细查询最近退款，支持多券申请中的任意一张券。 */
    @Select("SELECT r.* FROM voucher_refund r JOIN voucher_refund_item i ON i.refund_id=r.id "
            + "WHERE i.voucher_id=#{voucherId} ORDER BY r.id DESC LIMIT 1")
    VoucherRefund findLatestByVoucher(@Param("voucherId") Long voucherId);

    /** 锁定退款主记录，串行化审核、拒绝和人工重试。 */
    @Select("SELECT * FROM voucher_refund WHERE id=#{id} FOR UPDATE")
    VoucherRefund findByIdForUpdate(@Param("id") Long id);

    /** 按门店读取退款申请。 */
    @Select("SELECT * FROM voucher_refund WHERE shop_id = #{shopId} ORDER BY created_time DESC, id DESC")
    java.util.List<VoucherRefund> findByShop(@Param("shopId") Long shopId);
}
