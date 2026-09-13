package com.ray.service.discovery;
import com.ray.entity.Shop;
import lombok.Getter;
import lombok.Setter;
/** 店铺分页查询的轻量投影。 */
@Getter @Setter
public class DiscoveryShopRow extends Shop {
    private String categoryName;
    private String typeName;
    private Long categoryId;
    private Long totalSold;
}
