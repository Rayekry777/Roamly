package com.ray.controller;

import com.ray.dto.CommissionRuleUpdateDTO;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.FinanceService;
import com.ray.vo.CommissionRuleVO;
import com.ray.vo.FundLedgerEntryVO;
import com.ray.vo.MerchantFinanceSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "佣金与资金账本")
public class FinanceController {
    private final FinanceService service;
    public FinanceController(FinanceService service) { this.service = service; }
    @GetMapping("/v1/admin/commission-rules")
    @Operation(summary = "查询佣金规则", operationId = "getCommissionRule")
    public Result<CommissionRuleVO> rule(@RequestParam(required = false) String shopId) { return Result.ok(service.rule(shopId)); }
    @PutMapping("/v1/admin/commission-rules")
    @Operation(summary = "更新佣金规则", operationId = "updateCommissionRule")
    public Result<CommissionRuleVO> update(@RequestParam(required = false) String shopId, @Valid @RequestBody CommissionRuleUpdateDTO request,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}") String key) { return Result.ok(service.update(shopId, request, key)); }
    @GetMapping("/v1/admin/ledger-entries")
    @Operation(summary = "查询资金账本", operationId = "listLedgerEntries")
    public Result<PageResult<FundLedgerEntryVO>> ledger(@RequestParam(defaultValue = "1") @Min(1) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return Result.ok(service.ledger(page, size)); }
    @GetMapping("/v1/admin/finance/summary")
    @Operation(summary = "查询平台资金总览", operationId = "getAdminFinanceSummary")
    public Result<MerchantFinanceSummaryVO> adminSummary() { return Result.ok(service.adminSummary()); }
    @GetMapping("/v1/merchant/finance/summary")
    @Operation(summary = "查询商户财务摘要", operationId = "getMerchantFinanceSummary")
    public Result<MerchantFinanceSummaryVO> summary() { return Result.ok(service.merchantSummary()); }
}
