package com.ray.controller;

import com.ray.enums.PostFeedSort;
import com.ray.result.CursorPageResult;
import com.ray.result.Result;
import com.ray.service.ContentSectionService;
import com.ray.service.PostService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.PostCardVO;
import com.ray.vo.SectionDetailVO;
import com.ray.vo.SectionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供官方内容分区查询。 */
@RestController
@RequestMapping("/v1/sections")
@Tag(name = "内容分区")
public class ContentSectionController {
    private final ContentSectionService sectionService;
    private final PostService postService;

    public ContentSectionController(ContentSectionService sectionService, PostService postService) {
        this.sectionService = sectionService;
        this.postService = postService;
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "查询官方分区", operationId = "listSections")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    useReturnTypeSchema = true))
    public Result<List<SectionVO>> listSections(
            @Parameter(description = "是否只查询当前用户已关注分区")
                    @RequestParam(defaultValue = "false")
                    boolean followedOnly) {
        return Result.ok(sectionService.listEnabledSections(followedOnly));
    }

    @GetMapping("/{sectionId}")
    @SecurityRequirements
    @Operation(summary = "查询分区详情", operationId = "getSection")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    useReturnTypeSchema = true))
    public Result<SectionDetailVO> getSection(
            @Parameter(description = "分区 ID", required = true) @PathVariable String sectionId) {
        return Result.ok(sectionService.getEnabledSection(IdUtils.parse(sectionId, "sectionId")));
    }

    @GetMapping("/{sectionId}/posts")
    @SecurityRequirements
    @Operation(summary = "查询分区动态", operationId = "listSectionPosts")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    useReturnTypeSchema = true))
    public Result<CursorPageResult<PostCardVO>> listSectionPosts(
            @Parameter(description = "分区 ID", required = true) @PathVariable String sectionId,
            @Parameter(description = "城市编码；漫游日常分区必填")
                    @RequestParam(required = false)
                    @Size(max = 16)
                    String cityCode,
            @Parameter(description = "排序方式")
                    @RequestParam(defaultValue = "LATEST")
                    PostFeedSort sort,
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
        return Result.ok(postService.listSectionPosts(
                IdUtils.parse(sectionId, "sectionId"), cityCode, sort, cursor, offset, size));
    }
}
