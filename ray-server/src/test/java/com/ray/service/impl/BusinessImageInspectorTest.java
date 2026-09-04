package com.ray.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ray.exception.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class BusinessImageInspectorTest {
    private final BusinessImageInspector inspector = new BusinessImageInspector();

    @Test
    void acceptsMatchingPngWithinFrozenDimensions() throws Exception {
        var result = inspector.inspect(file("license.png", "image/png", 400, 500));

        assertEquals("image/png", result.mimeType());
        assertEquals("png", result.extension());
        assertEquals(400, result.width());
        assertEquals(500, result.height());
    }

    @Test
    void rejectsSmallImageAndMismatchedMime() throws Exception {
        BusinessException small = assertThrows(
                BusinessException.class,
                () -> inspector.inspect(file("small.png", "image/png", 319, 400)));
        assertEquals("BUSINESS_MEDIA_INVALID_DIMENSIONS", small.code());

        BusinessException mismatch = assertThrows(
                BusinessException.class,
                () -> inspector.inspect(file("license.jpg", "image/png", 400, 400)));
        assertEquals("BUSINESS_MEDIA_INVALID_TYPE", mismatch.code());
    }

    private MockMultipartFile file(String name, String mimeType, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("file", name, mimeType, output.toByteArray());
    }
}
