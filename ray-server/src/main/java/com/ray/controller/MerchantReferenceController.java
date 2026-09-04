package com.ray.controller;

import com.ray.result.Result;
import com.ray.service.CityService;
import com.ray.service.ShopTypeService;
import com.ray.vo.CityVO;
import com.ray.vo.ShopTypeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/merchant/reference")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "商户端字典")
public class MerchantReferenceController {
    private final CityService cityService;
    private final ShopTypeService shopTypeService;

    public MerchantReferenceController(CityService cityService, ShopTypeService shopTypeService) {
        this.cityService = cityService;
        this.shopTypeService = shopTypeService;
    }

    @GetMapping("/cities")
    @Operation(summary = "查询商户可选城市", operationId = "listMerchantReferenceCities")
    public Result<List<CityVO>> cities() {
        return Result.ok(cityService.listEnabledCities());
    }

    @GetMapping("/shop-types")
    @Operation(summary = "查询商户可选门店类目", operationId = "listMerchantReferenceShopTypes")
    public Result<List<ShopTypeVO>> shopTypes() {
        return Result.ok(shopTypeService.listTypes());
    }
}
