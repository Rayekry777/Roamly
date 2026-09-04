package com.ray.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 管理端 SSE 会话注册表，按固定权限过滤刷新事件。 */
public class AdminSseSessionRegistry {
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public SseEmitter register(java.util.Collection<String> permissions) {
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(30_000L);
        Subscription subscription = new Subscription(emitter, Set.copyOf(permissions));
        subscriptions.put(id, subscription);
        Runnable remove = () -> subscriptions.remove(id);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());
        return emitter;
    }

    public void broadcast(RealtimeEvent event) {
        subscriptions.entrySet().removeIf(entry -> {
            Subscription subscription = entry.getValue();
            if (!subscription.permissions().contains(event.requiredPermission())) return false;
            try {
                subscription.emitter().send(SseEmitter.event()
                        .id(event.eventId())
                        .name(event.type())
                        .data(event));
                return false;
            } catch (IOException exception) {
                subscription.emitter().completeWithError(exception);
                return true;
            }
        });
    }

    private record Subscription(SseEmitter emitter, Set<String> permissions) {}
}
