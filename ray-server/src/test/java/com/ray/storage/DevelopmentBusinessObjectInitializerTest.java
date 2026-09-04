package com.ray.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;

class DevelopmentBusinessObjectInitializerTest {
    @Test
    void createsMissingSeedObjectsAndKeepsExistingObjects() throws Exception {
        MemoryStorage storage = new MemoryStorage();
        String existingKey = DevelopmentBusinessObjectInitializer.SEED_OBJECT_KEYS.getFirst();
        storage.put(existingKey, "image/png", new byte[] {1});
        DevelopmentBusinessObjectInitializer initializer = new DevelopmentBusinessObjectInitializer(
                storage, new ClassPathResource("seed/business-media/license.png"));

        initializer.run(new DefaultApplicationArguments());

        assertThat(storage.objects).hasSize(5);
        assertThat(storage.objects.get(existingKey)).containsExactly(1);
        assertThat(storage.objects.get(DevelopmentBusinessObjectInitializer.SEED_OBJECT_KEYS.getLast()))
                .hasSize(543);
    }

    private static final class MemoryStorage implements ObjectStoragePort {
        private final Map<String, byte[]> objects = new HashMap<>();

        @Override
        public String bucketName() {
            return "test";
        }

        @Override
        public void put(String objectKey, String contentType, byte[] content) {
            objects.put(objectKey, content.clone());
        }

        @Override
        public StoredObject get(String objectKey) {
            byte[] content = objects.get(objectKey);
            if (content == null) throw new ObjectStorageException("not found");
            return new StoredObject(content, "image/png");
        }

        @Override
        public void delete(String objectKey) {
            objects.remove(objectKey);
        }
    }
}
