package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.Shop;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 商户表的数据访问接口。 */
public interface ShopMapper extends BaseMapper<Shop> {
    /** 根据有效点评事实重算商户评分和点评数量。 */
    @Update("UPDATE shop SET comments = (SELECT COUNT(*) FROM shop_review "
            + "WHERE shop_id = #{shopId} AND status = 0), "
            + "score = COALESCE((SELECT ROUND(AVG(score) * 10) FROM shop_review "
            + "WHERE shop_id = #{shopId} AND status = 0), 0) WHERE id = #{shopId}")
    int recalculateReviewSummary(@Param("shopId") Long shopId);
}
