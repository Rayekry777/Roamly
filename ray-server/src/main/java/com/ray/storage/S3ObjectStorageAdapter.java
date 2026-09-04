package com.ray.storage;

import com.ray.config.ObjectStorageProperties;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** 使用 AWS SDK 连接 S3 兼容私有对象存储。 */
@Component
@ConditionalOnProperty(prefix = "ray.storage", name = "mode", havingValue = "S3")
public class S3ObjectStorageAdapter implements ObjectStoragePort {
    private final S3Client client;
    private final String bucket;

    public S3ObjectStorageAdapter(ObjectStorageProperties properties) {
        ObjectStorageProperties.S3 config = properties.getS3();
        String endpoint = requireText(config.getEndpoint(), "S3 endpoint 未配置");
        String region = requireText(config.getRegion(), "S3 region 未配置");
        this.bucket = requireText(config.getBucket(), "S3 bucket 未配置");
        String accessKey = requireText(config.getAccessKey(), "S3 access key 未配置");
        String secretKey = requireText(config.getSecretKey(), "S3 secret key 未配置");
        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.isPathStyleAccess())
                        .build())
                .build();
    }

    S3ObjectStorageAdapter(S3Client client, String bucket) {
        this.client = client;
        this.bucket = requireText(bucket, "S3 bucket 未配置");
    }

    /** 返回配置的 S3 私有存储桶。 */
    @Override
    public String bucketName() {
        return bucket;
    }

    /** 写入 S3 私有对象。 */
    @Override
    public void put(String objectKey, String contentType, byte[] content) {
        try {
            client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(objectKey).contentType(contentType).build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception exception) {
            throw new ObjectStorageException("经营媒体写入失败", exception);
        }
    }

    /** 读取 S3 私有对象。 */
    @Override
    public StoredObject get(String objectKey) {
        try {
            var response = client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
            return new StoredObject(response.asByteArray(), response.response().contentType());
        } catch (NoSuchKeyException exception) {
            throw new ObjectStorageException("经营媒体对象不存在", exception);
        } catch (S3Exception exception) {
            throw new ObjectStorageException("经营媒体读取失败", exception);
        }
    }

    /** 幂等删除 S3 私有对象。 */
    @Override
    public void delete(String objectKey) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (S3Exception exception) {
            throw new ObjectStorageException("经营媒体删除失败", exception);
        }
    }

    /** 关闭 AWS SDK HTTP 资源。 */
    @PreDestroy
    public void close() {
        client.close();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalStateException(message);
        return value;
    }
}
