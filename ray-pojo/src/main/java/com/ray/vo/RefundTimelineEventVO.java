package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 从退款主记录和渠道尝试投影出的只读时间线事件。 */
@Schema(name = "RefundTimelineEventVO", description = "退款时间线事件")
public record RefundTimelineEventVO(
        String type,
        String title,
        String status,
        String description,
        LocalDateTime occurredAt) {
}
