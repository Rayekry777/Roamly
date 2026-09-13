package com.ray.vo;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "店铺发现卡片，券摘要最多两条")
public record ShopDiscoveryVO(ShopVO shop, String categoryId, String categoryName, String typeName,
        long soldCount, long availableVoucherCount, List<ShopVoucherSummaryVO> vouchers) {
    public ShopDiscoveryVO { vouchers = List.copyOf(vouchers); }
}
