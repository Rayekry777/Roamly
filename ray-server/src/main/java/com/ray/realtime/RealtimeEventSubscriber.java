package com.ray.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/** 接收 Redis 事件并投递到本实例的商户 WebSocket 与管理 SSE。 */
@Slf4j
@Component
public class RealtimeEventSubscriber implements MessageListener {
    private final ObjectMapper objectMapper;
    private final MerchantWebSocketSessionRegistry merchantSessions;
    private final AdminSseSessionRegistry adminSessions;

    public RealtimeEventSubscriber(
            ObjectMapper objectMapper,
            MerchantWebSocketSessionRegistry merchantSessions,
            AdminSseSessionRegistry adminSessions) {
        this.objectMapper = objectMapper;
        this.merchantSessions = merchantSessions;
        this.adminSessions = adminSessions;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
            RealtimeEvent event = objectMapper.readValue(payload, RealtimeEvent.class);
            merchantSessions.broadcast(event.shopId(), payload);
            adminSessions.broadcast(event);
        } catch (Exception exception) {
            log.warn("[实时事件] 消费消息格式异常", exception);
        }
    }
}
