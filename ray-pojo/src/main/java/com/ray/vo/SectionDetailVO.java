package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 官方内容分区详情。 */
@Schema(name = "SectionDetailVO", description = "官方内容分区详情")
public record SectionDetailVO(
        @Schema(type = "string", description = "分区 ID", example = "1") String id,
        @Schema(description = "稳定分区编码", example = "ROAM_DAILY") String code,
        @Schema(description = "分区名称", example = "漫游日常") String name,
        @Schema(description = "图标相对资源路径", example = "/sections/roam-daily.png") String icon,
        @Schema(description = "是否允许发布探店动态", example = "false") boolean allowShopVisit,
        @Schema(description = "当前用户是否已关注；匿名时为 false", example = "false") boolean followedByMe,
        @Schema(description = "分区说明", example = "记录日常生活与城市漫游") String description,
        @Schema(description = "封面相对资源路径", example = "/sections/roam-daily-cover.jpg") String cover) {}
