package com.ray.vo;

import java.time.LocalDateTime;

public record CustomerServiceMessageVO(Long id, Long ticketId, String senderType, Long senderId,
        String visibility, String messageType, String content, LocalDateTime createTime) {}
