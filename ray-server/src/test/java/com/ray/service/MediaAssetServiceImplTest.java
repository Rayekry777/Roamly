package com.ray.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ray.entity.MediaAsset;
import com.ray.enums.MediaAssetStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.MediaAssetMapper;
import com.ray.service.ImageStorageService.StoredImage;
import com.ray.service.impl.MediaAssetServiceImpl;
import com.ray.vo.MediaAssetVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class MediaAssetServiceImplTest {
    private MediaAssetMapper mapper;
    private ImageStorageService imageStorageService;
    private CurrentUserProvider currentUserProvider;
    private MediaAssetServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(MediaAssetMapper.class);
        imageStorageService = mock(ImageStorageService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        service = new MediaAssetServiceImpl(imageStorageService, currentUserProvider, 24);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void createsOwnedTemporaryMediaRecord() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1});
        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(imageStorageService.storeImage(file))
                .thenReturn(new StoredImage("/blogs/1/2/photo.jpg", "image/jpeg", 1, 100, 200));
        when(mapper.insert(any(MediaAsset.class))).thenAnswer(invocation -> {
            invocation.<MediaAsset>getArgument(0).setId(99L);
            return 1;
        });

        MediaAssetVO result = service.uploadImage(file);

        assertEquals("99", result.id());
        assertEquals(100, result.width());
    }

    @Test
    void removesPhysicalFileWhenDatabaseInsertFails() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1});
        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(imageStorageService.storeImage(file))
                .thenReturn(new StoredImage("/blogs/1/2/photo.jpg", "image/jpeg", 1, 100, 200));
        when(mapper.insert(any(MediaAsset.class))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.uploadImage(file));

        assertEquals("IMAGE_STORE_FAILED", exception.code());
        verify(imageStorageService).delete("/blogs/1/2/photo.jpg");
    }

    @Test
    void rejectsDeletingBoundMedia() {
        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(mapper.selectById(9L)).thenReturn(new MediaAsset()
                .setId(9L)
                .setOwnerUserId(7L)
                .setStatus(MediaAssetStatus.BOUND.code()));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.deleteTemporaryImage(9L));

        assertEquals("MEDIA_ALREADY_BOUND", exception.code());
    }

    @Test
    void rejectsDeletingAnotherUsersMedia() {
        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(mapper.selectById(9L)).thenReturn(new MediaAsset()
                .setId(9L)
                .setOwnerUserId(8L)
                .setStatus(MediaAssetStatus.TEMPORARY.code()));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.deleteTemporaryImage(9L));

        assertEquals("MEDIA_NOT_OWNED", exception.code());
    }

    @Test
    void marksOwnedTemporaryMediaDeletedBeforeRemovingFile() {
        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(mapper.selectById(9L)).thenReturn(new MediaAsset()
                .setId(9L)
                .setOwnerUserId(7L)
                .setStoragePath("/blogs/1/2/photo.jpg")
                .setStatus(MediaAssetStatus.TEMPORARY.code()));
        when(mapper.update(isNull(), any())).thenReturn(1);

        service.deleteTemporaryImage(9L);

        verify(imageStorageService).delete("/blogs/1/2/photo.jpg");
    }

    @Test
    void cleansExpiredTemporaryMediaAndMarksPhysicalDeletionComplete() {
        MediaAsset expired = new MediaAsset()
                .setId(9L)
                .setStoragePath("/blogs/1/2/photo.jpg")
                .setStatus(MediaAssetStatus.TEMPORARY.code())
                .setExpireTime(LocalDateTime.now().minusMinutes(1));
        AtomicInteger queryCount = new AtomicInteger();
        when(mapper.selectList(any()))
                .thenAnswer(invocation -> queryCount.getAndIncrement() == 0 ? List.of(expired) : List.of());
        when(mapper.update(isNull(), any())).thenReturn(1);

        service.cleanupExpiredTemporaryImages();

        verify(imageStorageService).delete("/blogs/1/2/photo.jpg");
        verify(mapper, times(2)).update(isNull(), any());
    }
}
