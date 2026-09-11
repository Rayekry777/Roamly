package com.ray.controller;

import com.ray.result.ErrorResult;
import com.ray.result.Result;
import com.ray.service.BusinessMediaService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.BusinessMediaVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/merchant/business-media/images")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "商户经营媒体")
public class BusinessMediaController {
    private final BusinessMediaService service;

    public BusinessMediaController(BusinessMediaService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传临时经营图片", operationId = "uploadMerchantBusinessImage")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "上传成功", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "413", description = "图片超过10MB", content = @Content(schema = @Schema(implementation = ErrorResult.class))),
        @ApiResponse(responseCode = "503", description = "对象存储不可用", content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    public ResponseEntity<Result<BusinessMediaVO>> upload(
            @Parameter(description = "图片文件", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "LICENSE（营业执照）、GALLERY（经营图片）、VOUCHER_COVER（券封面）、VOUCHER_DETAIL（券详情图）或 MERCHANT_AVATAR（商户头像）", required = true)
                    @RequestParam("purpose")
                    String purpose) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.ok(service.uploadImage(file, purpose)));
    }

    @DeleteMapping("/{mediaId}")
    @Operation(summary = "删除临时经营图片", operationId = "deleteMerchantBusinessImage")
    @ApiResponse(responseCode = "204", description = "删除成功")
    public ResponseEntity<Void> delete(@PathVariable String mediaId) {
        service.deleteTemporaryImage(IdUtils.parse(mediaId, "mediaId"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{mediaId}/content")
    @Operation(summary = "鉴权读取私有经营图片", operationId = "getMerchantBusinessImageContent")
    public ResponseEntity<byte[]> content(@PathVariable String mediaId) {
        BusinessMediaService.BusinessMediaContent content =
                service.readContent(IdUtils.parse(mediaId, "mediaId"));
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(content.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(content.content());
    }
}
