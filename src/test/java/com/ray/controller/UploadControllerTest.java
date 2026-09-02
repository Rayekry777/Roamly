package com.ray.controller;

import com.ray.dto.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadControllerTest {

    @TempDir
    Path uploadDirectory;

    private UploadController controller;

    @BeforeEach
    void setUp() {
        controller = new UploadController(uploadDirectory.toString());
        controller.checkUploadDirectory();
    }

    @Test
    void shouldStoreValidJpegImage() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        MockMultipartFile image = new MockMultipartFile("file", "shop.jpg", "image/jpeg", jpeg);

        Result result = controller.uploadImage(image);

        assertTrue(result.getSuccess());
        assertTrue(Files.exists(uploadDirectory.resolve(result.getData().toString().substring(1))));
    }

    @Test
    void shouldRejectMismatchedImageType() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile image = new MockMultipartFile("file", "shop.jpg", "image/jpeg", png);

        Result result = controller.uploadImage(image);

        assertFalse(result.getSuccess());
    }
}
