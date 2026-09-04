package com.ray.realtime;

/** 发布可丢失的资源刷新事件。 */
public interface RealtimeEventPublisher {
    void publish(String type, String resourceId, Long shopId);
}
