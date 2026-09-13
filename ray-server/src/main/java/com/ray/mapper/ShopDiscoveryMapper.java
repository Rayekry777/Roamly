package com.ray.mapper;
import com.ray.service.discovery.*;
import java.util.List;
import org.apache.ibatis.annotations.Param;
/** 店铺分页及批量券摘要访问，查询数量与页大小无关。 */
public interface ShopDiscoveryMapper {
    /** 按分类和位置返回一页营业店铺。 */
    List<DiscoveryShopRow> page(@Param("q") DiscoveryQuery query);
    /** 统计相同候选边界下的店铺数。 */
    long count(@Param("q") DiscoveryQuery query);
    /** 批量读取各店热销和低价候选，每个排序最多两条。 */
    List<DiscoveryVoucherRow> vouchers(@Param("ids") List<Long> ids, @Param("q") DiscoveryQuery query);
}
