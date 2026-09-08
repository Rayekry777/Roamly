package com.ray.controller;

import com.ray.dto.LoginDTO;
import com.ray.dto.PasswordLoginDTO;
import com.ray.dto.RegistrationDTO;
import com.ray.dto.SmsCodeDTO;
import com.ray.result.ErrorResult;
import com.ray.result.Result;
import com.ray.service.ConsumerAuthService;
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
import jakarta.servlet.http.HttpServletRequest;
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
    private final ConsumerAuthService authService;

    public AuthController(ConsumerAuthService authService) {
        this.authService = authService;
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
        authService.sendCode(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions")
    @SecurityRequirements
    @Operation(summary = "短信验证码登录", operationId = "createAuthSession")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "登录成功", useReturnTypeSchema = true))
    public Result<AuthTokenVO> login(@Valid @RequestBody LoginDTO request) {
        return Result.ok(authService.loginByCode(request));
    }

    @PostMapping("/registrations")
    @SecurityRequirements
    @Operation(summary = "注册消费者并登录", operationId = "registerConsumer")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "注册并登录成功", useReturnTypeSchema = true))
    public Result<AuthTokenVO> register(@Valid @RequestBody RegistrationDTO request) {
        return Result.ok(authService.register(request));
    }

    @PostMapping("/password-sessions")
    @SecurityRequirements
    @Operation(summary = "消费者密码登录", operationId = "createPasswordAuthSession")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "登录成功", useReturnTypeSchema = true))
    public Result<AuthTokenVO> passwordLogin(
            @Valid @RequestBody PasswordLoginDTO request, HttpServletRequest servletRequest) {
        return Result.ok(authService.loginByPassword(request, servletRequest.getRemoteAddr()));
    }

    @DeleteMapping("/session")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "注销当前会话", operationId = "deleteAuthSession")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "当前 Token 已注销"))
    public ResponseEntity<Void> logout() {
        authService.logout();
        return ResponseEntity.noContent().build();
    }
}
