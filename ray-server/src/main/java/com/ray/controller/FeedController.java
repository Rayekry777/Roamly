package com.ray.controller;

import com.ray.result.CursorPageResult;
import com.ray.result.Result;
import com.ray.service.PostService;
import com.ray.service.LocationService;
import com.ray.dto.LocationContextDTO;
import com.ray.vo.PostCardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供推荐和关注动态信息流。 */
@Validated
@RestController
@Tag(name = "动态信息流")
public class FeedController {
    private final PostService postService;
    private final LocationService locationService;

    public FeedController(PostService postService) {
        this(postService, null);
    }

    @Autowired
    public FeedController(PostService postService, LocationService locationService) {
        this.postService = postService;
        this.locationService = locationService;
    }

    @GetMapping("/v1/feeds/recommended")
    @SecurityRequirements
    @Operation(summary = "查询推荐动态", operationId = "listRecommendedFeed")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    useReturnTypeSchema = true))
    public Result<CursorPageResult<PostCardVO>> listRecommendedFeed(
            @Parameter(description = "城市编码", required = true)
            @RequestParam
                    @NotBlank
                    @Size(max = 16)
                    String cityCode,
            @Parameter(description = "上一页返回的不透明游标")
                    @RequestParam(required = false)
                    @Min(0)
                    Long cursor,
            @Parameter(description = "同排序值偏移量")
                    @RequestParam(defaultValue = "0")
                    @Min(0)
                    @Max(1000)
                    int offset,
            @Parameter(description = "每次条数，1 到 20")
                    @RequestParam(defaultValue = "10")
                    @Min(1)
                    @Max(20)
                    int size,
            @Parameter(description = "真实定位经度（GCJ-02）") @RequestParam(required = false) Double longitude,
            @Parameter(description = "真实定位纬度（GCJ-02）") @RequestParam(required = false) Double latitude) {
        String resolvedCity = cityCode;
        if (locationService != null && longitude != null && latitude != null) {
            resolvedCity = locationService.resolve(new LocationContextDTO(longitude, latitude, null)).cityCode();
        }
        return Result.ok(postService.listRecommendedFeed(resolvedCity, cursor, offset, size));
    }

    @GetMapping("/v1/feeds/following")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "查询关注动态", operationId = "listFollowingFeed")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    useReturnTypeSchema = true))
    public Result<CursorPageResult<PostCardVO>> listFollowingFeed(
            @Parameter(description = "上一页返回的不透明游标")
                    @RequestParam(required = false)
                    @Min(0)
                    Long cursor,
            @Parameter(description = "同排序值偏移量")
                    @RequestParam(defaultValue = "0")
                    @Min(0)
                    @Max(1000)
                    int offset,
            @Parameter(description = "每次条数，1 到 20")
                    @RequestParam(defaultValue = "10")
                    @Min(1)
                    @Max(20)
                    int size) {
        return Result.ok(postService.listFollowingFeed(cursor, offset, size));
    }
}
