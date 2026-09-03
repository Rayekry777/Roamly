package com.ray.controller;

import com.ray.dto.CreateShopDTO;
import com.ray.dto.UpdateShopDTO;
import com.ray.enums.ShopSort;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.ShopService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.IdVO;
import com.ray.vo.ShopVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/shops")
@Tag(name = "商户")
public class ShopController {
    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping("/{shopId}")
    @SecurityRequirements
    @Operation(summary = "查询商户详情", operationId = "getShop")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<ShopVO> get(@Parameter(description = "商户 ID") @PathVariable String shopId) {
        return Result.ok(shopService.getShop(IdUtils.parse(shopId, "shopId")));
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "筛选商户", operationId = "listShops")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<PageResult<ShopVO>> list(
            @Parameter(description = "城市编码", example = "330100") @RequestParam @NotBlank
                    @Pattern(regexp = "^[A-Za-z0-9_-]{1,16}$") String cityCode,
            @Parameter(description = "商户分类 ID") @RequestParam(required = false) String typeId,
            @Parameter(description = "商户名称关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "排序：DISTANCE、SCORE、POPULAR", example = "POPULAR")
                    @RequestParam(defaultValue = "POPULAR") ShopSort sort,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数，1 到 100") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @Parameter(description = "经度，需与 latitude 同时提供") @RequestParam(required = false) Double longitude,
            @Parameter(description = "纬度，需与 longitude 同时提供") @RequestParam(required = false) Double latitude) {
        return Result.ok(shopService.listShops(
                cityCode,
                typeId == null ? null : IdUtils.parse(typeId, "typeId"),
                keyword,
                sort.name(),
                page,
                size,
                longitude,
                latitude));
    }

    @PostMapping
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "新增商户", operationId = "createShop")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "创建成功", useReturnTypeSchema = true))
    public ResponseEntity<Result<IdVO>> create(@Valid @RequestBody CreateShopDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(new IdVO(IdUtils.format(shopService.createShop(request)))));
    }

    @PutMapping("/{shopId}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "更新商户", operationId = "updateShop")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "更新成功", useReturnTypeSchema = true))
    public Result<Void> update(
            @Parameter(description = "商户 ID") @PathVariable String shopId,
            @Valid @RequestBody UpdateShopDTO request) {
        shopService.updateShop(IdUtils.parse(shopId, "shopId"), request);
        return Result.ok(null);
    }
}
