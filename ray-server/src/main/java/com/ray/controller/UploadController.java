package com.ray.controller;

import com.ray.result.Result;
import com.ray.service.ImageStorageService;
import com.ray.vo.ImageUploadVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/blog-images")
@Tag(name = "图片")
@SecurityRequirement(name = "BearerAuth")
public class UploadController {
    private final ImageStorageService service;

    public UploadController(ImageStorageService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "上传探店图片", operationId = "uploadBlogImage")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "上传成功", useReturnTypeSchema = true))
    public ResponseEntity<Result<ImageUploadVO>> upload(
            @Parameter(description = "JPEG、PNG 或 WebP 图片，最大 10MB", required = true) @RequestParam("file")
                    MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.ok(new ImageUploadVO(service.store(file))));
    }

    @DeleteMapping
    @Operation(summary = "删除未使用的探店图片", operationId = "deleteBlogImage")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "删除成功"))
    public ResponseEntity<Void> delete(@Parameter(description = "上传接口返回的站内路径") @RequestParam String path) {
        service.delete(path);
        return ResponseEntity.noContent().build();
    }
}
