package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.Shop;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 商户表的数据访问接口。 */
public interface ShopMapper extends BaseMapper<Shop> {
    /** 根据有效点评事实重算商户评分和点评数量。 */
    @Update("UPDATE shop SET comments = (SELECT COUNT(*) FROM shop_review "
            + "WHERE shop_id = #{shopId} AND status = 0), "
            + "score = COALESCE((SELECT ROUND(AVG(score) * 10) FROM shop_review "
            + "WHERE shop_id = #{shopId} AND status = 0), 0) WHERE id = #{shopId}")
    int recalculateReviewSummary(@Param("shopId") Long shopId);

    /**
     * 按城市、分类、关键词及排序方式查询启用商户。
     * 当调用方提供完整坐标时，结果中的距离单位为米。
     */
    @Select("<script>SELECT s.*, "
            + "<choose><when test='longitude != null and latitude != null'>"
            + "ST_Distance_Sphere(POINT(s.x, s.y), POINT(#{longitude}, #{latitude})) "
            + "</when><otherwise>NULL </otherwise></choose>AS distance "
            + "FROM shop s WHERE s.status = 1 AND s.city_code = #{cityCode} "
            + "<if test='typeId != null'>AND s.type_id = #{typeId} </if>"
            + "<if test='keyword != null and keyword != \"\"'>AND s.name LIKE CONCAT('%', #{keyword}, '%') </if>"
            + "<choose>"
            + "<when test='sort == \"DISTANCE\"'>ORDER BY distance ASC, s.id ASC </when>"
            + "<when test='sort == \"SCORE\"'>ORDER BY s.score DESC, s.id ASC </when>"
            + "<otherwise>ORDER BY s.sold DESC, s.comments DESC, s.id ASC </otherwise>"
            + "</choose>LIMIT #{offset}, #{size}</script>")
    List<Shop> selectEnabledPage(
            @Param("cityCode") String cityCode,
            @Param("typeId") Long typeId,
            @Param("keyword") String keyword,
            @Param("sort") String sort,
            @Param("longitude") Double longitude,
            @Param("latitude") Double latitude,
            @Param("offset") int offset,
            @Param("size") int size);

    /** 统计与地理筛选相同条件的启用商户数量。 */
    @Select("<script>SELECT COUNT(*) FROM shop s WHERE s.status = 1 AND s.city_code = #{cityCode} "
            + "<if test='typeId != null'>AND s.type_id = #{typeId} </if>"
            + "<if test='keyword != null and keyword != \"\"'>AND s.name LIKE CONCAT('%', #{keyword}, '%') </if></script>")
    long countEnabledByFilter(
            @Param("cityCode") String cityCode,
            @Param("typeId") Long typeId,
            @Param("keyword") String keyword);
}
