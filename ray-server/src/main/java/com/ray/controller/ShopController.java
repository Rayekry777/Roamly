package com.ray.controller;

import com.ray.enums.ShopSort;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.ShopService;
import com.ray.service.LocationService;
import com.ray.dto.LocationContextDTO;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.ShopVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/shops")
@Tag(name = "商户")
public class ShopController {
    private final ShopService shopService;
    private final LocationService locationService;

    public ShopController(ShopService shopService) {
        this(shopService, null);
    }

    @Autowired
    public ShopController(ShopService shopService, LocationService locationService) {
        this.shopService = shopService;
        this.locationService = locationService;
    }

    @GetMapping("/{shopId}")
    @SecurityRequirements
    @Operation(summary = "查询商户详情", operationId = "getShop")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<ShopVO> get(
            @Parameter(description = "商户 ID") @PathVariable String shopId,
            @Parameter(description = "经度，需与 latitude 同时提供") @RequestParam(required = false) Double longitude,
            @Parameter(description = "纬度，需与 longitude 同时提供") @RequestParam(required = false) Double latitude) {
        return Result.ok(shopService.getShop(IdUtils.parse(shopId, "shopId"), longitude, latitude));
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
            @Parameter(description = "团购商品 ID，仅返回该商品真实适用门店") @RequestParam(required = false) String productId,
            @Parameter(description = "经度，需与 latitude 同时提供") @RequestParam(required = false) Double longitude,
            @Parameter(description = "纬度，需与 longitude 同时提供") @RequestParam(required = false) Double latitude) {
        if (locationService != null && longitude != null && latitude != null) {
            cityCode = locationService.resolve(new LocationContextDTO(longitude, latitude, null)).cityCode();
        }
        Long parsedTypeId = typeId == null ? null : IdUtils.parse(typeId, "typeId");
        if (productId != null && !productId.isBlank()) {
            return Result.ok(shopService.listShops(
                    cityCode, parsedTypeId, keyword, sort.name(), page, size,
                    IdUtils.parse(productId, "productId"), longitude, latitude));
        }
        return Result.ok(shopService.listShops(
                cityCode,
                parsedTypeId,
                keyword,
                sort.name(),
                page,
                size,
                longitude,
                latitude));
    }
}
