package com.ray.vo;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/** 券有效期内保持不变的固定二维码内容。 */
@Schema(description = "固定券二维码")
public record VoucherQrTokenVO(
        @Schema(description = "二维码内容，不包含裸券 ID") String token,
        @Schema(description = "券二维码有效截止时间") LocalDateTime expiresAt) {}
