package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 动态详情中的轻量商户连接。 */
@Schema(name = "ShopSummaryVO", description = "动态关联商户摘要")
public record ShopSummaryVO(
        @Schema(type = "string", description = "商户 ID", example = "4") String id,
        @Schema(description = "商户名称", example = "Mamala") String name,
        @Schema(type = "string", description = "商户分类 ID", example = "1") String typeId,
        @Schema(description = "封面资源地址") String cover,
        @Schema(description = "地址") String address,
        @Schema(description = "评分放大 10 倍后的整数", example = "49") int score) {}
