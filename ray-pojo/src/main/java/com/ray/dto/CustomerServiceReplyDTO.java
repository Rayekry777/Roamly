package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "客服回复或内部备注")
public record CustomerServiceReplyDTO(
        @Schema(description = "消息正文") @NotBlank @Size(max = 4000) String content,
        @Schema(description = "消息类型", allowableValues = {"TEXT", "IMAGE", "ATTACHMENT"}) @Size(max = 16) String messageType,
        @Schema(description = "待绑定的临时附件字符串 ID，最多 9 个") @Size(max = 9) List<String> attachmentIds) {
    public CustomerServiceReplyDTO {
        attachmentIds = attachmentIds == null ? List.of() : List.copyOf(attachmentIds);
    }
}
