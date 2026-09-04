package com.ray.controller;

import com.ray.dto.MerchantApplicationSaveDTO;
import com.ray.result.ErrorResult;
import com.ray.result.Result;
import com.ray.service.MerchantApplicationService;
import com.ray.vo.MerchantApplicationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/merchant/application")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "商户入驻")
public class MerchantApplicationController {
    private final MerchantApplicationService service;

    public MerchantApplicationController(MerchantApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "查询当前商户入驻申请", operationId = "getMerchantApplication")
    public Result<MerchantApplicationVO> current() {
        return Result.ok(service.current());
    }

    @PutMapping
    @Operation(summary = "保存商户入驻草稿", operationId = "saveMerchantApplicationDraft")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "草稿已保存", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "409", description = "版本或状态冲突", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<MerchantApplicationVO> save(@Valid @RequestBody MerchantApplicationSaveDTO request) {
        return Result.ok(service.saveDraft(request));
    }

    @PostMapping("/submission")
    @Operation(summary = "提交商户入驻申请", operationId = "submitMerchantApplication")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "申请已提交", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "409", description = "状态或幂等冲突", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<MerchantApplicationVO> submit(
            @Parameter(description = "8至128位提交幂等键", required = true)
                    @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}", message = "Idempotency-Key 格式无效")
                    String idempotencyKey) {
        return Result.ok(service.submit(idempotencyKey));
    }
}
