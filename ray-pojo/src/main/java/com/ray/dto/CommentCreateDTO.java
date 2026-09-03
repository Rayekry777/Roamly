package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建根评论或追加回复的数据传输对象。 */
@Schema(name = "CommentCreateDTO", description = "创建动态评论或追加回复")
public record CommentCreateDTO(
        @NotBlank
                @Size(max = 1000)
                @Schema(
                        description = "评论正文，去除首尾空白后 1 到 1000 字",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        maxLength = 1000,
                        example = "环境很好，下次还会再来。")
                String content) {}
