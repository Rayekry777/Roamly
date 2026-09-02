package com.ray.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.ray.dto.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
@Tag(name = "文件上传")
public class UploadController {

    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, String> EXTENSION_CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    );

    private final Path uploadRoot;

    public UploadController(@Value("${ray.upload.image-dir}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void checkUploadDirectory() {
        try {
            Files.createDirectories(uploadRoot);
            if (!Files.isDirectory(uploadRoot) || !Files.isWritable(uploadRoot)) {
                throw new IllegalStateException("图片上传目录不可写: " + uploadRoot);
            }
            log.info("[图片上传] 上传目录检查通过，目录={}", uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建图片上传目录: " + uploadRoot, e);
        }
    }

    @PostMapping("blog")
    @Operation(summary = "上传探店图片", operationId = "uploadBlogImage")
    @SecurityRequirement(name = "TokenAuth")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return Result.fail("上传文件不能为空");
        }
        if (image.getSize() > MAX_IMAGE_SIZE) {
            return Result.fail("单张图片不能超过10MB");
        }
        try {
            String extension = validateImage(image);
            String fileName = createNewFileName(extension);
            Path target = resolveUploadPath(fileName);
            Files.createDirectories(target.getParent());
            image.transferTo(target);
            log.info("[图片上传] 探店图片保存成功，路径={}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/blog/delete")
    @Operation(summary = "删除探店图片", operationId = "deleteBlogImage")
    @SecurityRequirement(name = "TokenAuth")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        Path file;
        try {
            file = resolveUploadPath(filename);
        } catch (IllegalArgumentException e) {
            return Result.fail("错误的文件名称");
        }
        if (Files.isDirectory(file)) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file.toFile());
        log.info("[图片上传] 未使用图片已清理，路径={}", filename);
        return Result.ok();
    }

    private String validateImage(MultipartFile image) throws IOException {
        String originalFilename = Objects.requireNonNullElse(image.getOriginalFilename(), "");
        String extension = StrUtil.subAfter(originalFilename, ".", true).toLowerCase(Locale.ROOT);
        String contentType = image.getContentType();
        if (!EXTENSION_CONTENT_TYPES.containsKey(extension)) {
            throw new IllegalArgumentException("仅支持JPEG、PNG、WebP图片");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)
                || !EXTENSION_CONTENT_TYPES.get(extension).equals(contentType)) {
            throw new IllegalArgumentException("图片扩展名与文件类型不匹配");
        }
        try (InputStream input = image.getInputStream()) {
            byte[] header = input.readNBytes(12);
            if (!matchesSignature(contentType, header)) {
                throw new IllegalArgumentException("图片内容格式无效");
            }
        }
        return "jpeg".equals(extension) ? "jpg" : extension;
    }

    private boolean matchesSignature(String contentType, byte[] header) {
        if ("image/jpeg".equals(contentType)) {
            return header.length >= 3 && unsigned(header[0]) == 0xFF
                    && unsigned(header[1]) == 0xD8 && unsigned(header[2]) == 0xFF;
        }
        if ("image/png".equals(contentType)) {
            return header.length >= 8 && unsigned(header[0]) == 0x89
                    && header[1] == 'P' && header[2] == 'N' && header[3] == 'G';
        }
        return header.length >= 12 && header[0] == 'R' && header[1] == 'I'
                && header[2] == 'F' && header[3] == 'F' && header[8] == 'W'
                && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private Path resolveUploadPath(String relativePath) {
        String normalizedName = StrUtil.removePrefix(relativePath, "/");
        normalizedName = StrUtil.removePrefix(normalizedName, "\\");
        Path resolved = uploadRoot.resolve(normalizedName).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("文件路径越界");
        }
        return resolved;
    }

    private String createNewFileName(String suffix) {
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
