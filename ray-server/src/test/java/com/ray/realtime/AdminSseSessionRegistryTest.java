package com.ray.realtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AdminSseSessionRegistryTest {
    @Test
    void shouldKeepSseConnectionOpenUntilClientDisconnects() {
        AdminSseSessionRegistry registry = new AdminSseSessionRegistry();

        SseEmitter emitter = registry.register(7L, List.of("admin:refund:manage"));

        assertEquals(0L, emitter.getTimeout());
        assertEquals(1, registry.activeSessionCount());
        assertDoesNotThrow(registry::heartbeat);

        registry.disconnectAdmin(7L);

        assertEquals(0, registry.activeSessionCount());
    }
}
