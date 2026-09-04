package com.ray.controller;

import com.ray.dto.LoginDTO;
import com.ray.dto.SmsCodeDTO;
import com.ray.result.ErrorResult;
import com.ray.result.Result;
import com.ray.service.UserService;
import com.ray.vo.AuthTokenVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "认证")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/sms-codes")
    @SecurityRequirements
    @Operation(summary = "发送短信验证码", operationId = "sendSmsCode")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "验证码已发送"),
        @ApiResponse(
                responseCode = "503",
                description = "当前环境未配置短信供应商",
                content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public ResponseEntity<Void> sendCode(@Valid @RequestBody SmsCodeDTO request) {
        userService.sendCode(request.phone());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions")
    @SecurityRequirements
    @Operation(summary = "短信验证码登录", operationId = "createAuthSession")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "登录成功", useReturnTypeSchema = true))
    public Result<AuthTokenVO> login(@Valid @RequestBody LoginDTO request) {
        return Result.ok(userService.login(request));
    }

    @DeleteMapping("/session")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "注销当前会话", operationId = "deleteAuthSession")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "当前 Token 已注销"))
    public ResponseEntity<Void> logout() {
        userService.logout();
        return ResponseEntity.noContent().build();
    }
}
