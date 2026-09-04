package com.ray.controller;

import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.MerchantOrderService;
import com.ray.vo.VoucherOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

/** 商户端门店订单查询。 */
@RestController
@RequestMapping("/v1/merchant/orders")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "商户订单")
public class MerchantOrderController {
    private final MerchantOrderService service;
    public MerchantOrderController(MerchantOrderService service) { this.service = service; }
    @GetMapping
    @Operation(summary = "查询门店订单", operationId = "listMerchantOrders")
    public Result<PageResult<VoucherOrderVO>> list(@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") @Min(1) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return Result.ok(service.list(status, page, size)); }
}
