package com.ray.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 商品展示标签请求。 */
public record VoucherProductTagDTO(
        @NotBlank @Size(max = 32) String text,
        @NotBlank @Size(max = 32) String iconKey,
        @Size(max = 32) String colorToken,
        Integer sortOrder) {}
