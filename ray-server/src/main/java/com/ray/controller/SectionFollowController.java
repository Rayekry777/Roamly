package com.ray.controller;

import com.ray.service.ContentSectionService;
import com.ray.utils.converter.IdUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理当前用户对官方内容分区的关注关系。 */
@RestController
@RequestMapping("/v1/users/me/section-follows")
@Tag(name = "分区关注")
@SecurityRequirement(name = "BearerAuth")
public class SectionFollowController {
    private final ContentSectionService sectionService;

    public SectionFollowController(ContentSectionService sectionService) {
        this.sectionService = sectionService;
    }

    @PutMapping("/{sectionId}")
    @Operation(summary = "关注官方分区", operationId = "followSection")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "204",
                    description = "关注成功"))
    public ResponseEntity<Void> followSection(
            @Parameter(description = "分区 ID", required = true) @PathVariable String sectionId) {
        sectionService.follow(IdUtils.parse(sectionId, "sectionId"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{sectionId}")
    @Operation(summary = "取消关注官方分区", operationId = "unfollowSection")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "204",
                    description = "取消成功"))
    public ResponseEntity<Void> unfollowSection(
            @Parameter(description = "分区 ID", required = true) @PathVariable String sectionId) {
        sectionService.unfollow(IdUtils.parse(sectionId, "sectionId"));
        return ResponseEntity.noContent().build();
    }
}
