package com.ray.controller;

import com.ray.dto.MerchantRefundDTO;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.VoucherRefundService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.VoucherRefundVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;

/** 商户售后只读与发起申请；资金审批统一由管理端完成。 */
@RestController
@RequestMapping("/v1/merchant/after-sales")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "商户售后")
public class MerchantRefundController {
    private final VoucherRefundService service;
    public MerchantRefundController(VoucherRefundService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "查询门店售后", operationId = "listMerchantAfterSales")
    public Result<PageResult<VoucherRefundVO>> list(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.merchantList(status, page, size));
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
