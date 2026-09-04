package com.ray.service.impl;

import com.ray.exception.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 校验经营图片签名、MIME、文件名和冻结尺寸边界。 */
@Component
class BusinessImageInspector {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final int MIN_DIMENSION = 320;
    private static final int MAX_DIMENSION = 8192;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, String> EXTENSIONS =
            Map.of("jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");

    ImageMetadata inspect(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("BUSINESS_MEDIA_INVALID_TYPE", "上传图片不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(413, "MEDIA_TOO_LARGE", "单张图片不能超过10MB");
        }
        try {
            byte[] content = file.getBytes();
            if (content.length > MAX_SIZE) {
                throw new BusinessException(413, "MEDIA_TOO_LARGE", "单张图片不能超过10MB");
            }
            String original = safeFilename(file.getOriginalFilename());
            String extension = extension(original);
            String mimeType = file.getContentType();
            if (!EXTENSIONS.containsKey(extension)
                    || !TYPES.contains(mimeType)
                    || !EXTENSIONS.get(extension).equals(mimeType)) {
                throw BusinessException.badRequest(
                        "BUSINESS_MEDIA_INVALID_TYPE", "仅支持内容与扩展名一致的 JPEG、PNG、WebP 图片");
            }
            Dimensions dimensions = dimensions(mimeType, content);
            if (dimensions.width() < MIN_DIMENSION
                    || dimensions.width() > MAX_DIMENSION
                    || dimensions.height() < MIN_DIMENSION
                    || dimensions.height() > MAX_DIMENSION) {
                throw BusinessException.badRequest(
                        "BUSINESS_MEDIA_INVALID_DIMENSIONS", "图片宽高必须在320至8192像素之间");
            }
            return new ImageMetadata(
                    original,
                    "jpeg".equals(extension) ? "jpg" : extension,
                    mimeType,
                    content,
                    dimensions.width(),
                    dimensions.height());
        } catch (IOException exception) {
            throw BusinessException.badRequest("BUSINESS_MEDIA_INVALID_TYPE", "图片内容无法读取");
        }
    }

    private Dimensions dimensions(String mimeType, byte[] content) {
        if ("image/webp".equals(mimeType)) return webpDimensions(content);
        if (!matchesSignature(mimeType, content)) {
            throw BusinessException.badRequest("BUSINESS_MEDIA_INVALID_TYPE", "图片内容格式无效");
        }
        try {
            BufferedImage image = javax.imageio.ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) throw new IOException("无法解码");
            return new Dimensions(image.getWidth(), image.getHeight());
        } catch (IOException exception) {
            throw BusinessException.badRequest("BUSINESS_MEDIA_INVALID_TYPE", "图片内容无法解码");
        }
    }

    private boolean matchesSignature(String mimeType, byte[] content) {
        if ("image/jpeg".equals(mimeType)) {
            return content.length >= 3
                    && unsigned(content[0]) == 0xff
                    && unsigned(content[1]) == 0xd8
                    && unsigned(content[2]) == 0xff;
        }
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

    private Dimensions webpDimensions(byte[] content) {
        if (content.length < 30
                || content[0] != 'R'
                || content[1] != 'I'
                || content[2] != 'F'
                || content[3] != 'F'
                || content[8] != 'W'
                || content[9] != 'E'
                || content[10] != 'B'
                || content[11] != 'P') {
            throw BusinessException.badRequest("BUSINESS_MEDIA_INVALID_TYPE", "WebP 图片内容格式无效");
        }
        String chunk = new String(content, 12, 4, StandardCharsets.US_ASCII);
        return switch (chunk) {
            case "VP8X" -> new Dimensions(1 + littleEndian24(content, 24), 1 + littleEndian24(content, 27));
            case "VP8L" -> losslessDimensions(content);
            case "VP8 " -> lossyDimensions(content);
            default -> throw BusinessException.badRequest("BUSINESS_MEDIA_INVALID_TYPE", "WebP 图片编码格式无效");
        };
    }

    private Dimensions losslessDimensions(byte[] content) {
        if (unsigned(content[20]) != 0x2f) {
            throw BusinessException.badRequest("BUSINESS_MEDIA_INVALID_TYPE", "WebP 图片内容格式无效");
        }
        int width = 1 + unsigned(content[21]) + ((unsigned(content[22]) & 0x3f) << 8);
        int height = 1
                + ((unsigned(content[22]) & 0xc0) >> 6)
                + (unsigned(content[23]) << 2)
                + ((unsigned(content[24]) & 0x0f) << 10);
        return new Dimensions(width, height);
    }

    private Dimensions lossyDimensions(byte[] content) {
        if (unsigned(content[23]) != 0x9d || unsigned(content[24]) != 0x01 || unsigned(content[25]) != 0x2a) {
            throw BusinessException.badRequest("BUSINESS_MEDIA_INVALID_TYPE", "WebP 图片内容格式无效");
        }
        int width = (unsigned(content[26]) | (unsigned(content[27]) << 8)) & 0x3fff;
        int height = (unsigned(content[28]) | (unsigned(content[29]) << 8)) & 0x3fff;
        return new Dimensions(width, height);
    }

    private String safeFilename(String name) {
        String value = Objects.requireNonNullElse(name, "image").replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        if (value.isEmpty()) value = "image";
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private int littleEndian24(byte[] content, int offset) {
        return unsigned(content[offset]) | (unsigned(content[offset + 1]) << 8) | (unsigned(content[offset + 2]) << 16);
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    record ImageMetadata(
            String originalFilename, String extension, String mimeType, byte[] content, int width, int height) {
        ImageMetadata {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    private record Dimensions(int width, int height) {}
}
