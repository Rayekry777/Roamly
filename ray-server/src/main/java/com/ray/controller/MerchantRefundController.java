package com.ray.controller;

import com.ray.dto.MerchantRefundDTO;
import com.ray.enums.MerchantAfterSaleStage;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.VoucherRefundService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.MerchantRefundCandidateVO;
import com.ray.vo.VoucherRefundVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

/** 商户售后只读与发起申请；资金审批统一由管理端完成。 */
@RestController
@RequestMapping("/v1/merchant/after-sales")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "商户售后")
@Validated
public class MerchantRefundController {
    private final VoucherRefundService service;
    public MerchantRefundController(VoucherRefundService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "查询门店售后", operationId = "listMerchantAfterSales")
    public Result<PageResult<VoucherRefundVO>> list(
            @Parameter(description = "精确退款状态；不能与 stage 同时使用")
            @RequestParam(required = false) String status,
            @Parameter(description = "页面聚合阶段；不能与 status 同时使用")
            @RequestParam(required = false) MerchantAfterSaleStage stage,
            @Parameter(description = "退款单号、订单号或券码精确关键词")
            @RequestParam(required = false) @Size(max = 64) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        if (status != null && !status.isBlank() && stage != null)
            throw com.ray.exception.BusinessException.badRequest("INVALID_REFUND_FILTER", "status 与 stage 不能同时使用");
        return Result.ok(service.merchantList(status, stage, keyword, page, size));
    }

    /** 查询当前门店订单内各张券的退款资格。 */
    @GetMapping("/candidate")
    @Operation(summary = "查询退款候选订单", operationId = "getMerchantRefundCandidate")
    public Result<MerchantRefundCandidateVO> candidate(
            @Parameter(description = "本店订单号或完整券码", required = true)
            @RequestParam @NotBlank @Size(max = 64) String keyword) {
        return Result.ok(service.merchantCandidate(keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询售后详情", operationId = "getMerchantAfterSale")
    public Result<VoucherRefundVO> get(@PathVariable String id) {
        return Result.ok(service.merchantGet(IdUtils.parse(id, "refundId")));
    }

    @PostMapping
    @Operation(summary = "发起门店退款申请", operationId = "createMerchantAfterSale")
    public Result<VoucherRefundVO> create(@Valid @RequestBody MerchantRefundDTO request,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}") String key) {
        return Result.ok(service.merchantRequest(request, key));
    }
}
