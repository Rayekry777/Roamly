package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.Voucher;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 优惠券表及秒杀扩展查询的数据访问接口。 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    /** 查询指定商户的优惠券，并联查秒杀库存与有效期。 */
    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}
