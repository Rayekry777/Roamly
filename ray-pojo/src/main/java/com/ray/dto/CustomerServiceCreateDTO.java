package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "创建客服工单")
public record CustomerServiceCreateDTO(
        @Schema(description = "工单类型", allowableValues = {"REFUND", "REDEMPTION", "ORDER", "SETTLEMENT", "GENERAL"})
                @NotBlank @Size(max = 32) String type,
        @Schema(description = "主题") @NotBlank @Size(max = 160) String subject,
        @Schema(description = "问题描述") @Size(max = 2000) String description,
        @Schema(description = "关联订单字符串 ID") String orderId,
        @Schema(description = "关联用户券字符串 ID") String voucherId,
        @Schema(description = "关联退款字符串 ID") String refundId,
        @Schema(description = "关联核销字符串 ID") String redemptionId,
        @Schema(description = "待绑定的临时附件字符串 ID，最多 9 个") @Size(max = 9) List<String> attachmentIds) {
    public CustomerServiceCreateDTO {
        attachmentIds = attachmentIds == null ? List.of() : List.copyOf(attachmentIds);
    }
}
