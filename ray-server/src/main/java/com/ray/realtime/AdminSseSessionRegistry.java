package com.ray.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 管理端 SSE 会话注册表，按固定权限过滤刷新事件。 */
@Slf4j
public class AdminSseSessionRegistry {
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    /** 注册管理端长连接；30 秒限制属于连接票据，不是事件流寿命。 */
    public SseEmitter register(Long adminId, java.util.Collection<String> permissions) {
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L);
        Subscription subscription = new Subscription(adminId, emitter, Set.copyOf(permissions));
        subscriptions.put(id, subscription);
        Runnable remove = () -> subscriptions.remove(id);
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            remove.run();
            emitter.complete();
        });
        emitter.onError(ignored -> remove.run());
        return emitter;
    }

    /** 管理员退出、停用、改密或角色变化时立即关闭其全部事件流。 */
    public void disconnectAdmin(Long adminId) {
        subscriptions.forEach((id, subscription) -> {
            if (adminId.equals(subscription.adminId()) && subscriptions.remove(id, subscription)) {
                subscription.emitter().complete();
            }
        });
    }

    /** 向具有对应权限的管理端连接广播资源刷新事件。 */
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
            } catch (IOException | IllegalStateException exception) {
                log.debug("[管理端实时事件] 移除已断开的SSE连接，会话={}", entry.getKey());
                return true;
            }
        });
    }

    /** 周期发送注释心跳，保持空闲连接并及时清理已断开的客户端。 */
    @Scheduled(fixedDelayString = "${ray.realtime.sse-heartbeat-interval-ms:25000}")
    public void heartbeat() {
        subscriptions.entrySet().removeIf(entry -> {
            try {
                entry.getValue().emitter().send(SseEmitter.event().comment("keepalive"));
                return false;
            } catch (IOException | IllegalStateException exception) {
                log.debug("[管理端实时事件] 心跳发现SSE连接已断开，会话={}", entry.getKey());
                return true;
            }
        });
    }

    int activeSessionCount() {
        return subscriptions.size();
    }

    private record Subscription(Long adminId, SseEmitter emitter, Set<String> permissions) {}
}
