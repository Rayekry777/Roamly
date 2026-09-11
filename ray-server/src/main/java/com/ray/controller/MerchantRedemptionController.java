package com.ray.controller;

import com.ray.dto.RedemptionNoteUpdateDTO;
import com.ray.dto.VoucherQrTokenPreviewDTO;
import com.ray.dto.VoucherRedemptionConfirmDTO;
import com.ray.dto.VoucherRedemptionPreviewDTO;
import com.ray.dto.VoucherRedemptionReversalDTO;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.VoucherRedemptionService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.RedemptionIncomeBreakdownVO;
import com.ray.vo.VoucherRedemptionPreviewVO;
import com.ray.vo.VoucherRedemptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 商户核销、核销收入明细和撤销接口。 */
@Validated
@RestController
@RequestMapping("/v1/merchant/redemptions")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "商户核销")
public class MerchantRedemptionController {
    private final VoucherRedemptionService service;

    public MerchantRedemptionController(VoucherRedemptionService service) {
        this.service = service;
    }

    @PostMapping("/previews/by-code")
    @Operation(summary = "券码核销预览", operationId = "previewMerchantRedemptionByCode")
    public Result<VoucherRedemptionPreviewVO> preview(@Valid @RequestBody VoucherRedemptionPreviewDTO request) {
        return Result.ok(service.preview(request));
    }

    @PostMapping("/previews/by-qr-token")
    @Operation(summary = "固定二维码核销预览", operationId = "previewMerchantRedemptionByQrToken")
    public Result<VoucherRedemptionPreviewVO> previewQr(@Valid @RequestBody VoucherQrTokenPreviewDTO body) {
        return Result.ok(service.previewByQrToken(body.token()));
    }

    @PostMapping
    @Operation(summary = "确认核销", operationId = "confirmMerchantRedemption")
    public Result<VoucherRedemptionVO> confirm(
            @Valid @RequestBody VoucherRedemptionConfirmDTO request,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}") String key) {
        return Result.ok(service.confirm(request, key));
    }

    @PostMapping("/{id}/reversal")
    @Operation(summary = "撤销核销", operationId = "reverseMerchantRedemption")
    public Result<VoucherRedemptionVO> reverse(
            @PathVariable String id,
            @Valid @RequestBody VoucherRedemptionReversalDTO request,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}") String key) {
        return Result.ok(service.reverse(IdUtils.parse(id, "redemptionId"), request, key));
    }

    @GetMapping
    @Operation(summary = "按状态、关键词和日期查询门店核销", operationId = "listMerchantRedemptions")
    public Result<PageResult<VoucherRedemptionVO>> list(
            @Parameter(description = "ALL、REDEEMED、REVERSIBLE、REVERSED、REFUNDED")
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @Size(max = 120) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.listMerchant(status, keyword, from, to, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询门店核销详情", operationId = "getMerchantRedemption")
    public Result<VoucherRedemptionVO> get(@PathVariable String id) {
        return Result.ok(service.get(IdUtils.parse(id, "redemptionId"), false));
    }

    @GetMapping("/{id}/income-breakdown")
    @Operation(summary = "查询核销收入明细", operationId = "getMerchantRedemptionIncomeBreakdown")
    public Result<RedemptionIncomeBreakdownVO> income(@PathVariable String id) {
        return Result.ok(service.income(IdUtils.parse(id, "redemptionId")));
    }

    @PutMapping("/{id}/note")
    @Operation(summary = "更新核销商家备注", operationId = "updateMerchantRedemptionNote")
    public Result<VoucherRedemptionVO> updateNote(
            @PathVariable String id, @Valid @RequestBody RedemptionNoteUpdateDTO request) {
        return Result.ok(service.updateNote(IdUtils.parse(id, "redemptionId"), request));
    }
}
