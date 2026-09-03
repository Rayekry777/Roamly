package com.ray.controller;

import com.ray.dto.ShopReviewCreateDTO;
import com.ray.dto.ShopReviewUpdateDTO;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.ShopReviewService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.ShopReviewVO;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供商户点评列表、创建、编辑和删除接口。 */
@Validated
@RestController
@RequestMapping("/v1/shops/{shopId}/reviews")
@Tag(name = "商户点评")
public class ShopReviewController {
    private final ShopReviewService reviewService;

    public ShopReviewController(ShopReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** 查询商户正常点评，支持最新和高分排序。 */
    @GetMapping
    @SecurityRequirements
    @Operation(summary = "查询商户点评", operationId = "listShopReviews")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<PageResult<ShopReviewVO>> list(
            @Parameter(description = "商户 ID", required = true) @PathVariable String shopId,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数，1 到 100") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @Parameter(description = "排序：LATEST 或 HIGHEST_SCORE") @RequestParam(defaultValue = "LATEST") String sort) {
        return Result.ok(reviewService.list(IdUtils.parse(shopId, "shopId"), page, size, sort));
    }

    /** 创建当前用户对指定商户的点评。 */
    @PostMapping
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "发布商户点评", operationId = "createShopReview")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "创建成功", useReturnTypeSchema = true))
    public ResponseEntity<Result<ShopReviewVO>> create(
            @Parameter(description = "商户 ID", required = true) @PathVariable String shopId,
            @Valid @RequestBody ShopReviewCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(reviewService.create(IdUtils.parse(shopId, "shopId"), dto)));
    }

    /** 更新当前用户对指定商户的点评。 */
    @PutMapping("/me")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "更新我的商户点评", operationId = "updateMyShopReview")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "更新成功", useReturnTypeSchema = true))
    public Result<ShopReviewVO> update(
            @Parameter(description = "商户 ID", required = true) @PathVariable String shopId,
            @Valid @RequestBody ShopReviewUpdateDTO dto) {
        return Result.ok(reviewService.update(IdUtils.parse(shopId, "shopId"), dto));
    }

    /** 删除当前用户对指定商户的点评。 */
    @DeleteMapping("/me")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "删除我的商户点评", operationId = "deleteMyShopReview")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "删除成功"))
    public ResponseEntity<Void> delete(
            @Parameter(description = "商户 ID", required = true) @PathVariable String shopId) {
        reviewService.delete(IdUtils.parse(shopId, "shopId"));
        return ResponseEntity.noContent().build();
    }
}
