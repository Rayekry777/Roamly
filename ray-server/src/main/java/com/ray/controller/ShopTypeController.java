package com.ray.controller;

import com.ray.result.Result;
import com.ray.service.ShopTypeService;
import com.ray.vo.ShopTypeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/shop-types")
@Tag(name = "商户分类")
public class ShopTypeController {
    private final ShopTypeService service;

    public ShopTypeController(ShopTypeService service) {
        this.service = service;
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "查询商户分类", operationId = "listShopTypes")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<List<ShopTypeVO>> list() {
        return Result.ok(service.listTypes());
    }
}
