package com.ray.controller;

import com.ray.dto.AdminPasswordResetDTO;
import com.ray.dto.AdminUserCreateDTO;
import com.ray.dto.AdminUserUpdateDTO;
import com.ray.dto.AdminUserVersionDTO;
import com.ray.enums.AdminRole;
import com.ray.enums.AdminStatus;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.AdminUserService;
import com.ray.vo.AdminUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/admin/users")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "管理员账号")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "分页查询管理员", operationId = "listAdminUsers")
    public Result<PageResult<AdminUserVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AdminRole role,
            @RequestParam(required = false) AdminStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.list(keyword, role, status, page, size));
    }

    @PostMapping
    @Operation(summary = "创建管理员", operationId = "createAdminUser")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "创建成功", useReturnTypeSchema = true))
    public ResponseEntity<Result<AdminUserVO>> create(@Valid @RequestBody AdminUserCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.ok(service.create(request)));
    }

    @GetMapping("/{adminUserId}")
    @Operation(summary = "查询管理员详情", operationId = "getAdminUser")
    public Result<AdminUserVO> get(
            @Parameter(description = "管理员 ID") @PathVariable String adminUserId) {
        return Result.ok(service.get(adminUserId));
    }

    @PutMapping("/{adminUserId}")
    @Operation(summary = "编辑管理员", operationId = "updateAdminUser")
    public Result<AdminUserVO> update(
            @Parameter(description = "管理员 ID") @PathVariable String adminUserId,
            @Valid @RequestBody AdminUserUpdateDTO request) {
        return Result.ok(service.update(adminUserId, request));
    }

    @PostMapping("/{adminUserId}/activation")
    @Operation(summary = "启用管理员", operationId = "activateAdminUser")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "启用成功"))
    public ResponseEntity<Void> activate(
            @PathVariable String adminUserId, @Valid @RequestBody AdminUserVersionDTO request) {
        service.activate(adminUserId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{adminUserId}/disablement")
    @Operation(summary = "停用管理员", operationId = "disableAdminUser")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "停用成功"))
    public ResponseEntity<Void> disable(
            @PathVariable String adminUserId, @Valid @RequestBody AdminUserVersionDTO request) {
        service.disable(adminUserId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{adminUserId}/password-reset")
    @Operation(summary = "重置管理员密码", operationId = "resetAdminUserPassword")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "密码重置成功"))
    public ResponseEntity<Void> resetPassword(
            @PathVariable String adminUserId, @Valid @RequestBody AdminPasswordResetDTO request) {
        service.resetPassword(adminUserId, request);
        return ResponseEntity.noContent().build();
    }
}
