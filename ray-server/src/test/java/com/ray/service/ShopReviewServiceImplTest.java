package com.ray.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ray.dto.ShopReviewCreateDTO;
import com.ray.entity.Shop;
import com.ray.entity.ShopReview;
import com.ray.exception.BusinessException;
import com.ray.mapper.ShopMapper;
import com.ray.mapper.ShopReviewMapper;
import com.ray.mapper.ShopReviewMediaMapper;
import com.ray.mapper.UserVoucherMapper;
import com.ray.service.impl.ShopReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ShopReviewServiceImplTest {
    private ShopMapper shopMapper;
    private ShopReviewMapper reviewMapper;
    private CurrentUserProvider currentUserProvider;
    private ShopReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        shopMapper = mock(ShopMapper.class);
        reviewMapper = mock(ShopReviewMapper.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        service = new ShopReviewServiceImpl(
                shopMapper,
                mock(ShopReviewMediaMapper.class),
                mock(MediaAssetService.class),
                currentUserProvider,
                mock(UserService.class),
                mock(UserVoucherMapper.class));
        ReflectionTestUtils.setField(service, "baseMapper", reviewMapper);
        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(shopMapper.selectById(4L)).thenReturn(new Shop().setId(4L).setStatus(1));
    }

    @Test
    void rejectsDuplicateReview() {
        when(reviewMapper.selectByShopAndUser(4L, 7L)).thenReturn(new ShopReview().setId(1L));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.create(4L, new ShopReviewCreateDTO(5, "很好", null)));

        assertEquals("REVIEW_ALREADY_EXISTS", exception.code());
        verify(reviewMapper, never()).insert(any(com.ray.entity.ShopReview.class));
    }

    @Test
    void rejectsInactiveShop() {
        when(shopMapper.selectById(4L)).thenReturn(new Shop().setId(4L).setStatus(0));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.create(4L, new ShopReviewCreateDTO(5, "很好", null)));

        assertEquals("SHOP_NOT_FOUND", exception.code());
    }
}
