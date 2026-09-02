package com.ray.controller;

import com.ray.result.Result;
import com.ray.service.CityService;
import com.ray.vo.CityVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供客户端可用城市。 */
@RestController
@RequestMapping("/v1/cities")
@Tag(name = "城市")
public class CityController {
    private final CityService cityService;

    public CityController(CityService cityService) {
        this.cityService = cityService;
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "查询可用城市", operationId = "listCities")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    useReturnTypeSchema = true))
    public Result<List<CityVO>> listCities() {
        return Result.ok(cityService.listEnabledCities());
    }
}
