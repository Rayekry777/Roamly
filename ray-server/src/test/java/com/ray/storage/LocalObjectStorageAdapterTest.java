package com.ray.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ray.config.ObjectStorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalObjectStorageAdapterTest {
    @TempDir
    Path root;

    @Test
    void storesReadsAndIdempotentlyDeletesPrivateObject() {
        LocalObjectStorageAdapter storage = storage();
        byte[] content = {1, 2, 3, 4};

        storage.put("merchant/8/image.png", "image/png", content);
        ObjectStoragePort.StoredObject stored = storage.get("merchant/8/image.png");

        assertArrayEquals(content, stored.content());
        assertEquals("image/png", stored.contentType());
        assertEquals("test-private-bucket", storage.bucketName());
        storage.delete("merchant/8/image.png");
        storage.delete("merchant/8/image.png");
        assertThrows(ObjectStorageException.class, () -> storage.get("merchant/8/image.png"));
    }

    @Test
    void rejectsTraversalAndAbsoluteObjectKeys() {
        LocalObjectStorageAdapter storage = storage();

        assertThrows(ObjectStorageException.class, () -> storage.put("../outside.png", "image/png", new byte[] {1}));
        assertThrows(ObjectStorageException.class, () -> storage.put("/absolute.png", "image/png", new byte[] {1}));
        assertThrows(ObjectStorageException.class, () -> storage.put("merchant\\bad.png", "image/png", new byte[] {1}));
    }

    @Test
    void readsLegacyMerchantObjectFromCompatibilityDirectory() throws IOException {
        byte[] content = {4, 3, 2, 1};
        Path legacy = root.resolve("legacy/accounts/7/old-avatar.png");
        Files.createDirectories(legacy.getParent());
        Files.write(legacy, content);

        ObjectStoragePort.StoredObject stored = storage().get("merchant/7/old-avatar.png");

        assertArrayEquals(content, stored.content());
    }

    @Test
    void mapsLegacySeedKeyToCategorizedSeedFile() throws IOException {
        byte[] content = {9, 8, 7};
        Path categorized = root.resolve("seed/onboarding/license/application-3-license.png");
        Files.createDirectories(categorized.getParent());
        Files.write(categorized, content);

        ObjectStoragePort.StoredObject stored = storage().get("seed/merchant/application-3-license.png");

        assertArrayEquals(content, stored.content());
    }

    private LocalObjectStorageAdapter storage() {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.getLocal().setRoot(root.toString());
        properties.getLocal().setBucket("test-private-bucket");
        return new LocalObjectStorageAdapter(properties);
    }
}
