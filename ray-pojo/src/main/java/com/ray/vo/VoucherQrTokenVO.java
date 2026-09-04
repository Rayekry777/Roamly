package com.ray.vo;
import java.time.LocalDateTime;
public record VoucherQrTokenVO(String token, LocalDateTime expiresAt) {}
