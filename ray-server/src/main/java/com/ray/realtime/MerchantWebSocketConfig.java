package com.ray.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ray.mapper.MerchantAccountMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import cn.dev33.satoken.stp.StpLogic;

/** 注册商户端 WebSocket 协议边界。 */
@Configuration
@EnableWebSocket
public class MerchantWebSocketConfig implements WebSocketConfigurer {
    private final MerchantWebSocketHandler handler;
    private final StpLogic merchantStpLogic;
    private final MerchantAccountMapper accountMapper;

    public MerchantWebSocketConfig(
            MerchantWebSocketSessionRegistry sessions,
            ObjectMapper objectMapper,
            @Qualifier("merchantStpLogic") StpLogic merchantStpLogic,
            MerchantAccountMapper accountMapper) {
        this.handler = new MerchantWebSocketHandler(sessions, objectMapper);
        this.merchantStpLogic = merchantStpLogic;
        this.accountMapper = accountMapper;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/v1/merchant/ws")
                .addInterceptors(new MerchantWebSocketHandshakeInterceptor(merchantStpLogic, accountMapper))
                .setAllowedOriginPatterns("*");
    }
}
