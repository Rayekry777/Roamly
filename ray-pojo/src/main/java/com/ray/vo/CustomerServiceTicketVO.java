package com.ray.vo;

import java.time.LocalDateTime;
import java.util.List;

public record CustomerServiceTicketVO(Long id, String ticketNo, String type, String status, String priority,
        Long userId, Long shopId, Long orderId, Long voucherId, Long refundId, Long redemptionId,
        String subject, String description, Long assigneeAdminId, String createdByType,
        LocalDateTime firstResponseTime, LocalDateTime resolvedTime, LocalDateTime closedTime,
        LocalDateTime lastMessageTime, LocalDateTime createTime, LocalDateTime updateTime,
        List<CustomerServiceMessageVO> messages) {}
