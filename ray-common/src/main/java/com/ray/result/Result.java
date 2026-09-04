package com.ray.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** 统一成功响应。 */
@Schema(name = "Result", description = "统一成功响应")
public record Result<T>(
        @Schema(description = "业务结果码", example = "OK") String code,
        @Schema(description = "结果说明", example = "操作成功") String message,
        @JsonInclude(JsonInclude.Include.ALWAYS) @Schema(description = "响应数据，可为空") T data) {
    public static <T> Result<T> ok(T data) {
        return new Result<>("OK", "操作成功", data);
    }
}
