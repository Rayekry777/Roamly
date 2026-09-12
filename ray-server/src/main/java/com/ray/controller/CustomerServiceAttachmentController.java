package com.ray.controller;

import com.ray.result.Result;
import com.ray.service.CustomerServiceAttachmentService;
import com.ray.service.CustomerServiceAttachmentService.Actor;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.CustomerServiceAttachmentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 三个登录域各自受保护的客服附件上传和读取入口。 */
@RestController
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "客服附件")
public class CustomerServiceAttachmentController {
    private final CustomerServiceAttachmentService service;

    public CustomerServiceAttachmentController(CustomerServiceAttachmentService service) {
        this.service = service;
    }

    @PostMapping(value = "/v1/users/me/customer-service/tickets/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "消费者上传客服附件", operationId = "uploadMyCustomerServiceAttachment")
    public Result<CustomerServiceAttachmentVO> consumerUpload(@PathVariable String ticketId, @RequestParam("file") MultipartFile file) {
        return Result.ok(service.upload(IdUtils.parse(ticketId, "ticketId"), file, Actor.CONSUMER));
    }

    @GetMapping("/v1/users/me/customer-service/tickets/{ticketId}/attachments/{attachmentId}/content")
    @Operation(summary = "消费者读取客服附件", operationId = "getMyCustomerServiceAttachmentContent")
    public ResponseEntity<byte[]> consumerContent(@PathVariable String ticketId, @PathVariable String attachmentId) {
        return content(service.read(IdUtils.parse(ticketId, "ticketId"), IdUtils.parse(attachmentId, "attachmentId"), Actor.CONSUMER));
    }

    @DeleteMapping("/v1/users/me/customer-service/tickets/{ticketId}/attachments/{attachmentId}")
    @Operation(summary = "消费者删除临时客服附件", operationId = "deleteMyCustomerServiceAttachment")
    public ResponseEntity<Void> consumerDelete(@PathVariable String ticketId, @PathVariable String attachmentId) {
        service.deleteTemporary(IdUtils.parse(ticketId, "ticketId"), IdUtils.parse(attachmentId, "attachmentId"), Actor.CONSUMER);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/v1/merchant/customer-service/tickets/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "商户上传客服附件", operationId = "uploadMerchantCustomerServiceAttachment")
    public Result<CustomerServiceAttachmentVO> merchantUpload(@PathVariable String ticketId, @RequestParam("file") MultipartFile file) {
        return Result.ok(service.upload(IdUtils.parse(ticketId, "ticketId"), file, Actor.MERCHANT));
    }

    @GetMapping("/v1/merchant/customer-service/tickets/{ticketId}/attachments/{attachmentId}/content")
    @Operation(summary = "商户读取客服附件", operationId = "getMerchantCustomerServiceAttachmentContent")
    public ResponseEntity<byte[]> merchantContent(@PathVariable String ticketId, @PathVariable String attachmentId) {
        return content(service.read(IdUtils.parse(ticketId, "ticketId"), IdUtils.parse(attachmentId, "attachmentId"), Actor.MERCHANT));
    }

    @DeleteMapping("/v1/merchant/customer-service/tickets/{ticketId}/attachments/{attachmentId}")
    @Operation(summary = "商户删除临时客服附件", operationId = "deleteMerchantCustomerServiceAttachment")
    public ResponseEntity<Void> merchantDelete(@PathVariable String ticketId, @PathVariable String attachmentId) {
        service.deleteTemporary(IdUtils.parse(ticketId, "ticketId"), IdUtils.parse(attachmentId, "attachmentId"), Actor.MERCHANT);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/v1/admin/customer-service/tickets/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "客服上传工单附件", operationId = "uploadAdminCustomerServiceAttachment")
    public Result<CustomerServiceAttachmentVO> adminUpload(@PathVariable String ticketId, @RequestParam("file") MultipartFile file) {
        return Result.ok(service.upload(IdUtils.parse(ticketId, "ticketId"), file, Actor.ADMIN));
    }

    @GetMapping("/v1/admin/customer-service/tickets/{ticketId}/attachments/{attachmentId}/content")
    @Operation(summary = "客服读取工单附件", operationId = "getAdminCustomerServiceAttachmentContent")
    public ResponseEntity<byte[]> adminContent(@PathVariable String ticketId, @PathVariable String attachmentId) {
        return content(service.read(IdUtils.parse(ticketId, "ticketId"), IdUtils.parse(attachmentId, "attachmentId"), Actor.ADMIN));
    }

    @DeleteMapping("/v1/admin/customer-service/tickets/{ticketId}/attachments/{attachmentId}")
    @Operation(summary = "客服删除临时附件", operationId = "deleteAdminCustomerServiceAttachment")
    public ResponseEntity<Void> adminDelete(@PathVariable String ticketId, @PathVariable String attachmentId) {
        service.deleteTemporary(IdUtils.parse(ticketId, "ticketId"), IdUtils.parse(attachmentId, "attachmentId"), Actor.ADMIN);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<byte[]> content(CustomerServiceAttachmentService.AttachmentContent content) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.mimeType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(content.filename(), StandardCharsets.UTF_8).build().toString())
                .body(content.content());
    }
}
