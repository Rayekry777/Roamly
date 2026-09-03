package com.ray.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ray.entity.City;
import com.ray.entity.Shop;
import com.ray.exception.BusinessException;
import com.ray.mapper.ShopMapper;
import com.ray.service.impl.ShopServiceImpl;
import com.ray.utils.cache.CacheClient;
import com.ray.vo.ShopVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** 商户列表服务的城市、排序和坐标契约测试。 */
class ShopServiceImplTest {
    private ShopServiceImpl shopService;
    private CacheClient cacheClient;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        CityService cityService = mock(CityService.class);
        LambdaQueryChainWrapper<City> cityQuery = mock(LambdaQueryChainWrapper.class);
        when(cityService.lambdaQuery()).thenReturn(cityQuery);
        when(cityQuery.eq(any(SFunction.class), any())).thenReturn(cityQuery);
        when(cityQuery.exists()).thenReturn(true);

        cacheClient = mock(CacheClient.class);
        shopService = new ShopServiceImpl(mock(StringRedisTemplate.class), cacheClient, cityService);
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

    /** 详情坐标与商户坐标重合时应返回零距离。 */
    @Test
    void calculatesDetailDistanceForCompleteCoordinates() {
        Shop shop = new Shop().setId(4L).setStatus(1).setX(120.1).setY(30.2);
        when(cacheClient.queryWithPassThrough(
                        eq("cache:shop:"), eq(4L), eq(Shop.class), any(), any(), any()))
                .thenReturn(shop);

        ShopVO result = shopService.getShop(4L, 120.1, 30.2);

        org.junit.jupiter.api.Assertions.assertEquals(0D, result.distance());
    }
}
