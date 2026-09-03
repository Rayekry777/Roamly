package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "商户信息")
public record ShopVO(
        @Schema(type = "string", example = "1") String id,
        @Schema(description = "商户名称", example = "西湖咖啡馆") String name,
        @Schema(type = "string", description = "商户分类 ID", example = "2") String typeId,
        @Schema(description = "以逗号分隔的图片资源地址") String images,
        @Schema(description = "商圈名称", example = "西湖景区") String area,
        @Schema(description = "详细地址", example = "杭州市西湖区北山街 1 号") String address,
        @Schema(description = "商户经度", example = "120.1551") Double longitude,
        @Schema(description = "商户纬度", example = "30.2741") Double latitude,
        @Schema(description = "人均价格，单位为元", example = "58", minimum = "0") Long avgPrice,
        @Schema(description = "累计销量", example = "128", minimum = "0") Integer sold,
        @Schema(description = "有效点评数量", example = "36", minimum = "0") Integer comments,
        @Schema(description = "评分乘 10 保存，例如 49 表示 4.9 分", example = "49", minimum = "0", maximum = "50") Integer score,
        @Schema(description = "营业时间", example = "10:00-22:00") String openHours,
        @Schema(description = "当前位置到商户的直线距离，单位为米；未提供定位时为空", example = "860.5", minimum = "0") Double distance) {}
