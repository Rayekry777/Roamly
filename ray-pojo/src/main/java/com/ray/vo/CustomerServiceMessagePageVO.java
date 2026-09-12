package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "客服消息游标页")
public record CustomerServiceMessagePageVO(
        List<CustomerServiceMessageVO> items,
        String oldestMessageId,
        String newestMessageId,
        boolean hasMore) {
    public CustomerServiceMessagePageVO {
        items = List.copyOf(items);
    }
}
