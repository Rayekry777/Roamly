package com.ray.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ray.config.ObjectStorageProperties;
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

    private LocalObjectStorageAdapter storage() {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.getLocal().setRoot(root.toString());
        properties.getLocal().setBucket("test-private-bucket");
        return new LocalObjectStorageAdapter(properties);
    }
}
