package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 当前门店生效的软件服务费规则。 */
@Schema(name = "ServiceFeePolicyVO", description = "平台软件服务费规则")
public record ServiceFeePolicyVO(
        @Schema(type = "string") String ruleId,
        @Schema(type = "string") String shopId,
        Integer rateBps,
        String rateText,
        String formula,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo) {}
