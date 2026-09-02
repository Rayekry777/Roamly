package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 上传成功后的临时媒体资产。 */
@Schema(name = "MediaAssetVO", description = "临时媒体资产")
public record MediaAssetVO(
        @Schema(type = "string", description = "媒体资产 ID", example = "10001") String id,
        @Schema(description = "相对资源路径", example = "/blogs/1/2/example.jpg") String path,
        @Schema(description = "实际识别的 MIME 类型", example = "image/jpeg") String mimeType,
        @Schema(description = "文件字节数", example = "204800") long size,
        @Schema(description = "图片像素宽度", example = "1080") int width,
        @Schema(description = "图片像素高度", example = "1440") int height,
        @Schema(description = "临时资产过期时间", format = "date-time") LocalDateTime expiresAt) {}
