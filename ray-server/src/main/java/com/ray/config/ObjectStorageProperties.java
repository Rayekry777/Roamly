package com.ray.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 私有经营媒体对象存储配置。 */
@ConfigurationProperties(prefix = "ray.storage")
public class ObjectStorageProperties {
    public enum Mode {
        LOCAL,
        S3
    }

    private Mode mode = Mode.LOCAL;
    private long temporaryRetentionHours = 24;
    private long cleanupInitialDelayMs = 90000;
    private long cleanupIntervalMs = 3600000;
    private final Local local = new Local();
    private final S3 s3 = new S3();

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public long getTemporaryRetentionHours() { return temporaryRetentionHours; }
    public void setTemporaryRetentionHours(long value) { this.temporaryRetentionHours = value; }
    public long getCleanupInitialDelayMs() { return cleanupInitialDelayMs; }
    public void setCleanupInitialDelayMs(long value) { this.cleanupInitialDelayMs = value; }
    public long getCleanupIntervalMs() { return cleanupIntervalMs; }
    public void setCleanupIntervalMs(long value) { this.cleanupIntervalMs = value; }
    public Local getLocal() { return local; }
    public S3 getS3() { return s3; }

    public static class Local {
        private String root = "./uploads/merchant";
        private String bucket = "roamly-business-local";
        public String getRoot() { return root; }
        public void setRoot(String root) { this.root = root; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
    }

    public static class S3 {
        private String endpoint;
        private String region;
        private String bucket;
        private String accessKey;
        private String secretKey;
        private boolean pathStyleAccess = true;
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public boolean isPathStyleAccess() { return pathStyleAccess; }
        public void setPathStyleAccess(boolean value) { this.pathStyleAccess = value; }
    }
}
