package com.ray.result;

import io.swagger.v3.oas.annotations.media.Schema;

/** 字段校验错误。 */
@Schema(description = "字段校验错误")
public record FieldErrorDetail(String field, String message) {}
