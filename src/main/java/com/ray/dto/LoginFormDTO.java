package com.ray.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
public class LoginFormDTO {
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
    private String phone;

    @Pattern(regexp = "^$|\\d{6}", message = "验证码必须为6位数字")
    private String code;
    private String password;
}
