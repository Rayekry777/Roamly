package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "客服工单消息")
public record CustomerServiceMessageVO(String id, String ticketId, String senderType, String senderId,
        String visibility, String messageType, String content, LocalDateTime createTime,
        List<CustomerServiceAttachmentVO> attachments) {
    public CustomerServiceMessageVO {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
