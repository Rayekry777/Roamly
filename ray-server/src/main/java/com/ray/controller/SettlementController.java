package com.ray.controller;

import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.SettlementService;
import com.ray.service.impl.AdminExportService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.SettlementBatchVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "结算与导出")
public class SettlementController {
    private final SettlementService service;
    private final AdminExportService exports;
    public SettlementController(SettlementService service, AdminExportService exports) { this.service = service; this.exports = exports; }
    @GetMapping("/v1/admin/settlements") @Operation(summary = "查询结算批次", operationId = "listAdminSettlements")
    public Result<PageResult<SettlementBatchVO>> listAdmin(@RequestParam(defaultValue = "1") @Min(1) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return Result.ok(service.list(page, size, true)); }
    @GetMapping("/v1/admin/settlements/{id}") @Operation(summary = "结算详情", operationId = "getAdminSettlement")
    public Result<SettlementBatchVO> getAdmin(@PathVariable String id) { return Result.ok(service.get(IdUtils.parse(id, "settlementId"), true)); }
    @PostMapping("/v1/admin/settlements/{id}/retry") @Operation(summary = "重试结算", operationId = "retryAdminSettlement")
    public Result<SettlementBatchVO> retry(@PathVariable String id, @RequestHeader("Idempotency-Key") String key) { return Result.ok(service.retry(IdUtils.parse(id, "settlementId"), key)); }
    @GetMapping("/v1/merchant/settlements") @Operation(summary = "查询商户结算", operationId = "listMerchantSettlements")
    public Result<PageResult<SettlementBatchVO>> listMerchant(@RequestParam(defaultValue = "1") @Min(1) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return Result.ok(service.list(page, size, false)); }
    @GetMapping("/v1/merchant/settlements/{id}") @Operation(summary = "商户结算详情", operationId = "getMerchantSettlement")
    public Result<SettlementBatchVO> getMerchant(@PathVariable String id) { return Result.ok(service.get(IdUtils.parse(id, "settlementId"), false)); }
    @PostMapping("/v1/admin/{resource}/export") @Operation(summary = "导出管理数据", operationId = "exportAdminResource")
    public ResponseEntity<byte[]> export(@PathVariable String resource) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=roamly-" + resource + ".xlsx").body(exports.export(resource));
    }
}
