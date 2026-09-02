package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 动态中已绑定图片的展示信息。 */
@Schema(name = "PostMediaVO", description = "动态图片")
public record PostMediaVO(
        @Schema(type = "string", description = "媒体 ID", example = "101") String id,
        @Schema(description = "相对资源路径", example = "/blogs/1/2/photo.jpg") String path,
        @Schema(description = "图片 MIME 类型", example = "image/jpeg") String mimeType,
        @Schema(description = "像素宽度", example = "1080") int width,
        @Schema(description = "像素高度", example = "1440") int height) {}
