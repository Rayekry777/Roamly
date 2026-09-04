package com.ray.realtime;

import cn.dev33.satoken.stp.StpLogic;
import com.ray.entity.MerchantAccount;
import com.ray.enums.MerchantAccountStatus;
import com.ray.mapper.MerchantAccountMapper;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** 在 WebSocket 握手阶段校验独立商户 Token 和门店范围。 */
public class MerchantWebSocketHandshakeInterceptor implements HandshakeInterceptor {
    private final StpLogic merchantStpLogic;
    private final MerchantAccountMapper accountMapper;

    public MerchantWebSocketHandshakeInterceptor(
            @Qualifier("merchantStpLogic") StpLogic merchantStpLogic,
            MerchantAccountMapper accountMapper) {
        this.merchantStpLogic = merchantStpLogic;
        this.accountMapper = accountMapper;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) return false;
        String token = header.substring("Bearer ".length()).trim();
        Object loginId = merchantStpLogic.getLoginIdByToken(token);
        if (loginId == null) return false;
        MerchantAccount account;
        try {
            account = accountMapper.selectById(Long.valueOf(loginId.toString()));
        } catch (RuntimeException exception) {
            return false;
        }
        if (account == null
                || !MerchantAccountStatus.ACTIVE.name().equals(account.getStatus())
                || account.getShopId() == null) return false;
        attributes.put("merchantAccountId", account.getId());
        attributes.put("shopId", account.getShopId());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {}
}
