package com.ray.service.impl;

import com.ray.service.AdminAuthService;
import com.ray.service.EventTicketService;
import com.ray.vo.AdminEventTicketVO;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 管理端 SSE 短期票据，绑定管理员并只允许一次连接消费。 */
@Service
public class EventTicketServiceImpl implements EventTicketService {
    private static final String PREFIX = "roamly:event:ticket:";

    private final AdminAuthService auth;
    private final StringRedisTemplate redis;

    public EventTicketServiceImpl(AdminAuthService auth, StringRedisTemplate redis) {
        this.auth = auth;
        this.redis = redis;
    }

    @Override
    public AdminEventTicketVO issue() {
        Long adminId = auth.currentAdminId();
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(PREFIX + token, adminId.toString(), 30, TimeUnit.SECONDS);
        return new AdminEventTicketVO(token, LocalDateTime.now().plusSeconds(30));
    }

    @Override
    public boolean consume(String token, Long adminId) {
        if (token == null || token.isBlank() || adminId == null) return false;
        String value = redis.opsForValue().getAndDelete(PREFIX + token);
        return adminId.toString().equals(value);
    }
}
