package com.ray.controller;

import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.AdminAuditQueryService;
import com.ray.vo.AdminAuditLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端操作审计查询。 */
@RestController
@RequestMapping("/v1/admin/audit-logs")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "操作审计")
public class AdminAuditController {
    private final AdminAuditQueryService service;

    public AdminAuditController(AdminAuditQueryService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "查询操作审计", operationId = "listAdminAuditLogs")
    public Result<PageResult<AdminAuditLogVO>> list(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.list(page, size));
    }
}
