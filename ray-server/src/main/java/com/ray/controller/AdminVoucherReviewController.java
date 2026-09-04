package com.ray.controller;

import com.ray.dto.VoucherReviewApprovalRequest;
import com.ray.dto.VoucherReviewRejectionRequest;
import com.ray.enums.VoucherProductType;
import com.ray.enums.VoucherReviewStatus;
import com.ray.result.ErrorResult;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.AdminVoucherReviewService;
import com.ray.vo.AdminVoucherReviewDetailVO;
import com.ray.vo.AdminVoucherReviewListItemVO;
import com.ray.vo.AdminVoucherReviewResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/** 管理端四类券审核接口。 */
@Validated
@RestController
@RequestMapping("/v1/admin/voucher-reviews")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "管理端团购券审核")
public class AdminVoucherReviewController {
    private final AdminVoucherReviewService service;

    public AdminVoucherReviewController(AdminVoucherReviewService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "分页查询团购券审核", operationId = "listAdminVoucherReviews")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "无团购券审核权限", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<PageResult<AdminVoucherReviewListItemVO>> list(
            @RequestParam(required = false) VoucherReviewStatus status,
            @RequestParam(required = false) VoucherProductType productType,
            @Parameter(description = "字符串门店 ID") @RequestParam(required = false) String shopId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.list(status, productType, shopId, keyword, page, size));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "查询团购券审核详情", operationId = "getAdminVoucherReview")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "无团购券审核权限", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "404", description = "团购券不存在", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<AdminVoucherReviewDetailVO> get(@PathVariable String productId) {
        return Result.ok(service.get(productId));
    }

    @PostMapping("/{productId}/approval")
    @Operation(summary = "审核通过团购券", operationId = "approveAdminVoucherReview")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "审核通过", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "无团购券审核权限", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "404", description = "团购券不存在", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "409", description = "版本、状态或幂等冲突", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<AdminVoucherReviewResultVO> approve(
            @PathVariable String productId,
            @Parameter(description = "8至128位审核幂等键", required = true)
                    @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}", message = "Idempotency-Key 格式无效")
                    String idempotencyKey,
            @Valid @RequestBody VoucherReviewApprovalRequest request) {
        return Result.ok(service.approve(productId, idempotencyKey, request));
    }

    @PostMapping("/{productId}/rejection")
    @Operation(summary = "驳回团购券", operationId = "rejectAdminVoucherReview")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "审核驳回", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "无团购券审核权限", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "404", description = "团购券不存在", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "409", description = "版本、状态或幂等冲突", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<AdminVoucherReviewResultVO> reject(
            @PathVariable String productId,
            @Parameter(description = "8至128位审核幂等键", required = true)
                    @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}", message = "Idempotency-Key 格式无效")
                    String idempotencyKey,
            @Valid @RequestBody VoucherReviewRejectionRequest request) {
        return Result.ok(service.reject(productId, idempotencyKey, request));
    }
}
