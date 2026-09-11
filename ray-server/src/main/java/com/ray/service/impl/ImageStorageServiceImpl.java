package com.ray.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ray.config.WorkspacePathResolver;
import com.ray.exception.BusinessException;
import com.ray.enums.MediaUploadPurpose;
import com.ray.service.ImageStorageService;
import jakarta.annotation.PostConstruct;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
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
        root = WorkspacePathResolver.resolve(directory);
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

    /** 校验图片类型和有效尺寸后按用户及用途写入分类目录。 */
    @Override
    public StoredImage storeImage(MultipartFile image, MediaUploadPurpose purpose, Long ownerUserId) {
        if (purpose == null || ownerUserId == null || ownerUserId <= 0) {
            throw BusinessException.badRequest("INVALID_IMAGE_PURPOSE", "图片上传用途无效");
        }
        if (image == null || image.isEmpty()) throw BusinessException.badRequest("EMPTY_IMAGE", "上传文件不能为空");
        if (image.getSize() > MAX_SIZE) throw new BusinessException(413, "MEDIA_TOO_LARGE", "单张图片不能超过10MB");
        Path target = null;
        String path = null;
        try {
            byte[] content = image.getBytes();
            if (content.length > MAX_SIZE)
                throw new BusinessException(413, "MEDIA_TOO_LARGE", "单张图片不能超过10MB");
            ImageInfo info = inspect(image, content);
            path = newPath(purpose, ownerUserId, info.extension());
            target = resolve(path);
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            log.info("[图片上传] 图片保存成功，路径={}", path);
            return new StoredImage(path, info.mimeType(), content.length, info.width(), info.height());
        } catch (IOException exception) {
            deletePartialFile(target, path);
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

    private ImageInfo inspect(MultipartFile image, byte[] content) {
        String name = Objects.requireNonNullElse(image.getOriginalFilename(), "");
        String extension = StrUtil.subAfter(name, ".", true).toLowerCase(Locale.ROOT);
        String contentType = image.getContentType();
        if (!EXTENSIONS.containsKey(extension)
                || !TYPES.contains(contentType)
                || !EXTENSIONS.get(extension).equals(contentType))
            throw BusinessException.badRequest("INVALID_IMAGE_TYPE", "仅支持内容与扩展名一致的 JPEG、PNG、WebP 图片");

        ImageDimensions dimensions = dimensions(contentType, content);
        return new ImageInfo(
                "jpeg".equals(extension) ? "jpg" : extension,
                contentType,
                dimensions.width(),
                dimensions.height());
    }

    private ImageDimensions dimensions(String contentType, byte[] content) {
        if ("image/webp".equals(contentType)) return webpDimensions(content);
        if (!matchesRasterSignature(contentType, content))
            throw BusinessException.badRequest("INVALID_IMAGE_CONTENT", "图片内容格式无效");

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException exception) {
            throw BusinessException.badRequest("INVALID_IMAGE_CONTENT", "图片内容无法解码");
        }
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0)
            throw BusinessException.badRequest("INVALID_IMAGE_CONTENT", "图片内容无法解码");
        return new ImageDimensions(image.getWidth(), image.getHeight());
    }

    private boolean matchesRasterSignature(String type, byte[] content) {
        if ("image/jpeg".equals(type))
            return content.length >= 3
                    && unsigned(content[0]) == 0xff
                    && unsigned(content[1]) == 0xd8
                    && unsigned(content[2]) == 0xff;
        return content.length >= 8
                && unsigned(content[0]) == 0x89
                && content[1] == 'P'
                && content[2] == 'N'
                && content[3] == 'G'
                && unsigned(content[4]) == 0x0d
                && unsigned(content[5]) == 0x0a
                && unsigned(content[6]) == 0x1a
                && unsigned(content[7]) == 0x0a;
    }

    private ImageDimensions webpDimensions(byte[] content) {
        if (content.length < 30
                || content[0] != 'R'
                || content[1] != 'I'
                || content[2] != 'F'
                || content[3] != 'F'
                || content[8] != 'W'
                || content[9] != 'E'
                || content[10] != 'B'
                || content[11] != 'P')
            throw BusinessException.badRequest("INVALID_IMAGE_CONTENT", "WebP 图片内容格式无效");

        String chunk = new String(content, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return switch (chunk) {
            case "VP8X" -> new ImageDimensions(
                    1 + littleEndian24(content, 24), 1 + littleEndian24(content, 27));
            case "VP8L" -> losslessWebpDimensions(content);
            case "VP8 " -> lossyWebpDimensions(content);
            default -> throw BusinessException.badRequest("INVALID_IMAGE_CONTENT", "WebP 图片编码格式无效");
        };
    }

    private ImageDimensions losslessWebpDimensions(byte[] content) {
        if (unsigned(content[20]) != 0x2f)
            throw BusinessException.badRequest("INVALID_IMAGE_CONTENT", "WebP 图片内容格式无效");
        int width = 1 + unsigned(content[21]) + ((unsigned(content[22]) & 0x3f) << 8);
        int height = 1
                + ((unsigned(content[22]) & 0xc0) >> 6)
                + (unsigned(content[23]) << 2)
                + ((unsigned(content[24]) & 0x0f) << 10);
        return new ImageDimensions(width, height);
    }

    private ImageDimensions lossyWebpDimensions(byte[] content) {
        if (unsigned(content[23]) != 0x9d || unsigned(content[24]) != 0x01 || unsigned(content[25]) != 0x2a)
            throw BusinessException.badRequest("INVALID_IMAGE_CONTENT", "WebP 图片内容格式无效");
        int width = (unsigned(content[26]) | (unsigned(content[27]) << 8)) & 0x3fff;
        int height = (unsigned(content[28]) | (unsigned(content[29]) << 8)) & 0x3fff;
        if (width == 0 || height == 0)
            throw BusinessException.badRequest("INVALID_IMAGE_CONTENT", "WebP 图片尺寸无效");
        return new ImageDimensions(width, height);
    }

    private int littleEndian24(byte[] content, int offset) {
        return unsigned(content[offset]) | (unsigned(content[offset + 1]) << 8) | (unsigned(content[offset + 2]) << 16);
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private Path resolve(String path) {
        String normalized = StrUtil.removePrefix(StrUtil.removePrefix(path, "/"), "\\");
        String relative;
        if (normalized.startsWith("media/")) {
            relative = StrUtil.removePrefix(normalized, "media/");
        } else if (normalized.startsWith("blogs/")) {
            // 兼容历史消费者媒体：旧记录统一迁入 uploads/user/blogs。
            relative = "user/" + normalized;
        } else {
            throw BusinessException.badRequest("INVALID_IMAGE_PATH", "图片路径无效");
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw BusinessException.badRequest("INVALID_IMAGE_PATH", "图片路径无效");
        return target;
    }

    private String newPath(MediaUploadPurpose purpose, Long ownerUserId, String suffix) {
        LocalDate today = LocalDate.now();
        return StrUtil.format(
                "/media/user/{}/{}/{}/{}/{}.{}",
                purpose.directory(),
                ownerUserId,
                today.getYear(),
                String.format("%02d", today.getMonthValue()),
                UUID.randomUUID(),
                suffix);
    }

    private void deletePartialFile(Path target, String path) {
        if (target == null) return;
        try {
            Files.deleteIfExists(target);
        } catch (IOException cleanupException) {
            log.warn("[图片上传] 写入失败后的残留文件清理失败，路径={}", path, cleanupException);
        }
    }

    private record ImageInfo(String extension, String mimeType, int width, int height) {}

    private record ImageDimensions(int width, int height) {}
}
