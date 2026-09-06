package com.ray.controller;

import com.ray.dto.MerchantVoucherProductCreateDTO;
import com.ray.dto.MerchantVoucherProductOffSaleDTO;
import com.ray.dto.MerchantVoucherProductSubmitDTO;
import com.ray.dto.MerchantVoucherProductUpdateDTO;
import com.ray.result.ErrorResult;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.MerchantVoucherProductService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.MerchantVoucherProductVO;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 商户四类团购券草稿、复制、删除和提交接口。 */
@Validated
@RestController
@RequestMapping("/v1/merchant/voucher-products")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "商户团购券")
public class MerchantVoucherProductController {
    private final MerchantVoucherProductService service;

    public MerchantVoucherProductController(MerchantVoucherProductService service) {
        this.service = service;
    }

    /** 分页查询当前门店团购券。 */
    @GetMapping
    @Operation(summary = "查询商户团购券", operationId = "listMerchantVoucherProducts")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "筛选或分页无效", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "401", description = "未登录", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "403", description = "账号或角色无权访问", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "500", description = "服务内部错误", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<PageResult<MerchantVoucherProductVO>> list(
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.list(reviewStatus, productType, keyword, page, size));
    }

    /** 创建券型不可变的空草稿。 */
    @PostMapping
    @Operation(summary = "创建团购券草稿", operationId = "createMerchantVoucherProduct")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "创建成功", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "券型无效", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "401", description = "未登录", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "403", description = "账号或角色无权访问", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "500", description = "服务内部错误", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public ResponseEntity<Result<MerchantVoucherProductVO>> create(
            @Valid @RequestBody MerchantVoucherProductCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.ok(service.create(request)));
    }

    /** 查询当前门店团购券完整事实。 */
    @GetMapping("/{productId}")
    @Operation(summary = "查询商户团购券详情", operationId = "getMerchantVoucherProduct")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(schema = @Schema(implementation = MerchantVoucherProductVO.class))),
        @ApiResponse(responseCode = "401", description = "未登录", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "403", description = "账号或角色无权访问", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "404", description = "商品不存在或不属于当前门店", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "500", description = "服务内部错误", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<MerchantVoucherProductVO> get(@PathVariable String productId) {
        return Result.ok(service.get(IdUtils.parse(productId, "productId")));
    }

    /** 按版本保存完整草稿快照。 */
    @PutMapping("/{productId}")
    @Operation(summary = "保存团购券草稿", operationId = "updateMerchantVoucherProduct")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "保存成功", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "草稿字段或券型规则无效", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "401", description = "未登录", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "403", description = "账号或角色无权访问", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "404", description = "商品不存在或不属于当前门店", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "409", description = "版本或状态冲突", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "503", description = "对象存储不可用", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<MerchantVoucherProductVO> update(
            @PathVariable String productId,
            @Valid @RequestBody MerchantVoucherProductUpdateDTO request) {
        return Result.ok(service.update(IdUtils.parse(productId, "productId"), request));
    }

    /** 删除从未提交且没有订单的草稿。 */
    @DeleteMapping("/{productId}")
    @Operation(summary = "删除团购券草稿", operationId = "deleteMerchantVoucherProduct")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "删除成功"),
        @ApiResponse(responseCode = "401", description = "未登录", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "403", description = "账号或角色无权访问", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "404", description = "商品不存在或不属于当前门店", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "409", description = "状态冲突或商品已有订单", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable String productId) {
        service.delete(IdUtils.parse(productId, "productId"));
        return ResponseEntity.noContent().build();
    }

    /** 复制为拥有独立媒体对象的新草稿。 */
    @PostMapping("/{productId}/copies")
    @Operation(summary = "复制团购券草稿", operationId = "copyMerchantVoucherProduct")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "复制成功", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "未登录", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "403", description = "账号或角色无权访问", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "404", description = "商品不存在或不属于当前门店", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "503", description = "对象存储不可用", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public ResponseEntity<Result<MerchantVoucherProductVO>> copy(@PathVariable String productId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(service.copy(IdUtils.parse(productId, "productId"))));
    }

    /** 校验完整性并幂等提交平台审核。 */
    @PostMapping("/{productId}/submission")
    @Operation(summary = "提交团购券审核", operationId = "submitMerchantVoucherProduct")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "提交成功或同键重放", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "团购券资料不完整", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "401", description = "未登录", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "403", description = "账号或角色无权访问", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "404", description = "商品不存在或不属于当前门店", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "409", description = "版本、状态或幂等冲突", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "500", description = "服务内部错误", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<MerchantVoucherProductVO> submit(
            @PathVariable String productId,
            @Parameter(description = "8至128位提交幂等键", required = true)
                    @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}", message = "Idempotency-Key 格式无效")
                    String idempotencyKey,
            @Valid @RequestBody MerchantVoucherProductSubmitDTO request) {
        return Result.ok(service.submit(IdUtils.parse(productId, "productId"), idempotencyKey, request));
    }

    /** 下架已审核商品，修改规则前必须重新审核。 */
    @PostMapping("/{productId}/off-sale")
    @Operation(summary = "下架团购券", operationId = "offSaleMerchantVoucherProduct")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "下架成功或同键重放", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "请求参数无效", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "401", description = "未登录", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "403", description = "账号或角色无权操作", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "404", description = "商品不存在", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "409", description = "版本、状态或幂等冲突", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public Result<MerchantVoucherProductVO> offSale(
            @PathVariable String productId,
            @Parameter(description = "8至128位下架幂等键", required = true)
                    @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}", message = "Idempotency-Key 格式无效")
                    String idempotencyKey,
            @Valid @RequestBody MerchantVoucherProductOffSaleDTO request) {
        return Result.ok(service.offSale(IdUtils.parse(productId, "productId"), idempotencyKey, request));
    }
}
