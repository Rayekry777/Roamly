package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "客服快捷回复")
public record CustomerServiceQuickReplyVO(String id, String title, String content,
        String scope, String ownerAdminId, int sortOrder) {}
