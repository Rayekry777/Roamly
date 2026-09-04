package com.ray.service.impl;

import static com.ray.constant.RedisConstants.CACHE_SHOP_KEY;
import static com.ray.constant.RedisConstants.CACHE_SHOP_TTL;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.entity.City;
import com.ray.entity.Shop;
import com.ray.enums.EnableStatus;
import com.ray.enums.ShopSort;
import com.ray.exception.BusinessException;
import com.ray.mapper.ShopMapper;
import com.ray.result.PageResult;
import com.ray.service.CityService;
import com.ray.service.ShopService;
import com.ray.utils.cache.CacheClient;
import com.ray.utils.converter.ViewMapper;
import com.ray.vo.ShopVO;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 商户查询、地理排序与缓存一致性实现。 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {
    private final CacheClient cacheClient;
    private final CityService cityService;

    public ShopServiceImpl(CacheClient cacheClient, CityService cityService) {
        this.cacheClient = cacheClient;
        this.cityService = cityService;
    }

    /** 按 ID 查询启用商户、处理缓存穿透并可选计算直线距离。 */
    @Override
    public ShopVO getShop(Long id, Double longitude, Double latitude) {
        validateCoordinates(longitude, latitude);
        Shop shop = cacheClient.queryWithPassThrough(
                CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null || !Integer.valueOf(EnableStatus.ENABLED.code()).equals(shop.getStatus())) {
            throw BusinessException.notFound("SHOP_NOT_FOUND", "商户不存在或已停用");
        }
        ShopVO view = ViewMapper.toShop(shop);
        return longitude == null ? view : withDistance(view, calculateDistanceMeters(longitude, latitude, shop.getX(), shop.getY()));
    }

    /** 按城市、分类、关键词、坐标和排序方式查询启用商户。 */
    @Override
    public PageResult<ShopVO> listShops(
            String cityCode, Long typeId, String keyword, String sort, int page, int size, Double longitude, Double latitude) {
        String normalizedCityCode = requireEnabledCity(cityCode);
        ShopSort shopSort = parseSort(sort);
        validateCoordinates(longitude, latitude);
        if (shopSort == ShopSort.DISTANCE && longitude == null) {
            throw BusinessException.badRequest("DISTANCE_REQUIRES_COORDINATES", "DISTANCE 排序必须同时提供 longitude 和 latitude");
        }
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        int offset = Math.multiplyExact(page - 1, size);
        List<Shop> shops = baseMapper.selectEnabledPage(
                normalizedCityCode, typeId, normalizedKeyword, shopSort.name(), longitude, latitude, offset, size);
        long total = baseMapper.countEnabledByFilter(normalizedCityCode, typeId, normalizedKeyword);
        return new PageResult<>(
                shops.stream().map(ViewMapper::toShop).toList(), page, size, total);
    }

    private String requireEnabledCity(String cityCode) {
        String normalizedCityCode = StringUtils.hasText(cityCode) ? cityCode.trim() : null;
        boolean enabled = normalizedCityCode != null
                && cityService.lambdaQuery()
                        .eq(City::getCode, normalizedCityCode)
                        .eq(City::getStatus, EnableStatus.ENABLED.code())
                        .exists();
        if (!enabled) throw BusinessException.notFound("CITY_NOT_FOUND", "城市不存在或暂未开放");
        return normalizedCityCode;
    }

    private ShopSort parseSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            throw BusinessException.badRequest("INVALID_SHOP_SORT", "商户排序方式无效");
        }
        try {
            return ShopSort.valueOf(sort.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("INVALID_SHOP_SORT", "商户排序方式仅支持 DISTANCE、SCORE、POPULAR");
        }
    }

    private void validateCoordinates(Double longitude, Double latitude) {
        if ((longitude == null) != (latitude == null)) {
            throw BusinessException.badRequest("INCOMPLETE_COORDINATES", "longitude 和 latitude 必须同时提供");
        }
        if (longitude != null && (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90)) {
            throw BusinessException.badRequest("INVALID_COORDINATES", "经纬度超出有效范围");
        }
    }

    private double calculateDistanceMeters(double longitude, double latitude, Double shopLongitude, Double shopLatitude) {
        if (shopLongitude == null || shopLatitude == null) return 0D;
        double latitudeRadians = Math.toRadians(shopLatitude - latitude);
        double longitudeRadians = Math.toRadians(shopLongitude - longitude);
        double haversine = Math.sin(latitudeRadians / 2) * Math.sin(latitudeRadians / 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(shopLatitude))
                * Math.sin(longitudeRadians / 2) * Math.sin(longitudeRadians / 2);
        return 6_371_000D * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }

    private ShopVO withDistance(ShopVO shop, double distance) {
        return new ShopVO(
                shop.id(), shop.name(), shop.typeId(), shop.images(), shop.area(), shop.address(),
                shop.longitude(), shop.latitude(), shop.avgPrice(), shop.sold(), shop.comments(),
                shop.score(), shop.openHours(), distance);
    }
}
