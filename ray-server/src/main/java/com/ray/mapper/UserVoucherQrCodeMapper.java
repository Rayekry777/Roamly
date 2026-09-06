package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.UserVoucherQrCode;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 用户券固定二维码凭证数据访问接口。 */
public interface UserVoucherQrCodeMapper extends BaseMapper<UserVoucherQrCode> {
    /** 按用户券查询固定二维码凭证。 */
    @Select("SELECT * FROM user_voucher_qr_code WHERE voucher_id=#{voucherId} LIMIT 1")
    UserVoucherQrCode findByVoucherId(@Param("voucherId") Long voucherId);

    /** 按不可猜测的定位值查询二维码凭证。 */
    @Select("SELECT * FROM user_voucher_qr_code WHERE token_key=#{tokenKey} LIMIT 1")
    UserVoucherQrCode findByTokenKey(@Param("tokenKey") String tokenKey);
}
