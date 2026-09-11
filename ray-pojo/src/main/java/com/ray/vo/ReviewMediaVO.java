package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 点评中已绑定图片的展示信息。 */
@Schema(name = "ReviewMediaVO", description = "商户点评图片")
public record ReviewMediaVO(
        @Schema(type = "string", example = "10001") String id,
        @Schema(description = "图片资源地址", example = "/media/user/review/1/2026/09/photo.jpg") String url,
        @Schema(description = "图片 MIME 类型", example = "image/jpeg") String mimeType,
        @Schema(description = "文件字节数", example = "204800") long size,
        @Schema(description = "图片像素宽度", example = "1080") int width,
        @Schema(description = "图片像素高度", example = "1440") int height) {}
