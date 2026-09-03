package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.ShopReview;
import com.ray.entity.ShopReviewMedia;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 商户点评表的数据访问接口。 */
public interface ShopReviewMapper extends BaseMapper<ShopReview> {
    /** 查询指定商户、指定用户的点评，供唯一点评和编辑校验使用。 */
    @Select("SELECT * FROM shop_review WHERE shop_id = #{shopId} AND user_id = #{userId} LIMIT 1")
    ShopReview selectByShopAndUser(@Param("shopId") Long shopId, @Param("userId") Long userId);

    /** 批量查询点评所属的已绑定媒体关系。 */
    @Select("<script>SELECT * FROM shop_review_media WHERE review_id IN "
            + "<foreach collection='reviewIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY review_id ASC, sort ASC, id ASC</script>")
    List<ShopReviewMedia> selectMediaRelations(@Param("reviewIds") List<Long> reviewIds);
}
