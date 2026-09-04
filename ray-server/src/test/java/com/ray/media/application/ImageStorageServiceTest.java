package com.ray.media.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ray.exception.BusinessException;
import com.ray.service.impl.ImageStorageServiceImpl;
import com.ray.service.ImageStorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ImageStorageServiceTest {
    @TempDir
    Path uploadDirectory;

    @Test
    void storesValidJpegImage() throws IOException {
        ImageStorageService service = new ImageStorageServiceImpl(uploadDirectory.toString());
        String path = service.store(new MockMultipartFile("file", "shop.jpg", "image/jpeg", jpeg()));
        assertTrue(Files.exists(uploadDirectory.resolve(path.substring(1))));
    }

    @Test
    void returnsDecodedImageMetadata() throws IOException {
        ImageStorageService service = new ImageStorageServiceImpl(uploadDirectory.toString());
        ImageStorageService.StoredImage stored =
                service.storeImage(new MockMultipartFile("file", "shop.jpg", "image/jpeg", jpeg()));
        assertTrue(stored.size() > 0);
        assertTrue(stored.path().endsWith(".jpg"));
        assertEquals("image/jpeg", stored.mimeType());
        assertEquals(2, stored.width());
        assertEquals(3, stored.height());
    }

    @Test
    void rejectsMismatchedImageType() {
        ImageStorageService service = new ImageStorageServiceImpl(uploadDirectory.toString());
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertThrows(
                BusinessException.class,
                () -> service.store(new MockMultipartFile("file", "shop.jpg", "image/jpeg", png)));
    }

    @Test
    void rejectsEmptyAndOversizedImages() {
        ImageStorageService service = new ImageStorageServiceImpl(uploadDirectory.toString());
        assertThrows(
                BusinessException.class,
                () -> service.store(new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0])));
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;
        assertThrows(
                BusinessException.class,
                () -> service.store(new MockMultipartFile("file", "large.jpg", "image/jpeg", oversized)));
    }

    @Test
    void deletesStoredImage() throws IOException {
        ImageStorageService service = new ImageStorageServiceImpl(uploadDirectory.toString());
        String path = service.store(new MockMultipartFile("file", "shop.jpg", "image/jpeg", jpeg()));
        service.delete(path);
        assertFalse(Files.exists(uploadDirectory.resolve(path.substring(1))));
    }

    @Test
    void rejectsPathTraversal() {
        ImageStorageService service = new ImageStorageServiceImpl(uploadDirectory.toString());
        assertThrows(BusinessException.class, () -> service.delete("../../outside.jpg"));
    }

    private byte[] jpeg() throws IOException {
        BufferedImage image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }
}
