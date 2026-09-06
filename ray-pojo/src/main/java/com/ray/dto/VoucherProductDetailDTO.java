package com.ray.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 商品详情分段请求。 */
public record VoucherProductDetailDTO(
        @NotBlank @Size(max = 32) String sectionType,
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 4000) String content,
        Integer sortOrder) {}
