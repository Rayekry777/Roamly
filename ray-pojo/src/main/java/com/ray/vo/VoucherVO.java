package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "优惠券")
public record VoucherVO(
        @Schema(type = "string", example = "1") String id,
        @Schema(type = "string", example = "2") String shopId,
        String title,
        String subTitle,
        String rules,
        Long payValue,
        Long actualValue,
        Integer type,
        Integer status,
        Integer stock,
        LocalDateTime beginTime,
        LocalDateTime endTime) {}
