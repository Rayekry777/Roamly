package com.ray.controller;

import com.ray.dto.AdminLoginDTO;
import com.ray.dto.AdminPasswordChangeDTO;
import com.ray.result.Result;
import com.ray.service.AdminAuthService;
import com.ray.vo.AdminAuthTokenVO;
import com.ray.vo.CurrentAdminVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/auth")
@Tag(name = "管理端认证")
public class AdminAuthController {
    private final AdminAuthService service;

    public AdminAuthController(AdminAuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "管理员登录", operationId = "loginAdmin")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "登录成功", useReturnTypeSchema = true))
    public Result<AdminAuthTokenVO> login(@Valid @RequestBody AdminLoginDTO request, HttpServletRequest servletRequest) {
        return Result.ok(service.login(request, servletRequest.getRemoteAddr()));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "查询当前管理员", operationId = "getCurrentAdmin")
    public Result<CurrentAdminVO> me() {
        return Result.ok(service.currentAdmin());
    }

    @PutMapping("/password")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "修改管理员本人密码", operationId = "changeAdminPassword")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "密码修改成功且原会话已注销"))
    public ResponseEntity<Void> changePassword(@Valid @RequestBody AdminPasswordChangeDTO request) {
        service.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "注销管理员当前会话", operationId = "logoutAdmin")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "注销成功"))
    public ResponseEntity<Void> logout() {
        service.logout();
        return ResponseEntity.noContent().build();
    }
}
