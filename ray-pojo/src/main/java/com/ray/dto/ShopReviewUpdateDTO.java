package com.ray.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 更新当前用户商户点评的数据传输对象。字段语义与创建点评一致。 */
@Schema(name = "ShopReviewUpdateDTO", description = "更新当前用户商户点评")
public record ShopReviewUpdateDTO(
        @NotNull @Min(1) @Max(5)
                @Schema(description = "评分，1 到 5 分", minimum = "1", maximum = "5", example = "5",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Integer score,
        @NotBlank @Size(max = 2000)
                @Schema(description = "点评正文，去除首尾空白后 1 到 2000 字", minLength = 1, maxLength = 2000,
                        example = "环境很好，服务也很周到。", requiredMode = Schema.RequiredMode.REQUIRED)
                String content,
        @Size(max = 9)
                @ArraySchema(maxItems = 9, uniqueItems = true,
                        arraySchema = @Schema(description = "临时媒体资产 ID"),
                        schema = @Schema(type = "string", pattern = "^[1-9]\\d*$", example = "10001"))
                List<@NotBlank @Pattern(regexp = "^[1-9]\\d*$") String> mediaIds) {
    public ShopReviewUpdateDTO {
        content = content == null ? null : content.trim();
        mediaIds = mediaIds == null ? List.of() : List.copyOf(mediaIds);
    }
}
