package com.ray.realtime;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.web.socket.WebSocketSession;

/** 按门店隔离商户 WebSocket 会话。 */
public class MerchantWebSocketSessionRegistry {
    private final ConcurrentMap<Long, Set<WebSocketSession>> sessionsByShop = new ConcurrentHashMap<>();

    public void register(Long shopId, WebSocketSession session) {
        sessionsByShop.computeIfAbsent(shopId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(Long shopId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByShop.get(shopId);
        if (sessions == null) return;
        sessions.remove(session);
        if (sessions.isEmpty()) sessionsByShop.remove(shopId, sessions);
    }

    public void broadcast(Long shopId, String payload) {
        if (shopId == null) return;
        Set<WebSocketSession> sessions = sessionsByShop.get(shopId);
        if (sessions == null) return;
        sessions.removeIf(session -> !session.isOpen());
        for (WebSocketSession session : sessions) {
            try {
                session.sendMessage(new org.springframework.web.socket.TextMessage(payload));
            } catch (IOException exception) {
                sessions.remove(session);
                closeQuietly(session);
            }
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException ignored) {
            // 已经断开的连接无需重复处理。
        }
    }
}
