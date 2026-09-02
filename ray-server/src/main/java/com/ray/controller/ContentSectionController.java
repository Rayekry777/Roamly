package com.ray.controller;

import com.ray.result.Result;
import com.ray.service.ContentSectionService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.SectionDetailVO;
import com.ray.vo.SectionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public ContentSectionController(ContentSectionService sectionService) {
        this.sectionService = sectionService;
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
}
