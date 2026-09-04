package com.ray.controller;

import com.ray.dto.VoucherOrderCreateDTO;
import com.ray.dto.VoucherPaymentRequest;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.VoucherTradeService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.UserVoucherVO;
import com.ray.vo.VoucherOrderDetailVO;
import com.ray.vo.VoucherOrderVO;
import com.ray.vo.VoucherOrderConfirmationVO;
import com.ray.vo.VoucherPaymentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.RestController;

/** 团购订单和券包接口。 */
@Validated
@RestController
@Tag(name = "团购订单与券包")
@SecurityRequirement(name = "BearerAuth")
public class VoucherTradeController {
    private final VoucherTradeService service;
    private final com.ray.service.VoucherPaymentService paymentService;

    public VoucherTradeController(VoucherTradeService service, com.ray.service.VoucherPaymentService paymentService) {
        this.service = service; this.paymentService = paymentService;
    }
    public VoucherTradeController(VoucherTradeService service) { this(service, null); }

    /** 读取服务端订单确认快照，不占用库存。 */
    @PostMapping("/v1/voucher-products/{productId}/order-confirmations")
    @Operation(summary = "确认团购订单", operationId = "confirmVoucherProductOrder")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "确认成功", useReturnTypeSchema = true))
    public Result<VoucherOrderConfirmationVO> confirm(
            @Parameter(description = "团购商品 ID") @PathVariable String productId,
            @Valid @RequestBody VoucherOrderCreateDTO dto) {
        return Result.ok(service.confirmOrder(IdUtils.parse(productId, "productId"), dto.quantity()));
    }

    /** 创建待支付团购订单。 */
    @PostMapping("/v1/voucher-products/{productId}/orders")
    @Operation(summary = "创建团购订单", operationId = "createVoucherProductOrder")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "创建成功", useReturnTypeSchema = true))
    public ResponseEntity<Result<VoucherOrderVO>> create(
            @Parameter(description = "团购商品 ID") @PathVariable String productId,
            @Valid @RequestBody VoucherOrderCreateDTO dto,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}") String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(service.createOrder(IdUtils.parse(productId, "productId"), dto, idempotencyKey)));
    }

    /** 查询当前用户订单。 */
    @GetMapping("/v1/users/me/orders")
    @Operation(summary = "查询我的团购订单", operationId = "listMyVoucherOrders")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<PageResult<VoucherOrderVO>> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.ok(service.listOrders(status, page, size));
    }

    /** 查询当前用户订单详情。 */
    @GetMapping("/v1/users/me/orders/{orderId}")
    @Operation(summary = "查询团购订单详情", operationId = "getMyVoucherOrder")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<VoucherOrderDetailVO> getOrder(@PathVariable String orderId) {
        return Result.ok(service.getOrder(IdUtils.parse(orderId, "orderId")));
    }

    /** 取消待支付订单。 */
    @DeleteMapping("/v1/users/me/orders/{orderId}")
    @Operation(summary = "取消待支付订单", operationId = "cancelMyVoucherOrder")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "取消成功"))
    public ResponseEntity<Void> cancel(@PathVariable String orderId) {
        service.cancelOrder(IdUtils.parse(orderId, "orderId"));
        return ResponseEntity.noContent().build();
    }

    /** Mock 支付订单；真实渠道由后续微信支付适配器接入。 */
    @PostMapping("/v1/users/me/orders/{orderId}/payments")
    @Operation(summary = "支付团购订单", operationId = "payMyVoucherOrder")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "支付结果", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "503", description = "支付服务不可用")})
    public Result<VoucherPaymentVO> pay(@PathVariable String orderId, @Valid @RequestBody VoucherPaymentRequest request,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}") String idempotencyKey) {
        return Result.ok(paymentService.pay(IdUtils.parse(orderId, "orderId"), request, idempotencyKey));
    }

    /** 查询当前用户券包。 */
    @GetMapping("/v1/users/me/vouchers")
    @Operation(summary = "查询我的券包", operationId = "listMyVouchers")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<PageResult<UserVoucherVO>> listVouchers(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.ok(service.listVouchers(status, page, size));
    }

    /** 查询当前用户券详情。 */
    @GetMapping("/v1/users/me/vouchers/{userVoucherId}")
    @Operation(summary = "查询用户券详情", operationId = "getMyVoucher")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<UserVoucherVO> getVoucher(@PathVariable String userVoucherId) {
        return Result.ok(service.getVoucher(IdUtils.parse(userVoucherId, "userVoucherId")));
    }
}
