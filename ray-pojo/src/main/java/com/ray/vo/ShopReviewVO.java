package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** 商户点评展示模型。 */
@Schema(name = "ShopReviewVO", description = "商户点评")
public record ShopReviewVO(
        @Schema(type = "string", example = "501") String id,
        @Schema(type = "string", example = "4") String shopId,
        @Schema(description = "点评作者") UserVO author,
        @Schema(description = "评分，1 到 5 分", minimum = "1", maximum = "5", example = "5") int score,
        @Schema(description = "点评正文") String content,
        @Schema(description = "点评图片") List<ReviewMediaVO> media,
        @Schema(description = "是否由已核销消费凭证认证") boolean verifiedConsumption,
        @Schema(description = "当前用户是否可编辑") boolean editable,
        @Schema(description = "点评状态", allowableValues = {"NORMAL", "HIDDEN", "DELETED"}) String status,
        @Schema(description = "创建时间", format = "date-time") LocalDateTime createdTime,
        @Schema(description = "更新时间", format = "date-time") LocalDateTime updatedTime) {
    public ShopReviewVO {
        media = media == null ? List.of() : List.copyOf(media);
    }
}
