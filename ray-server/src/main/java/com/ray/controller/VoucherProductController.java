package com.ray.controller;

import com.ray.result.Result;
import com.ray.service.VoucherProductService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.VoucherProductDetailVO;
import com.ray.vo.VoucherProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 团购商品公开查询接口。 */
@RestController
@Tag(name = "团购商品")
public class VoucherProductController {
    private final VoucherProductService service;

    public VoucherProductController(VoucherProductService service) { this.service = service; }

    /** 查询商户在售团购商品。 */
    @GetMapping("/v1/shops/{shopId}/voucher-products")
    @SecurityRequirements
    @Operation(summary = "查询商户团购商品", operationId = "listShopVoucherProducts")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<List<VoucherProductVO>> listByShop(
            @Parameter(description = "商户 ID") @PathVariable String shopId,
            @Parameter(description = "商品状态，默认 ON_SALE") @RequestParam(required = false) String status) {
        return Result.ok(service.listByShop(IdUtils.parse(shopId, "shopId"), status));
    }

    /** 查询团购商品详情。 */
    @GetMapping("/v1/voucher-products/{productId}")
    @SecurityRequirements
    @Operation(summary = "查询团购商品详情", operationId = "getVoucherProduct")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<VoucherProductDetailVO> get(
            @Parameter(description = "团购商品 ID") @PathVariable String productId) {
        return Result.ok(service.getDetail(IdUtils.parse(productId, "productId")));
    }
}
