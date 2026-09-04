package com.ray.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** 商户端只接收资源刷新事件，业务事实仍由 HTTP 权威接口确认。 */
public class MerchantWebSocketHandler extends TextWebSocketHandler {
    private final MerchantWebSocketSessionRegistry sessions;
    private final ObjectMapper objectMapper;

    public MerchantWebSocketHandler(MerchantWebSocketSessionRegistry sessions, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long shopId = shopId(session);
        sessions.register(shopId, session);
        session.sendMessage(new TextMessage(
                objectMapper.writeValueAsString(java.util.Map.of(
                        "type", "CONNECTED",
                        "shopId", shopId.toString(),
                        "occurredAt", LocalDateTime.now().toString()))));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode body;
        try {
            body = objectMapper.readTree(message.getPayload());
        } catch (Exception exception) {
            session.sendMessage(new TextMessage("{\"type\":\"MESSAGE_INVALID\"}"));
            return;
        }
        if ("PING".equals(body.path("type").asText())) {
            session.sendMessage(new TextMessage(
                    objectMapper.writeValueAsString(java.util.Map.of(
                            "type", "PONG", "occurredAt", LocalDateTime.now().toString()))));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object shopId = session.getAttributes().get("shopId");
        if (shopId instanceof Long id) sessions.unregister(id, session);
    }

    private Long shopId(WebSocketSession session) throws IOException {
        Object value = session.getAttributes().get("shopId");
        if (value instanceof Long id) return id;
        session.close(CloseStatus.NOT_ACCEPTABLE.withReason("商户门店无效"));
        throw new IOException("商户门店无效");
    }
}
