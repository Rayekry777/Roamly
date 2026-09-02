package com.ray.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 完整替换动态可编辑内容的请求。 */
@Schema(name = "PostUpdateRequest", description = "完整替换动态的可编辑内容")
public record PostUpdateRequest(
        @Size(max = 120)
                @Schema(description = "可选标题，空白值按未填写处理", maxLength = 120, example = "周末探店")
                String title,
        @NotBlank
                @Size(max = 5000)
                @Schema(
                        description = "动态正文",
                        minLength = 1,
                        maxLength = 5000,
                        example = "环境舒适，推荐招牌菜。",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String content,
        @Size(max = 9)
                @ArraySchema(
                        maxItems = 9,
                        uniqueItems = true,
                        arraySchema = @Schema(description = "完整媒体 ID 列表，按展示顺序排列"),
                        schema = @Schema(type = "string", pattern = "^[1-9]\\d*$", example = "101"))
                List<@NotBlank @Pattern(regexp = "^[1-9]\\d*$") String> mediaIds,
        @NotNull
                @Schema(
                        description = "是否为探店动态",
                        example = "true",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Boolean shopVisit,
        @Pattern(regexp = "^[1-9]\\d*$")
                @Schema(type = "string", description = "探店分区 ID，仅探店时提交", pattern = "^[1-9]\\d*$", example = "2")
                String sectionId,
        @Pattern(regexp = "^[1-9]\\d*$")
                @Schema(type = "string", description = "探店商户 ID，仅探店时提交", pattern = "^[1-9]\\d*$", example = "4")
                String shopId) {
    public PostUpdateRequest {
        mediaIds = mediaIds == null ? List.of() : List.copyOf(mediaIds);
    }
}
