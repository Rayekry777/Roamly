package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 套餐券或次卡明细展示模型。 */
@Schema(description = "套餐券或次卡明细")
public record MerchantVoucherPackageItemVO(
        @Schema(type = "string", description = "明细 ID") String id,
        String name,
        Integer quantity,
        String unit,
        Long unitPriceAmount,
        Integer sortOrder) {}
