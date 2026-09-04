package com.ray.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** 配置跨实例事件订阅基础设施。 */
@Configuration
public class RealtimeInfrastructureConfig {
    @Bean
    MerchantWebSocketSessionRegistry merchantWebSocketSessionRegistry() {
        return new MerchantWebSocketSessionRegistry();
    }

    @Bean
    AdminSseSessionRegistry adminSseSessionRegistry() {
        return new AdminSseSessionRegistry();
    }

    @Bean
    ChannelTopic realtimeEventTopic() {
        return new ChannelTopic(RealtimeEventPublisherImpl.CHANNEL);
    }

    @Bean
    RedisMessageListenerContainer realtimeEventListenerContainer(
            RedisConnectionFactory connectionFactory,
            RealtimeEventSubscriber subscriber,
            ChannelTopic realtimeEventTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, realtimeEventTopic);
        return container;
    }
}
