package com.ray.vo;

import com.ray.enums.BusinessMediaPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "私有经营媒体摘要")
public record BusinessMediaVO(
        @Schema(type = "string", description = "经营媒体 ID") String id,
        @Schema(description = "媒体用途") BusinessMediaPurpose purpose,
        @Schema(description = "用途中文名") String purposeLabel,
        @Schema(description = "原始文件名") String originalFilename,
        @Schema(description = "实际 MIME") String mimeType,
        @Schema(description = "字节数") long byteSize,
        @Schema(description = "像素宽度") int width,
        @Schema(description = "像素高度") int height,
        @Schema(description = "鉴权内容读取路径") String contentPath,
        @Schema(description = "临时媒体过期时间，已绑定时为空") LocalDateTime expiresAt) {}
