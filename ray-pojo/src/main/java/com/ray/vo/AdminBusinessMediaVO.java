package com.ray.vo;

import com.ray.enums.BusinessMediaPurpose;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端申请私有经营媒体摘要")
public record AdminBusinessMediaVO(
        @Schema(type = "string", description = "经营媒体 ID") String id,
        @Schema(description = "媒体用途") BusinessMediaPurpose purpose,
        @Schema(description = "用途中文名") String purposeLabel,
        String originalFilename,
        String mimeType,
        @Schema(minimum = "0") long byteSize,
        @Schema(minimum = "1") int width,
        @Schema(minimum = "1") int height,
        @Schema(description = "管理端鉴权内容读取路径") String contentPath) {}
