package com.ray.controller;

import com.ray.dto.CustomerServiceCreateDTO;
import com.ray.dto.CustomerServiceReplyDTO;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.CustomerServiceService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.CustomerServiceMessagePageVO;
import com.ray.vo.CustomerServiceTicketVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 消费者和商户分别访问本人主动创建的平台客服工单。 */
@RestController
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "客服工单")
public class CustomerServiceController {
    private final CustomerServiceService service;

    public CustomerServiceController(CustomerServiceService service) {
        this.service = service;
    }

    @PostMapping("/v1/users/me/customer-service/tickets")
    @Operation(summary = "创建消费者客服工单", operationId = "createCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> create(@Valid @RequestBody CustomerServiceCreateDTO request) {
        return Result.ok(service.createForConsumer(request));
    }

    @GetMapping("/v1/users/me/customer-service/tickets")
    @Operation(summary = "查询我的消费者客服工单", operationId = "listMyCustomerServiceTickets")
    public Result<PageResult<CustomerServiceTicketVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.listForConsumer(page, size));
    }

    @GetMapping("/v1/users/me/customer-service/tickets/{id}")
    @Operation(summary = "查询消费者客服工单详情", operationId = "getMyCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> get(@PathVariable String id) {
        return Result.ok(service.getForConsumer(IdUtils.parse(id, "id")));
    }

    @PostMapping("/v1/users/me/customer-service/tickets/{id}/messages")
    @Operation(summary = "回复消费者客服工单", operationId = "replyMyCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> reply(@PathVariable String id,
            @Valid @RequestBody CustomerServiceReplyDTO request) {
        return Result.ok(service.replyForConsumer(IdUtils.parse(id, "id"), request));
    }

    @GetMapping("/v1/users/me/customer-service/tickets/{id}/messages")
    @Operation(summary = "游标查询消费者客服消息", operationId = "listMyCustomerServiceMessages")
    public Result<CustomerServiceMessagePageVO> messages(
            @PathVariable String id,
            @Parameter(description = "加载此消息之前的历史消息") @RequestParam(name = "before_message_id", required = false) String before,
            @Parameter(description = "加载此消息之后的新消息") @RequestParam(name = "after_message_id", required = false) String after,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
        return Result.ok(service.messagesForConsumer(IdUtils.parse(id, "id"), nullableId(before, "before_message_id"),
                nullableId(after, "after_message_id"), limit));
    }

    @PostMapping("/v1/merchant/customer-service/tickets")
    @Operation(summary = "商户创建平台客服工单", operationId = "createMerchantCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> merchantCreate(@Valid @RequestBody CustomerServiceCreateDTO request) {
        return Result.ok(service.createForMerchant(request));
    }

    @GetMapping("/v1/merchant/customer-service/tickets")
    @Operation(summary = "查询当前商户账号的客服工单", operationId = "listMerchantCustomerServiceTickets")
    public Result<PageResult<CustomerServiceTicketVO>> merchantList(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.listForMerchant(page, size));
    }

    @GetMapping("/v1/merchant/customer-service/tickets/{id}")
    @Operation(summary = "查询商户客服工单详情", operationId = "getMerchantCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> merchantGet(@PathVariable String id) {
        return Result.ok(service.getForMerchant(IdUtils.parse(id, "id")));
    }

    @PostMapping("/v1/merchant/customer-service/tickets/{id}/messages")
    @Operation(summary = "回复商户客服工单", operationId = "replyMerchantCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> merchantReply(@PathVariable String id,
            @Valid @RequestBody CustomerServiceReplyDTO request) {
        return Result.ok(service.replyForMerchant(IdUtils.parse(id, "id"), request));
    }

    @GetMapping("/v1/merchant/customer-service/tickets/{id}/messages")
    @Operation(summary = "游标查询商户客服消息", operationId = "listMerchantCustomerServiceMessages")
    public Result<CustomerServiceMessagePageVO> merchantMessages(
            @PathVariable String id,
            @RequestParam(name = "before_message_id", required = false) String before,
            @RequestParam(name = "after_message_id", required = false) String after,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
        return Result.ok(service.messagesForMerchant(IdUtils.parse(id, "id"), nullableId(before, "before_message_id"),
                nullableId(after, "after_message_id"), limit));
    }

    private Long nullableId(String value, String field) {
        return value == null || value.isBlank() ? null : IdUtils.parse(value, field);
    }
}
