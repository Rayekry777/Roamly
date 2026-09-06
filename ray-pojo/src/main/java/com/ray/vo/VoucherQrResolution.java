package com.ray.vo;

/** 已完成格式、签名和有效期校验的固定二维码定位结果。 */
public record VoucherQrResolution(Long voucherId, Long userId) {}
