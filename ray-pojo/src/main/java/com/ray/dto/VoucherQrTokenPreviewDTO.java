package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 商户使用固定二维码发起核销预览。 */
@Schema(description = "固定二维码预览请求")
public record VoucherQrTokenPreviewDTO(
        @NotBlank @Schema(description = "rq1 固定二维码内容", requiredMode = Schema.RequiredMode.REQUIRED)
        String token) {}
