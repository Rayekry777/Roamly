package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.VoucherProduct;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 团购商品数据访问接口。 */
public interface VoucherProductMapper extends BaseMapper<VoucherProduct> {
    /** 商品综合推荐基础分叠加区县匹配和真实距离衰减。 */
    String RECOMMENDED_SCORE_SQL =
            "((LN(1 + CAST(vp.sold_count AS DECIMAL(20, 6))) * 100 + COALESCE(s.score, 0) * 2) "
                    + "* CASE WHEN #{districtCode} IS NOT NULL AND s.district_code = #{districtCode} "
                    + "THEN 1.15 ELSE 1.0 END "
                    + "* CASE WHEN #{longitude} IS NOT NULL AND #{latitude} IS NOT NULL "
                    + "THEN 1 / (1 + COALESCE(ST_Distance_Sphere(POINT(s.x, s.y), "
                    + "POINT(#{longitude}, #{latitude})), 1000000) / 3000) ELSE 1.0 END)";

    /** 按城市、门店分类、关键词和排序读取消费者可见商品。 */
    @Select("<script>SELECT vp.*, s.name AS shop_name, s.type_id AS shop_type_id, "
            + "SUBSTRING_INDEX(s.images, ',', 1) AS shop_cover, s.address AS shop_address, "
            + "s.score AS shop_score, "
            + "<choose><when test='longitude != null and latitude != null'>"
            + "ST_Distance_Sphere(POINT(s.x, s.y), POINT(#{longitude}, #{latitude})) "
            + "</when><otherwise>NULL </otherwise></choose>AS distance "
            + "FROM voucher_product vp JOIN shop s ON s.id = vp.shop_id "
            + "WHERE s.status = 'ACTIVE' AND s.city_code = #{cityCode} "
            + "AND vp.review_status = 'APPROVED' AND vp.sale_status = 'ON_SALE' "
            + "AND vp.available_stock &gt; 0 "
            + "AND (vp.sale_begin_time IS NULL OR vp.sale_begin_time &lt;= NOW()) "
            + "AND (vp.sale_end_time IS NULL OR vp.sale_end_time &gt;= NOW()) "
            + "<if test='typeId != null'>AND s.type_id = #{typeId} </if>"
            + "<if test='keyword != null and keyword != \"\"'>AND (vp.title LIKE CONCAT('%', #{keyword}, '%') "
            + "OR vp.sub_title LIKE CONCAT('%', #{keyword}, '%') OR s.name LIKE CONCAT('%', #{keyword}, '%')) </if>"
            + "<choose>"
            + "<when test='sort == \"SALES\"'>ORDER BY vp.sold_count DESC, vp.id DESC </when>"
            + "<when test='sort == \"DISTANCE\"'>ORDER BY distance ASC, vp.id DESC </when>"
            + "<when test='sort == \"PRICE_ASC\"'>ORDER BY vp.price_amount ASC, vp.id DESC </when>"
            + "<otherwise>ORDER BY " + RECOMMENDED_SCORE_SQL + " DESC, vp.id DESC </otherwise>"
            + "</choose>LIMIT #{offset}, #{size}</script>")
    List<VoucherProduct> selectPublicPage(
            @Param("cityCode") String cityCode,
            @Param("districtCode") String districtCode,
            @Param("typeId") Long typeId,
            @Param("keyword") String keyword,
            @Param("sort") String sort,
            @Param("longitude") Double longitude,
            @Param("latitude") Double latitude,
            @Param("offset") int offset,
            @Param("size") int size);

    /** 统计与消费者商品列表相同过滤条件的商品数。 */
    @Select("<script>SELECT COUNT(*) FROM voucher_product vp JOIN shop s ON s.id = vp.shop_id "
            + "WHERE s.status = 'ACTIVE' AND s.city_code = #{cityCode} "
            + "AND vp.review_status = 'APPROVED' AND vp.sale_status = 'ON_SALE' "
            + "AND vp.available_stock &gt; 0 "
            + "AND (vp.sale_begin_time IS NULL OR vp.sale_begin_time &lt;= NOW()) "
            + "AND (vp.sale_end_time IS NULL OR vp.sale_end_time &gt;= NOW()) "
            + "<if test='typeId != null'>AND s.type_id = #{typeId} </if>"
            + "<if test='keyword != null and keyword != \"\"'>AND (vp.title LIKE CONCAT('%', #{keyword}, '%') "
            + "OR vp.sub_title LIKE CONCAT('%', #{keyword}, '%') OR s.name LIKE CONCAT('%', #{keyword}, '%')) </if>"
            + "</script>")
    long countPublic(
            @Param("cityCode") String cityCode,
            @Param("typeId") Long typeId,
            @Param("keyword") String keyword);

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
