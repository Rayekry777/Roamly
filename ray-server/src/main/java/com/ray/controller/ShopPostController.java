package com.ray.controller;

import com.ray.result.CursorPageResult;
import com.ray.result.Result;
import com.ray.service.PostService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.PostCardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供商户关联探店动态读取接口。 */
@Validated
@RestController
@Tag(name = "商户关联动态")
public class ShopPostController {
    private final PostService postService;

    public ShopPostController(PostService postService) { this.postService = postService; }

    /** 读取商户关联的探店动态时间线。 */
    @GetMapping("/v1/shops/{shopId}/posts")
    @SecurityRequirements
    @Operation(summary = "查询商户探店动态", operationId = "listShopPosts")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<CursorPageResult<PostCardVO>> list(
            @Parameter(description = "商户 ID", required = true) @PathVariable String shopId,
            @Parameter(description = "不透明时间游标；首次不传") @RequestParam(required = false) Long cursor,
            @Parameter(description = "同一时间游标内偏移量，首次为 0") @RequestParam(defaultValue = "0") @Min(0) int offset,
            @Parameter(description = "每页条数，1 到 20") @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size) {
        return Result.ok(postService.listShopPosts(IdUtils.parse(shopId, "shopId"), cursor, offset, size));
    }
}
