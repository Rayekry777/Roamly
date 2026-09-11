package com.ray.controller;

import com.ray.enums.VoucherProductSort;
import com.ray.dto.LocationContextDTO;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.BusinessMediaService;
import com.ray.service.LocationService;
import com.ray.service.VoucherProductService;
import com.ray.exception.BusinessException;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.VoucherProductDetailVO;
import com.ray.vo.VoucherProductListItemVO;
import com.ray.vo.VoucherProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/** 团购商品公开查询接口。 */
@Validated
@RestController
@Tag(name = "团购商品")
public class VoucherProductController {
    private final VoucherProductService service;
    private final BusinessMediaService mediaService;
    private final LocationService locationService;

    /** 保留无定位服务的单元测试构造方式；生产容器使用带定位服务的构造器。 */
    public VoucherProductController(VoucherProductService service, BusinessMediaService mediaService) {
        this(service, mediaService, null);
    }

    /** 注入商品查询、媒体读取和真实定位解析能力。 */
    @Autowired
    public VoucherProductController(
            VoucherProductService service, BusinessMediaService mediaService, LocationService locationService) {
        this.service = service;
        this.mediaService = mediaService;
        this.locationService = locationService;
    }

    /** 按真实定位解析后的城市和消费者筛选条件分页查询当前可售商品。 */
    @GetMapping("/v1/voucher-products")
    @SecurityRequirements
    @Operation(summary = "查询同城团购商品", operationId = "listVoucherProducts")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<PageResult<VoucherProductListItemVO>> list(
            @RequestParam @Pattern(regexp = "^[A-Za-z0-9_-]{1,16}$") String cityCode,
            @RequestParam(required = false) String typeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "RECOMMENDED") VoucherProductSort sort,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double latitude) {
        if (page < 1 || size < 1 || size > 100) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "分页参数不合法");
        }
        String districtCode = null;
        if (locationService != null && longitude != null && latitude != null) {
            var context = locationService.resolve(new LocationContextDTO(longitude, latitude, null));
            cityCode = context.cityCode();
            districtCode = context.districtCode();
        }
        return Result.ok(service.listPublic(
                cityCode, districtCode, typeId == null ? null : IdUtils.parse(typeId, "typeId"), keyword,
                sort, page, size, longitude, latitude));
    }

    /** 查询商户在售团购商品。 */
    @GetMapping("/v1/shops/{shopId}/voucher-products")
    @SecurityRequirements
    @Operation(summary = "查询商户团购商品", operationId = "listShopVoucherProducts")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<List<VoucherProductVO>> listByShop(
            @Parameter(description = "商户 ID") @PathVariable String shopId,
            @Parameter(description = "商品状态，默认 ON_SALE") @RequestParam(required = false) String status) {
        return Result.ok(service.listByShop(IdUtils.parse(shopId, "shopId"), status));
    }

    /** 查询团购商品详情。 */
    @GetMapping("/v1/voucher-products/{productId}")
    @SecurityRequirements
    @Operation(summary = "查询团购商品详情", operationId = "getVoucherProduct")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<VoucherProductDetailVO> get(
            @Parameter(description = "团购商品 ID") @PathVariable String productId) {
        return Result.ok(service.getDetail(IdUtils.parse(productId, "productId")));
    }

    /** 公开读取审核通过商品拥有的已绑定券图片。 */
    @GetMapping("/v1/voucher-products/{productId}/media/{mediaId}/content")
    @SecurityRequirements
    @Operation(summary = "读取团购商品图片", operationId = "getVoucherProductMediaContent")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "读取成功"),
        @ApiResponse(responseCode = "404", description = "图片不存在或不可公开"),
        @ApiResponse(responseCode = "503", description = "对象存储暂不可用")
    })
    public ResponseEntity<byte[]> content(@PathVariable String productId, @PathVariable String mediaId) {
        BusinessMediaService.BusinessMediaContent content = mediaService.readPublicVoucherContent(
                IdUtils.parse(productId, "productId"), IdUtils.parse(mediaId, "mediaId"));
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(content.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(content.content());
    }
}
