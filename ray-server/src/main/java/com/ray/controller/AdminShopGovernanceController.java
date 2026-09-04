package com.ray.controller;

import com.ray.dto.ShopGovernanceRequest;
import com.ray.enums.ShopStatus;
import com.ray.result.ErrorResult;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.AdminMerchantGovernanceService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.AdminShopDetailVO;
import com.ray.vo.AdminShopGovernanceResultVO;
import com.ray.vo.AdminShopListItemVO;
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
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/admin/shops")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "管理端门店治理")
public class AdminShopGovernanceController {
    private final AdminMerchantGovernanceService service;

    public AdminShopGovernanceController(AdminMerchantGovernanceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "分页查询治理门店", operationId = "listAdminShops")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "无门店治理权限", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<PageResult<AdminShopListItemVO>> list(
            @RequestParam(required = false) ShopStatus status,
            @RequestParam(required = false) String cityCode,
            @Parameter(description = "字符串门店类目 ID") @RequestParam(required = false) String shopTypeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.listShops(
                status,
                cityCode,
                parseOptionalId(shopTypeId, "shopTypeId"),
                keyword,
                page,
                size));
    }

    @GetMapping("/{shopId}")
    @Operation(summary = "查询门店治理详情", operationId = "getAdminShop")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "无门店治理权限", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "404", description = "门店不存在", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<AdminShopDetailVO> get(@PathVariable String shopId) {
        return Result.ok(service.getShop(shopId));
    }

    @PostMapping("/{shopId}/suspension")
    @Operation(summary = "停用门店", operationId = "suspendAdminShop")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "停用成功", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "无门店治理权限", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "404", description = "门店不存在", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "409", description = "状态、版本或幂等冲突", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<AdminShopGovernanceResultVO> suspend(
            @PathVariable String shopId,
            @Parameter(description = "8至128位治理幂等键", required = true)
                    @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}", message = "Idempotency-Key 格式无效")
                    String idempotencyKey,
            @Valid @RequestBody ShopGovernanceRequest request) {
        return Result.ok(service.suspendShop(shopId, idempotencyKey, request));
    }

    @PostMapping("/{shopId}/activation")
    @Operation(summary = "恢复门店营业", operationId = "activateAdminShop")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "恢复成功", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "无门店治理权限", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "404", description = "门店不存在", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "409", description = "状态、版本或幂等冲突", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<AdminShopGovernanceResultVO> activate(
            @PathVariable String shopId,
            @Parameter(description = "8至128位治理幂等键", required = true)
                    @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}", message = "Idempotency-Key 格式无效")
                    String idempotencyKey,
            @Valid @RequestBody ShopGovernanceRequest request) {
        return Result.ok(service.activateShop(shopId, idempotencyKey, request));
    }

    private Long parseOptionalId(String value, String field) {
        return StringUtils.hasText(value) ? IdUtils.parse(value, field) : null;
    }
}
