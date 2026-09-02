package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "探店笔记")
public record BlogVO(
        @Schema(type = "string", example = "1") String id,
        @Schema(type = "string", example = "1") String shopId,
        @Schema(type = "string", example = "2") String userId,
        String icon,
        String name,
        Boolean likedByMe,
        String title,
        String images,
        String content,
        Integer liked,
        Integer comments,
        LocalDateTime createTime) {}
