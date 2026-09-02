package com.ray.result;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 统一错误响应。 */
@Schema(name = "ErrorResult", description = "统一错误响应")
public record ErrorResult(
        @Schema(description = "稳定错误码", example = "VALIDATION_FAILED") String code,
        @Schema(description = "可安全展示的错误说明", example = "请求参数校验失败") String message,
        @Schema(description = "字段级校验错误；没有时为空数组") List<FieldErrorDetail> fieldErrors) {
    public ErrorResult {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static ErrorResult of(String code, String message) {
        return new ErrorResult(code, message, List.of());
    }
}
