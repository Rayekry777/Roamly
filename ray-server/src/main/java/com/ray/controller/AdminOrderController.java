package com.ray.controller;

import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.AdminOrderService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.VoucherOrderDetailVO;
import com.ray.vo.VoucherOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端订单和支付查询。 */
@RestController
@RequestMapping("/v1/admin/orders")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "管理端订单")
public class AdminOrderController {
    private final AdminOrderService service;

    public AdminOrderController(AdminOrderService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "查询订单列表", operationId = "listAdminOrders")
    public Result<PageResult<VoucherOrderVO>> list(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.list(status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询订单详情", operationId = "getAdminOrder")
    public Result<VoucherOrderDetailVO> get(@PathVariable String id) {
        return Result.ok(service.get(IdUtils.parse(id, "orderId")));
    }
}
