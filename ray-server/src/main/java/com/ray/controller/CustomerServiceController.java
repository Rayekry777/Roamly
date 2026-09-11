package com.ray.controller;

import com.ray.dto.CustomerServiceCreateDTO;
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

/** 消费者和商户客服入口。 */
@RestController
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "客服工单")
public class CustomerServiceController {
    private final CustomerServiceService service;
    public CustomerServiceController(CustomerServiceService service) { this.service = service; }

    @PostMapping("/v1/users/me/customer-service/tickets")
    @Operation(summary = "创建客服工单", operationId = "createCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> create(@Valid @RequestBody CustomerServiceCreateDTO request) { return Result.ok(service.createForConsumer(request)); }
    @GetMapping("/v1/users/me/customer-service/tickets")
    @Operation(summary = "查询我的客服工单", operationId = "listMyCustomerServiceTickets")
    public Result<PageResult<CustomerServiceTicketVO>> list(@RequestParam(defaultValue = "1") @Min(1) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return Result.ok(service.listForConsumer(page, size)); }
    @GetMapping("/v1/users/me/customer-service/tickets/{id}")
    @Operation(summary = "查询客服工单详情", operationId = "getMyCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> get(@PathVariable Long id) { return Result.ok(service.getForConsumer(id)); }
    @PostMapping("/v1/users/me/customer-service/tickets/{id}/messages")
    @Operation(summary = "回复客服工单", operationId = "replyMyCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> reply(@PathVariable Long id, @Valid @RequestBody CustomerServiceReplyDTO request) { return Result.ok(service.replyForConsumer(id, request)); }

    @PostMapping("/v1/merchant/customer-service/tickets")
    @Operation(summary = "商户创建客服工单", operationId = "createMerchantCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> merchantCreate(@Valid @RequestBody CustomerServiceCreateDTO request) { return Result.ok(service.createForMerchant(request)); }
    @GetMapping("/v1/merchant/customer-service/tickets")
    @Operation(summary = "查询门店客服工单", operationId = "listMerchantCustomerServiceTickets")
    public Result<PageResult<CustomerServiceTicketVO>> merchantList(@RequestParam(defaultValue = "1") @Min(1) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return Result.ok(service.listForMerchant(page, size)); }
}
