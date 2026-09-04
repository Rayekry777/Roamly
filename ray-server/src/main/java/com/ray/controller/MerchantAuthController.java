package com.ray.controller;

import com.ray.dto.LoginDTO;
import com.ray.dto.SmsCodeDTO;
import com.ray.result.ErrorResult;
import com.ray.result.Result;
import com.ray.service.MerchantAuthService;
import com.ray.vo.AuthTokenVO;
import com.ray.vo.CurrentMerchantVO;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/merchant/auth")
@Tag(name = "商户端认证")
public class MerchantAuthController {
    private final MerchantAuthService service;

    public MerchantAuthController(MerchantAuthService service) {
        this.service = service;
    }

    @PostMapping("/sms-codes")
    @SecurityRequirements
    @Operation(summary = "发送商户短信验证码", operationId = "sendMerchantSmsCode")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "验证码已发送"),
        @ApiResponse(
                responseCode = "429",
                description = "发送过于频繁",
                content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(
                responseCode = "503",
                description = "短信或认证服务不可用",
                content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public ResponseEntity<Void> sendCode(@Valid @RequestBody SmsCodeDTO request) {
        service.sendCode(request.phone());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "商户短信登录", operationId = "loginMerchant")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "登录成功", useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "503",
                description = "认证服务不可用",
                content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<AuthTokenVO> login(@Valid @RequestBody LoginDTO request) {
        return Result.ok(service.login(request));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "查询当前商户账号", operationId = "getCurrentMerchant")
    public Result<CurrentMerchantVO> me() {
        return Result.ok(service.currentMerchant());
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "注销商户当前会话", operationId = "logoutMerchant")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "注销成功"))
    public ResponseEntity<Void> logout() {
        service.logout();
        return ResponseEntity.noContent().build();
    }
}
