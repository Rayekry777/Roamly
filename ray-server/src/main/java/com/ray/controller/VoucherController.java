package com.ray.controller;

import com.ray.dto.CreateSeckillVoucherDTO;
import com.ray.dto.CreateVoucherDTO;
import com.ray.result.Result;
import com.ray.service.VoucherService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.IdVO;
import com.ray.vo.VoucherVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "优惠券")
public class VoucherController {
    private final VoucherService service;

    public VoucherController(VoucherService service) {
        this.service = service;
    }

    @GetMapping("/v1/shops/{shopId}/vouchers")
    @SecurityRequirements
    @Operation(summary = "查询商户优惠券", operationId = "listShopVouchers")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<List<VoucherVO>> list(@Parameter(description = "商户 ID") @PathVariable String shopId) {
        return Result.ok(service.listShopVouchers(IdUtils.parse(shopId, "shopId")));
    }

    @PostMapping("/v1/vouchers")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "新增普通优惠券", operationId = "createVoucher")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "创建成功", useReturnTypeSchema = true))
    public ResponseEntity<Result<IdVO>> create(@Valid @RequestBody CreateVoucherDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(new IdVO(IdUtils.format(service.createVoucher(request)))));
    }

    @PostMapping("/v1/seckill-vouchers")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "新增秒杀优惠券", operationId = "createSeckillVoucher")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "创建成功", useReturnTypeSchema = true))
    public ResponseEntity<Result<IdVO>> createSeckill(@Valid @RequestBody CreateSeckillVoucherDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(new IdVO(IdUtils.format(service.createSeckillVoucher(request)))));
    }
}
