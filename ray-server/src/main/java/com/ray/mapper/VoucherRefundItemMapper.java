package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.VoucherRefundItem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 退款逐券明细持久化访问。 */
@Mapper
public interface VoucherRefundItemMapper extends BaseMapper<VoucherRefundItem> {
    /** 按退款申请读取稳定顺序的逐券明细。 */
    @Select("SELECT * FROM voucher_refund_item WHERE refund_id=#{refundId} ORDER BY id")
    List<VoucherRefundItem> findByRefundId(@Param("refundId") Long refundId);

    /** 查询订单中仍处于有效退款流程的券。 */
    @Select("SELECT DISTINCT i.voucher_id FROM voucher_refund_item i JOIN voucher_refund r ON r.id=i.refund_id "
            + "WHERE r.order_id=#{orderId} AND r.status IN ('REQUESTED','PROCESSING','SUCCEEDED')")
    List<Long> findActiveVoucherIds(@Param("orderId") Long orderId);
}
