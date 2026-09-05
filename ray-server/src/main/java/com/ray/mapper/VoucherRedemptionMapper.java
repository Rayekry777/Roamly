package com.ray.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.VoucherRedemption;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
public interface VoucherRedemptionMapper extends BaseMapper<VoucherRedemption> {
    @Select("SELECT COUNT(*) FROM voucher_redemption WHERE voucher_id=#{voucherId} AND status='SUCCEEDED'")
    long countSucceeded(@Param("voucherId") Long voucherId);
}
