package com.ray.controller;

import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.dto.AdminRefundDTO;
import com.ray.service.VoucherRefundService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.VoucherRefundVO;
import com.ray.vo.RefundTimelineEventVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/v1/admin/refunds")
@SecurityRequirement(name="BearerAuth")
@Tag(name="管理端退款")
public class AdminRefundController {
    private final VoucherRefundService service;
    public AdminRefundController(VoucherRefundService service){this.service=service;}
    @GetMapping @Operation(summary="查询退款列表", operationId="listAdminRefunds")
    public Result<PageResult<VoucherRefundVO>> list(@RequestParam(required=false) String status,
            @RequestParam(required=false) String queue,
            @RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        if (queue != null && !queue.isBlank()) return Result.ok(service.listForAdminQueue(queue, page, size));
        return Result.ok(service.list(status,page,size,true));
    }
    @GetMapping("/{id}") @Operation(summary="查询退款详情", operationId="getAdminRefund")
    public Result<VoucherRefundVO> get(@PathVariable String id){return Result.ok(service.get(IdUtils.parse(id,"refundId"),true));}
    @GetMapping("/{id}/timeline") @Operation(summary="查询退款时间线", operationId="getAdminRefundTimeline")
    public Result<List<RefundTimelineEventVO>> timeline(@PathVariable String id){return Result.ok(service.timeline(IdUtils.parse(id,"refundId"),true));}
    @PostMapping("/{id}/approval") @Operation(summary="通过退款", operationId="approveAdminRefund")
    public Result<VoucherRefundVO> approve(@PathVariable String id,@RequestHeader("Idempotency-Key") @Pattern(regexp="[A-Za-z0-9._:-]{8,128}") String key){return Result.ok(service.decide(IdUtils.parse(id,"refundId"),true,null,key));}
    @PostMapping("/{id}/rejection") @Operation(summary="驳回退款", operationId="rejectAdminRefund")
    public Result<VoucherRefundVO> reject(@PathVariable String id,@RequestHeader("Idempotency-Key") @Pattern(regexp="[A-Za-z0-9._:-]{8,128}") String key,@RequestParam String reason){return Result.ok(service.decide(IdUtils.parse(id,"refundId"),false,reason,key));}
    @PostMapping("/{id}/retry") @Operation(summary="重试退款", operationId="retryAdminRefund")
    public Result<VoucherRefundVO> retry(@PathVariable String id,@RequestHeader("Idempotency-Key") @Pattern(regexp="[A-Za-z0-9._:-]{8,128}") String key){return Result.ok(service.decide(IdUtils.parse(id,"refundId"),true,null,key));}
    @PostMapping
    @Operation(summary="管理员发起退款", operationId="createAdminRefund")
    public Result<VoucherRefundVO> create(@Valid @RequestBody AdminRefundDTO request,
            @RequestHeader("Idempotency-Key") @Pattern(regexp="[A-Za-z0-9._:-]{8,128}") String key) {
        return Result.ok(service.adminRequest(request, key));
    }
}
