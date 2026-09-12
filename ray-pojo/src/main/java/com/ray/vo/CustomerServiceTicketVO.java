package com.ray.vo;

import java.time.LocalDateTime;
import java.util.List;

public record CustomerServiceTicketVO(String id, String ticketNo, String type, String status, String priority,
        String applicantType, String applicantId, String relatedUserId, String relatedShopId,
        String orderId, String voucherId, String refundId, String redemptionId,
        String subject, String description, String assigneeAdminId,
        LocalDateTime firstResponseTime, LocalDateTime lastResponseTime,
        LocalDateTime waitingCustomerSince, LocalDateTime waitingMerchantSince,
        LocalDateTime resolvedTime, LocalDateTime closedTime, LocalDateTime slaDeadline,
        boolean slaBreached, boolean hasInternalNote, int unreadCount,
        LocalDateTime lastMessageTime, LocalDateTime createTime, LocalDateTime updateTime,
        List<CustomerServiceTagVO> tags, List<CustomerServiceMessageVO> messages) {
    public CustomerServiceTicketVO {
        tags = tags == null ? List.of() : List.copyOf(tags);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
