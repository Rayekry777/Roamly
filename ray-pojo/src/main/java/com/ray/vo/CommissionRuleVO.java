package com.ray.vo;import java.time.LocalDateTime;public record CommissionRuleVO(String id,String shopId,Integer rateBps,LocalDateTime effectiveFrom,LocalDateTime effectiveTo,Integer version){}
