package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.VoucherProduct;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 团购商品数据访问接口。 */
public interface VoucherProductMapper extends BaseMapper<VoucherProduct> {
    /** 在商品仍可售且库存充足时原子扣减库存。 */
    @Update("UPDATE voucher_product SET available_stock = available_stock - #{quantity}, "
            + "sale_status = CASE WHEN available_stock - #{quantity} = 0 THEN 'SOLD_OUT' ELSE sale_status END, version = version + 1 "
            + "WHERE id = #{productId} AND review_status = 'APPROVED' AND sale_status = 'ON_SALE' "
            + "AND available_stock >= #{quantity} "
            + "AND (sale_begin_time IS NULL OR sale_begin_time <= NOW()) "
            + "AND (sale_end_time IS NULL OR sale_end_time >= NOW())")
    int deductStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    /** 取消未支付订单时原子返还库存。 */
    @Update("UPDATE voucher_product SET available_stock = available_stock + #{quantity}, "
            + "sale_status = CASE WHEN sale_status = 'SOLD_OUT' THEN 'ON_SALE' ELSE sale_status END, version = version + 1 "
            + "WHERE id = #{productId}")
    int restoreStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    /** 在支付成功后增加已售数量；库存已在创建订单时预扣。 */
    @Update("UPDATE voucher_product SET sold_count = sold_count + #{quantity}, version = version + 1 "
            + "WHERE id = #{productId}")
    int increaseSoldCount(@Param("productId") Long productId, @Param("quantity") int quantity);

    /** 按商品 ID 加行锁读取商户命令的权威事实。 */
    @org.apache.ibatis.annotations.Select("SELECT * FROM voucher_product WHERE id = #{productId} FOR UPDATE")
    VoucherProduct selectByIdForUpdate(@Param("productId") Long productId);
}
