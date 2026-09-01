package com.ray.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.ray.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("upload")
@Tag(name = "文件上传")
public class UploadController {

    private final Path uploadRoot;

    public UploadController(@Value("${ray.upload.image-dir}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @PostMapping("blog")
    @Operation(summary = "上传探店图片", operationId = "uploadBlogImage")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return Result.fail("上传文件不能为空");
        }
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            Path target = resolveUploadPath(fileName);
            Files.createDirectories(target.getParent());
            image.transferTo(target);
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @GetMapping("/blog/delete")
    @Operation(summary = "删除探店图片", operationId = "deleteBlogImage")
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
        return Result.ok();
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

    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        if (StrUtil.isBlank(suffix) || suffix.length() > 10 || !suffix.matches("[A-Za-z0-9]+")) {
            suffix = "jpg";
        }
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
