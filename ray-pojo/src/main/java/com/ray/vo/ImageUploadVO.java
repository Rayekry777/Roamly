package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "图片上传结果")
public record ImageUploadVO(@Schema(description = "站内图片路径", example = "/media/user/post/1/2026/09/example.jpg") String path) {}
