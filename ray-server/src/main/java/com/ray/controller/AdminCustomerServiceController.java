package com.ray.controller;

import com.ray.dto.CustomerServiceReplyDTO;
import com.ray.dto.CustomerServiceStatusDTO;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.CustomerServiceService;
import com.ray.vo.CustomerServiceTicketVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

/** 管理端客服公共队列、认领、回复和状态处理。 */
@RestController
@RequestMapping("/v1/admin/customer-service/tickets")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "管理端客服")
public class AdminCustomerServiceController {
    private final CustomerServiceService service;
    public AdminCustomerServiceController(CustomerServiceService service) { this.service = service; }
    @GetMapping
    @Operation(summary = "查询客服队列", operationId = "listAdminCustomerServiceTickets")
    public Result<PageResult<CustomerServiceTicketVO>> list(@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") @Min(1) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return Result.ok(service.listForAdmin(status, page, size)); }
    @GetMapping("/{id}")
    @Operation(summary = "查询客服工单", operationId = "getAdminCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> get(@PathVariable Long id) { return Result.ok(service.getForAdmin(id)); }
    @PostMapping("/{id}/claim")
    @Operation(summary = "认领客服工单", operationId = "claimCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> claim(@PathVariable Long id) { return Result.ok(service.claim(id)); }
    @PostMapping("/{id}/messages")
    @Operation(summary = "公开回复客服工单", operationId = "replyCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> reply(@PathVariable Long id, @Valid @RequestBody CustomerServiceReplyDTO request) { return Result.ok(service.replyForAdmin(id, request, false)); }
    @PostMapping("/{id}/internal-notes")
    @Operation(summary = "添加客服内部备注", operationId = "addCustomerServiceInternalNote")
    public Result<CustomerServiceTicketVO> note(@PathVariable Long id, @Valid @RequestBody CustomerServiceReplyDTO request) { return Result.ok(service.replyForAdmin(id, request, true)); }
    @PutMapping("/{id}/status")
    @Operation(summary = "更新客服工单状态", operationId = "updateCustomerServiceTicketStatus")
    public Result<CustomerServiceTicketVO> status(@PathVariable Long id, @Valid @RequestBody CustomerServiceStatusDTO request) { return Result.ok(service.updateStatus(id, request)); }
}
