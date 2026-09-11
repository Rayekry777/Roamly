package com.ray.controller;

import com.ray.dto.LocationContextDTO;
import com.ray.result.Result;
import com.ray.service.LocationService;
import com.ray.vo.LocationContextVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 消费者真实定位解析接口。 */
@RestController
@Tag(name = "真实定位")
public class LocationController {
    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PostMapping("/v1/location/context")
    @SecurityRequirements
    @Operation(summary = "解析真实定位", operationId = "resolveLocationContext")
    public Result<LocationContextVO> resolve(@Valid @RequestBody LocationContextDTO request) {
        return Result.ok(locationService.resolve(request));
    }
}
