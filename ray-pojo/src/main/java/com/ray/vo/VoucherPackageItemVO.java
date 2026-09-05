package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 消费者可见的套餐券或次卡明细。 */
@Schema(name = "VoucherPackageItemVO", description = "团购商品套餐明细")
public record VoucherPackageItemVO(
        @Schema(type = "string") String id,
        String name,
        Integer quantity,
        String unit,
        Long unitPriceAmount,
        Integer sortOrder) {}
