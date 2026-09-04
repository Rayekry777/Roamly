package com.ray.controller;

import com.ray.dto.VoucherRefundRequest;
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

@RestController
@SecurityRequirement(name="BearerAuth")
@Tag(name="消费者退款")
public class VoucherRefundController {
    private final VoucherRefundService service;
    public VoucherRefundController(VoucherRefundService service) { this.service = service; }
    @PostMapping("/v1/users/me/vouchers/{voucherId}/refunds")
    @Operation(summary="申请单券退款", operationId="requestVoucherRefund")
    public Result<VoucherRefundVO> request(@PathVariable String voucherId, @Valid @RequestBody VoucherRefundRequest request,
            @RequestHeader("Idempotency-Key") @Pattern(regexp="[A-Za-z0-9._:-]{8,128}") String key) { return Result.ok(service.request(IdUtils.parse(voucherId,"voucherId"), request, key)); }
    @GetMapping("/v1/users/me/refunds")
    @Operation(summary="查询我的退款", operationId="listMyVoucherRefunds")
    public Result<PageResult<VoucherRefundVO>> list(@RequestParam(required=false) String status,@RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return Result.ok(service.list(status,page,size,false));}
    @GetMapping("/v1/users/me/refunds/{id}")
    @Operation(summary="查询退款详情", operationId="getMyVoucherRefund")
    public Result<VoucherRefundVO> get(@PathVariable String id){return Result.ok(service.get(IdUtils.parse(id,"refundId"),false));}
}
