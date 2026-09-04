package com.ray.storage;

import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** 在开发环境补齐数据库种子引用的私有经营媒体对象。 */
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "ray.storage", name = "mode", havingValue = "LOCAL", matchIfMissing = true)
public class DevelopmentBusinessObjectInitializer implements ApplicationRunner {
    static final List<String> SEED_OBJECT_KEYS = List.of(
            "seed/merchant/application-3-license.png",
            "seed/merchant/application-4-license.png");

    private final ObjectStoragePort objectStorage;
    private final Resource seedLicense;

    public DevelopmentBusinessObjectInitializer(
            ObjectStoragePort objectStorage,
            @Value("classpath:seed/business-media/license.png") Resource seedLicense) {
        this.objectStorage = objectStorage;
        this.seedLicense = seedLicense;
    }

    /** 幂等创建缺失的种子营业执照对象，已有对象保持不变。 */
    @Override
    public void run(ApplicationArguments args) throws IOException {
        byte[] content = seedLicense.getContentAsByteArray();
        for (String objectKey : SEED_OBJECT_KEYS) {
            if (exists(objectKey)) continue;
            objectStorage.put(objectKey, "image/png", content);
            log.info("[开发数据] 已补齐种子经营媒体，objectKey={}", objectKey);
        }
    }

    private boolean exists(String objectKey) {
        try {
            objectStorage.get(objectKey);
            return true;
        } catch (ObjectStorageException exception) {
            return false;
        }
    }
}
