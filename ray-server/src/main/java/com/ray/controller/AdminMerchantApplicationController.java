package com.ray.controller;

import com.ray.dto.MerchantApplicationApprovalRequest;
import com.ray.dto.MerchantApplicationRejectionRequest;
import com.ray.enums.MerchantApplicationStatus;
import com.ray.result.ErrorResult;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.AdminMerchantGovernanceService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.MerchantApplicationReviewDetailVO;
import com.ray.vo.MerchantApplicationReviewListItemVO;
import com.ray.vo.MerchantApplicationReviewResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/admin/merchant-applications")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "管理端商户申请审核")
public class AdminMerchantApplicationController {
    private final AdminMerchantGovernanceService service;

    public AdminMerchantApplicationController(AdminMerchantGovernanceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "分页查询商户申请", operationId = "listAdminMerchantApplications")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "无审核权限", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<PageResult<MerchantApplicationReviewListItemVO>> list(
            @RequestParam(required = false) MerchantApplicationStatus status,
            @RequestParam(required = false) String cityCode,
            @Parameter(description = "字符串门店类目 ID") @RequestParam(required = false) String shopTypeId,
            @Parameter(description = "精确联系人手机号") @RequestParam(required = false) String phone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime submittedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime submittedTo,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.listApplications(
                status,
                cityCode,
                parseOptionalId(shopTypeId, "shopTypeId"),
                phone,
                submittedFrom,
                submittedTo,
                page,
                size));
    }

    @GetMapping("/{applicationId}")
    @Operation(summary = "查询商户申请审核详情", operationId = "getAdminMerchantApplication")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "无审核权限", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "404", description = "申请不存在", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<MerchantApplicationReviewDetailVO> get(
            @Parameter(description = "字符串商户申请 ID") @PathVariable String applicationId) {
        return Result.ok(service.getApplication(applicationId));
    }

    @GetMapping("/{applicationId}/media/{mediaId}/content")
    @Operation(summary = "读取申请私有经营图片", operationId = "getAdminMerchantApplicationMediaContent")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "读取成功", content = @Content(mediaType = "image/*")),
        @ApiResponse(responseCode = "403", description = "无审核权限", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "404", description = "申请或媒体不存在", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "503", description = "对象存储不可用", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public ResponseEntity<byte[]> content(
            @PathVariable String applicationId, @PathVariable String mediaId) {
        AdminMerchantGovernanceService.AdminMediaContent content =
                service.readApplicationMedia(applicationId, mediaId);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(content.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .contentLength(content.content().length)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(content.content());
    }

    @PostMapping("/{applicationId}/approval")
    @Operation(summary = "审核通过商户申请", operationId = "approveAdminMerchantApplication")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "审核通过", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "无审核权限", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "404", description = "申请不存在", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "409", description = "状态、版本或幂等冲突", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<MerchantApplicationReviewResultVO> approve(
            @PathVariable String applicationId,
            @Parameter(description = "8至128位审核幂等键", required = true)
                    @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}", message = "Idempotency-Key 格式无效")
                    String idempotencyKey,
            @Valid @RequestBody MerchantApplicationApprovalRequest request) {
        return Result.ok(service.approve(applicationId, idempotencyKey, request));
    }

    @PostMapping("/{applicationId}/rejection")
    @Operation(summary = "驳回商户申请", operationId = "rejectAdminMerchantApplication")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "审核驳回", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "无审核权限", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "404", description = "申请不存在", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "409", description = "状态、版本或幂等冲突", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<MerchantApplicationReviewResultVO> reject(
            @PathVariable String applicationId,
            @Parameter(description = "8至128位审核幂等键", required = true)
                    @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}", message = "Idempotency-Key 格式无效")
                    String idempotencyKey,
            @Valid @RequestBody MerchantApplicationRejectionRequest request) {
        return Result.ok(service.reject(applicationId, idempotencyKey, request));
    }

    private Long parseOptionalId(String value, String field) {
        return StringUtils.hasText(value) ? IdUtils.parse(value, field) : null;
    }
}
