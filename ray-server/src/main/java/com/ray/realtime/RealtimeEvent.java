package com.ray.realtime;

import java.time.LocalDateTime;

/** 仅用于通知资源回查的跨实例事件，不承载业务状态。 */
public record RealtimeEvent(
        String eventId,
        String type,
        String resourceId,
        Long shopId,
        String requiredPermission,
        LocalDateTime occurredAt) {}
