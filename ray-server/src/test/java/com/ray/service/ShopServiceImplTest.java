package com.ray.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ray.entity.City;
import com.ray.exception.BusinessException;
import com.ray.mapper.ShopMapper;
import com.ray.service.impl.ShopServiceImpl;
import com.ray.utils.cache.CacheClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** 商户列表服务的城市、排序和坐标契约测试。 */
class ShopServiceImplTest {
    private ShopServiceImpl shopService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        CityService cityService = mock(CityService.class);
        LambdaQueryChainWrapper<City> cityQuery = mock(LambdaQueryChainWrapper.class);
        when(cityService.lambdaQuery()).thenReturn(cityQuery);
        when(cityQuery.eq(any(SFunction.class), any())).thenReturn(cityQuery);
        when(cityQuery.exists()).thenReturn(true);

        shopService = new ShopServiceImpl(mock(StringRedisTemplate.class), mock(CacheClient.class), cityService);
        ReflectionTestUtils.setField(shopService, "baseMapper", mock(ShopMapper.class));
    }

    /** 只提交一个坐标会在访问数据库前被拒绝。 */
    @Test
    void rejectsIncompleteCoordinates() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> shopService.listShops("330100", null, null, "POPULAR", 1, 10, 120.1, null));

        org.junit.jupiter.api.Assertions.assertEquals("INCOMPLETE_COORDINATES", exception.code());
    }

    /** 距离排序必须给出完整且有效的坐标。 */
    @Test
    void rejectsDistanceSortWithoutCoordinates() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> shopService.listShops("330100", null, null, "DISTANCE", 1, 10, null, null));

        org.junit.jupiter.api.Assertions.assertEquals("DISTANCE_REQUIRES_COORDINATES", exception.code());
    }

    /** 不受支持的排序值不会落入默认热门排序。 */
    @Test
    void rejectsUnsupportedSort() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> shopService.listShops("330100", null, null, "NEWEST", 1, 10, null, null));

        org.junit.jupiter.api.Assertions.assertEquals("INVALID_SHOP_SORT", exception.code());
    }
}
