package com.ray.storage;

import com.ray.config.ObjectStorageProperties;
import com.ray.config.WorkspacePathResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 将私有经营媒体安全保存在开发机本地目录。 */
@Component
@ConditionalOnProperty(prefix = "ray.storage", name = "mode", havingValue = "LOCAL", matchIfMissing = true)
public class LocalObjectStorageAdapter implements ObjectStoragePort {
    private final Path root;
    private final String bucket;

    public LocalObjectStorageAdapter(ObjectStorageProperties properties) {
        this.root = WorkspacePathResolver.resolve(properties.getLocal().getRoot());
        this.bucket = requireText(properties.getLocal().getBucket(), "本地对象存储桶不能为空");
        try {
            Files.createDirectories(root);
            if (!Files.isDirectory(root) || !Files.isWritable(root)) throw new IOException("目录不可写");
        } catch (IOException exception) {
            throw new IllegalStateException("本地对象存储目录不可用: " + root, exception);
        }
    }

    /** 返回本地适配器的逻辑存储桶。 */
    @Override
    public String bucketName() {
        return bucket;
    }

    /** 原子创建由服务端生成对象键对应的文件。 */
    @Override
    public void put(String objectKey, String contentType, byte[] content) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
        } catch (IOException exception) {
            throw new ObjectStorageException("经营媒体写入失败", exception);
        }
    }

    /** 从私有目录读取对象，不对外暴露目录结构。 */
    @Override
    public StoredObject get(String objectKey) {
        try {
            Path target = resolve(objectKey);
            if (!Files.isRegularFile(target)) {
                Path compatible = legacyAlias(objectKey);
                if (compatible != null) target = compatible;
            }
            if (!Files.isRegularFile(target)) throw new ObjectStorageException("经营媒体对象不存在");
            return new StoredObject(Files.readAllBytes(target), contentType(target));
        } catch (IOException exception) {
            throw new ObjectStorageException("经营媒体读取失败", exception);
        }
    }

    /** 幂等删除私有对象。 */
    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
            Path compatible = legacyAlias(objectKey);
            if (compatible != null) Files.deleteIfExists(compatible);
        } catch (IOException exception) {
            throw new ObjectStorageException("经营媒体删除失败", exception);
        }
    }

    private Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/") || objectKey.contains("\\")) {
            throw new ObjectStorageException("经营媒体对象键无效");
        }
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root) || target.equals(root)) throw new ObjectStorageException("经营媒体对象键无效");
        return target;
    }

    private Path legacyAlias(String objectKey) {
        if (objectKey.startsWith("merchant/")) {
            // 旧商户对象键统一归档到 accounts，避免在 merchant 下再次嵌套同名目录。
            return resolve("legacy/accounts/" + objectKey.substring("merchant/".length()));
        }
        if (objectKey.startsWith("seed/merchant/application-") && objectKey.endsWith("-license.png")) {
            return resolve("seed/onboarding/license/" + objectKey.substring("seed/merchant/".length()));
        }
        if ("seed/merchant/voucher-3105-cover.png".equals(objectKey)) {
            return resolve("seed/voucher/cover/voucher-3105-cover.png");
        }
        return null;
    }

    private String contentType(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }
}
