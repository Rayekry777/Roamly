package com.ray.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ray.exception.BusinessException;
import com.ray.service.ImageStorageService;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 校验文件签名并安全写入本地目录的图片存储实现。 */
@Slf4j
@Service
public class ImageStorageServiceImpl implements ImageStorageService {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, String> EXTENSIONS =
            Map.of("jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");
    private final Path root;

    public ImageStorageServiceImpl(@Value("${ray.upload.image-dir}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }

    @PostConstruct
    void checkDirectory() {
        try {
            Files.createDirectories(root);
            if (!Files.isDirectory(root) || !Files.isWritable(root)) throw new IOException("目录不可写");
        } catch (IOException exception) {
            throw new IllegalStateException("图片上传目录不可用: " + root, exception);
        }
    }

    /** 校验并保存图片。 */
    @Override
    public String store(MultipartFile image) {
        if (image == null || image.isEmpty()) throw BusinessException.badRequest("EMPTY_IMAGE", "上传文件不能为空");
        if (image.getSize() > MAX_SIZE) throw new BusinessException(413, "IMAGE_TOO_LARGE", "单张图片不能超过10MB");
        try {
            String extension = validate(image);
            String path = newPath(extension);
            Path target = resolve(path);
            Files.createDirectories(target.getParent());
            image.transferTo(target);
            log.info("[图片上传] 图片保存成功，路径={}", path);
            return path;
        } catch (IOException exception) {
            throw new BusinessException(500, "IMAGE_STORE_FAILED", "图片保存失败");
        }
    }

    /** 删除发布流程中未使用的图片。 */
    @Override
    public void delete(String path) {
        Path target = resolve(path);
        if (Files.isDirectory(target)) throw BusinessException.badRequest("INVALID_IMAGE_PATH", "图片路径无效");
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new BusinessException(500, "IMAGE_DELETE_FAILED", "图片删除失败");
        }
        log.info("[图片上传] 未使用图片已清理，路径={}", path);
    }

    private String validate(MultipartFile image) throws IOException {
        String name = Objects.requireNonNullElse(image.getOriginalFilename(), "");
        String extension = StrUtil.subAfter(name, ".", true).toLowerCase(Locale.ROOT);
        String contentType = image.getContentType();
        if (!EXTENSIONS.containsKey(extension)
                || !TYPES.contains(contentType)
                || !EXTENSIONS.get(extension).equals(contentType))
            throw BusinessException.badRequest("INVALID_IMAGE_TYPE", "仅支持内容与扩展名一致的 JPEG、PNG、WebP 图片");
        try (InputStream input = image.getInputStream()) {
            if (!matches(contentType, input.readNBytes(12)))
                throw BusinessException.badRequest("INVALID_IMAGE_CONTENT", "图片内容格式无效");
        }
        return "jpeg".equals(extension) ? "jpg" : extension;
    }

    private boolean matches(String type, byte[] h) {
        if ("image/jpeg".equals(type)) return h.length >= 3 && u(h[0]) == 0xff && u(h[1]) == 0xd8 && u(h[2]) == 0xff;
        if ("image/png".equals(type))
            return h.length >= 8 && u(h[0]) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
        return h.length >= 12
                && h[0] == 'R'
                && h[1] == 'I'
                && h[2] == 'F'
                && h[3] == 'F'
                && h[8] == 'W'
                && h[9] == 'E'
                && h[10] == 'B'
                && h[11] == 'P';
    }

    private int u(byte value) {
        return value & 0xff;
    }

    private Path resolve(String path) {
        String normalized = StrUtil.removePrefix(StrUtil.removePrefix(path, "/"), "\\");
        Path target = root.resolve(normalized).normalize();
        if (!target.startsWith(root)) throw BusinessException.badRequest("INVALID_IMAGE_PATH", "图片路径无效");
        return target;
    }

    private String newPath(String suffix) {
        String id = UUID.randomUUID().toString();
        int hash = id.hashCode();
        return StrUtil.format("/blogs/{}/{}/{}.{}", hash & 0xf, (hash >> 4) & 0xf, id, suffix);
    }
}
