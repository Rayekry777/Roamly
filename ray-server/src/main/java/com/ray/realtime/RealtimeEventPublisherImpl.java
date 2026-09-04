package com.ray.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 使用 Redis Pub/Sub 将业务提交后的刷新事件广播到各服务实例。 */
@Slf4j
@Component
public class RealtimeEventPublisherImpl implements RealtimeEventPublisher {
    public static final String CHANNEL = "roamly:realtime-events";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RealtimeEventPublisherImpl(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String type, String resourceId, Long shopId) {
        RealtimeEvent event = new RealtimeEvent(
                UUID.randomUUID().toString(),
                type,
                resourceId,
                shopId,
                requiredPermission(type),
                LocalDateTime.now());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
            return;
        }
        send(event);
    }

    private void send(RealtimeEvent event) {
        try {
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (DataAccessException | JsonProcessingException exception) {
            // 事件只负责刷新提示，不能让 Redis 故障破坏已经提交的业务事实。
            log.warn("[实时事件] 发布失败，type={}，resourceId={}", event.type(), event.resourceId(), exception);
        }
    }

    private String requiredPermission(String type) {
        return switch (type) {
            case "REFUND_UPDATED" -> "admin:refund:manage";
            case "SETTLEMENT_UPDATED" -> "admin:settlement:manage";
            case "MERCHANT_REVIEWED" -> "admin:merchant-application:review";
            default -> "admin:trade:read";
        };
    }
}
