package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "发布探店笔记请求")
public record CreateBlogDTO(
        @NotBlank
                @Pattern(regexp = "^\\d+$")
                @Schema(
                        description = "关联商户 ID；无关联商户时传 0",
                        example = "1",
                        pattern = "^\\d+$",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String shopId,
        @NotBlank
                @Size(max = 255)
                @Schema(
                        description = "笔记标题",
                        example = "周末探店",
                        maxLength = 255,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String title,
        @NotBlank
                @Size(max = 2048)
                @Schema(
                        description = "图片路径，多个路径以逗号分隔",
                        example = "/blogs/a/b/example.jpg",
                        maxLength = 2048,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String images,
        @NotBlank
                @Size(max = 2048)
                @Schema(
                        description = "笔记正文",
                        example = "环境舒适，推荐招牌菜。",
                        maxLength = 2048,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String content) {}
