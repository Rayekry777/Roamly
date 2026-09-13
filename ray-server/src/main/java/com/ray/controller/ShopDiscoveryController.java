package com.ray.controller;
import com.ray.service.ShopDiscoveryService;
import com.ray.result.*;
import com.ray.vo.ShopDiscoveryVO;
import com.ray.utils.converter.IdUtils;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
/** 店铺发现公开接口。 */
@RestController @Validated @RequiredArgsConstructor @Slf4j @Tag(name="店铺发现")
public class ShopDiscoveryController {
    private final ShopDiscoveryService service;
    @GetMapping("/v1/shops/discovery") @SecurityRequirements
    @Operation(summary="按分类发现店铺与两张可售券",operationId="discoverShops")
    @ApiResponses({@ApiResponse(responseCode="200",description="查询成功",useReturnTypeSchema=true),
        @ApiResponse(responseCode="400",description="分类、分页或定位参数无效"),
        @ApiResponse(responseCode="404",description="城市未开放"),@ApiResponse(responseCode="500",description="服务异常")})
    public Result<PageResult<ShopDiscoveryVO>> discover(
        @RequestParam @NotBlank String cityCode,
        @Parameter(description="一级分类 ID：1 美食，2 休闲娱乐") @RequestParam String categoryId,
        @Parameter(description="可选二级分类 ID") @RequestParam(required=false) String typeId,
        @RequestParam(required=false) @Size(max=100) String keyword,
        @RequestParam(defaultValue="RECOMMENDED") String sort,
        @RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,
        @RequestParam(defaultValue="10") @Min(1) @Max(100) int size,
        @RequestParam(required=false) Double longitude, @RequestParam(required=false) Double latitude) {
        log.debug("[店铺发现] 查询分类，categoryId={}, page={}",categoryId,page);
        return Result.ok(service.discover(cityCode,IdUtils.parse(categoryId,"categoryId"),
                typeId == null ? null : IdUtils.parse(typeId,"typeId"),keyword,sort,page,size,longitude,latitude));
    }
}
