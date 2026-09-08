package com.ray.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/** 商户售后列表面向页面页签的聚合阶段。 */
@Schema(description = "商户售后聚合阶段")
public enum MerchantAfterSaleStage {
    PENDING,
    PROCESSING,
    DECLINED,
    COMPLETED
}
