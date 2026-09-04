package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.VoucherRefund;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface VoucherRefundMapper extends BaseMapper<VoucherRefund> {
    @Select("SELECT * FROM voucher_refund WHERE voucher_id = #{voucherId} ORDER BY id DESC LIMIT 1")
    VoucherRefund findLatestByVoucher(@Param("voucherId") Long voucherId);
}
