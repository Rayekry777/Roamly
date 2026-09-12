package com.ray.controller;

import com.ray.dto.CustomerServiceQuickReplyDTO;
import com.ray.dto.CustomerServiceReplyDTO;
import com.ray.dto.CustomerServiceStatusDTO;
import com.ray.dto.CustomerServiceTagUpdateDTO;
import com.ray.dto.CustomerServiceTransferDTO;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.CustomerServiceService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.CustomerServiceMessagePageVO;
import com.ray.vo.CustomerServiceQuickReplyVO;
import com.ray.vo.CustomerServiceTagVO;
import com.ray.vo.CustomerServiceTicketVO;
import com.ray.vo.CustomerServiceTransferVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端客服队列、认领、回复、转交、标签和快捷回复入口。 */
@RestController
@RequestMapping("/v1/admin/customer-service")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "管理端客服")
public class AdminCustomerServiceController {
    private final CustomerServiceService service;

    public AdminCustomerServiceController(CustomerServiceService service) {
        this.service = service;
    }

    @GetMapping("/tickets")
    @Operation(summary = "查询客服队列", operationId = "listAdminCustomerServiceTickets")
    public Result<PageResult<CustomerServiceTicketVO>> list(
            @RequestParam(required = false) String queue,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String applicantType,
            @RequestParam(required = false) String tagId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.listForAdmin(queue, status, applicantType, nullableId(tagId, "tagId"), page, size));
    }

    @GetMapping("/tickets/{id}")
    @Operation(summary = "查询客服工单", operationId = "getAdminCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> get(@PathVariable String id) {
        return Result.ok(service.getForAdmin(IdUtils.parse(id, "id")));
    }

    @GetMapping("/tickets/{id}/messages")
    @Operation(summary = "游标查询客服消息", operationId = "listAdminCustomerServiceMessages")
    public Result<CustomerServiceMessagePageVO> messages(
            @PathVariable String id,
            @RequestParam(name = "before_message_id", required = false) String before,
            @RequestParam(name = "after_message_id", required = false) String after,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
        return Result.ok(service.messagesForAdmin(IdUtils.parse(id, "id"), nullableId(before, "before_message_id"),
                nullableId(after, "after_message_id"), limit));
    }

    @PostMapping("/tickets/{id}/claim")
    @Operation(summary = "原子认领客服工单", operationId = "claimCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> claim(@PathVariable String id) {
        return Result.ok(service.claim(IdUtils.parse(id, "id")));
    }

    @PostMapping("/tickets/{id}/messages")
    @Operation(summary = "公开回复客服工单", operationId = "replyCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> reply(@PathVariable String id,
            @Valid @RequestBody CustomerServiceReplyDTO request) {
        return Result.ok(service.replyForAdmin(IdUtils.parse(id, "id"), request, false));
    }

    @PostMapping("/tickets/{id}/internal-notes")
    @Operation(summary = "添加不改变主状态的内部备注", operationId = "addCustomerServiceInternalNote")
    public Result<CustomerServiceTicketVO> note(@PathVariable String id,
            @Valid @RequestBody CustomerServiceReplyDTO request) {
        return Result.ok(service.replyForAdmin(IdUtils.parse(id, "id"), request, true));
    }

    @PutMapping("/tickets/{id}/status")
    @Operation(summary = "按白名单更新客服工单状态", operationId = "updateCustomerServiceTicketStatus")
    public Result<CustomerServiceTicketVO> status(@PathVariable String id,
            @Valid @RequestBody CustomerServiceStatusDTO request) {
        return Result.ok(service.updateStatus(IdUtils.parse(id, "id"), request));
    }

    @PostMapping("/tickets/{id}/transfer")
    @Operation(summary = "转交客服工单", operationId = "transferCustomerServiceTicket")
    public Result<CustomerServiceTicketVO> transfer(@PathVariable String id,
            @Valid @RequestBody CustomerServiceTransferDTO request) {
        return Result.ok(service.transfer(IdUtils.parse(id, "id"), request));
    }

    @GetMapping("/tickets/{id}/transfers")
    @Operation(summary = "查询客服转交记录", operationId = "listCustomerServiceTransfers")
    public Result<List<CustomerServiceTransferVO>> transfers(@PathVariable String id) {
        return Result.ok(service.transfers(IdUtils.parse(id, "id")));
    }

    @GetMapping("/tags")
    @Operation(summary = "查询客服标签", operationId = "listCustomerServiceTags")
    public Result<List<CustomerServiceTagVO>> tags() {
        return Result.ok(service.tags());
    }

    @PutMapping("/tickets/{id}/tags")
    @Operation(summary = "替换工单标签", operationId = "replaceCustomerServiceTicketTags")
    public Result<CustomerServiceTicketVO> tags(@PathVariable String id,
            @Valid @RequestBody CustomerServiceTagUpdateDTO request) {
        return Result.ok(service.replaceTags(IdUtils.parse(id, "id"), request));
    }

    @GetMapping("/quick-replies")
    @Operation(summary = "查询快捷回复", operationId = "listCustomerServiceQuickReplies")
    public Result<List<CustomerServiceQuickReplyVO>> quickReplies() {
        return Result.ok(service.quickReplies());
    }

    @PostMapping("/quick-replies")
    @Operation(summary = "创建快捷回复", operationId = "createCustomerServiceQuickReply")
    public Result<CustomerServiceQuickReplyVO> createQuickReply(
            @Valid @RequestBody CustomerServiceQuickReplyDTO request) {
        return Result.ok(service.createQuickReply(request));
    }

    @DeleteMapping("/quick-replies/{id}")
    @Operation(summary = "删除快捷回复", operationId = "deleteCustomerServiceQuickReply")
    public ResponseEntity<Void> deleteQuickReply(@PathVariable String id) {
        service.deleteQuickReply(IdUtils.parse(id, "id"));
        return ResponseEntity.noContent().build();
    }

    private Long nullableId(String value, String field) {
        return value == null || value.isBlank() ? null : IdUtils.parse(value, field);
    }
}
