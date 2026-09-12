package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "客服私有附件")
public record CustomerServiceAttachmentVO(
        String id,
        String ticketId,
        String messageId,
        String status,
        String originalFilename,
        String mimeType,
        long byteSize,
        String contentPath) {}
