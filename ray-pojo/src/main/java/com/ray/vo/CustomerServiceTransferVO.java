package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "客服转交记录")
public record CustomerServiceTransferVO(String id, String ticketId, String fromAdminId,
        String toAdminId, String operatorAdminId, String reason, LocalDateTime createTime) {}
