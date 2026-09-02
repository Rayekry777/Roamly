package com.ray.controller;

import com.ray.result.Result;
import com.ray.service.VoucherOrderService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.IdVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/seckill-vouchers")
@Tag(name = "秒杀订单")
@SecurityRequirement(name = "BearerAuth")
public class VoucherOrderController {
    private final VoucherOrderService service;

    public VoucherOrderController(VoucherOrderService service) {
        this.service = service;
    }

    @PostMapping("/{voucherId}/orders")
    @Operation(summary = "创建秒杀订单", operationId = "createSeckillOrder")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "下单请求已受理", useReturnTypeSchema = true))
    public ResponseEntity<Result<IdVO>> create(@Parameter(description = "秒杀优惠券 ID") @PathVariable String voucherId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(
                        new IdVO(IdUtils.format(service.createSeckillOrder(IdUtils.parse(voucherId, "voucherId"))))));
    }
}
