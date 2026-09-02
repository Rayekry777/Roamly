package com.ray.controller;

import com.ray.result.Result;
import com.ray.service.MediaAssetService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.MediaAssetVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 管理统一发布流程使用的临时媒体。 */
@RestController
@RequestMapping("/v1/media/images")
@Tag(name = "媒体资产")
@SecurityRequirement(name = "BearerAuth")
public class MediaAssetController {
    private final MediaAssetService mediaAssetService;

    public MediaAssetController(MediaAssetService mediaAssetService) {
        this.mediaAssetService = mediaAssetService;
    }

    @PostMapping
    @Operation(summary = "上传临时图片", operationId = "uploadImage")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "201",
                    description = "上传成功",
                    useReturnTypeSchema = true))
    public ResponseEntity<Result<MediaAssetVO>> uploadImage(
            @Parameter(description = "JPEG、PNG 或 WebP 图片，最大 10MB", required = true)
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.ok(mediaAssetService.uploadImage(file)));
    }

    @DeleteMapping("/{mediaId}")
    @Operation(summary = "删除临时图片", operationId = "deleteTemporaryImage")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "204",
                    description = "删除成功"))
    public ResponseEntity<Void> deleteTemporaryImage(
            @Parameter(description = "媒体资产 ID", required = true) @PathVariable String mediaId) {
        mediaAssetService.deleteTemporaryImage(IdUtils.parse(mediaId, "mediaId"));
        return ResponseEntity.noContent().build();
    }
}
