package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "昵称更新请求")
public record NicknameUpdateDTO(@NotBlank @Size(min = 2, max = 16) String nickName) {}
