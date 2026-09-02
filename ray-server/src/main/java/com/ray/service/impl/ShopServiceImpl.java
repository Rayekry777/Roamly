package com.ray.service.impl;

import static com.ray.constant.RedisConstants.CACHE_SHOP_KEY;
import static com.ray.constant.RedisConstants.CACHE_SHOP_TTL;
import static com.ray.constant.RedisConstants.SHOP_GEO_KEY;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.dto.CreateShopDTO;
import com.ray.dto.UpdateShopDTO;
import com.ray.entity.Shop;
import com.ray.exception.BusinessException;
import com.ray.mapper.ShopMapper;
import com.ray.result.PageResult;
import com.ray.service.ShopService;
import com.ray.utils.cache.CacheClient;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.converter.ViewMapper;
import com.ray.vo.ShopVO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 商户查询、地理排序与缓存一致性实现。 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {
    private final StringRedisTemplate redis;
    private final CacheClient cacheClient;

    public ShopServiceImpl(StringRedisTemplate redis, CacheClient cacheClient) {
        this.redis = redis;
        this.cacheClient = cacheClient;
    }

    /** 按 ID 查询商户并处理缓存穿透。 */
    @Override
    public ShopVO getShop(Long id) {
        Shop shop = cacheClient.queryWithPassThrough(
                CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) throw BusinessException.notFound("SHOP_NOT_FOUND", "商户不存在");
        return ViewMapper.toShop(shop);
    }

    /** 按名称、分类和可选坐标查询商户。 */
    @Override
    public PageResult<ShopVO> listShops(
            Long typeId, String name, int page, int size, Double longitude, Double latitude) {
        if ((longitude == null) != (latitude == null)) {
            throw BusinessException.badRequest("INCOMPLETE_COORDINATES", "longitude 和 latitude 必须同时提供");
        }
        if (typeId != null && longitude != null && latitude != null && StrUtil.isBlank(name)) {
            return listByLocation(typeId, page, size, longitude, latitude);
        }
        Page<Shop> result = query().eq(typeId != null, "type_id", typeId)
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(page, size));
        return new PageResult<>(
                result.getRecords().stream().map(ViewMapper::toShop).toList(), page, size, result.getTotal());
    }

    /** 新增商户。 */
    @Override
    public Long createShop(CreateShopDTO request) {
        Shop shop = new Shop()
                .setName(request.name())
                .setTypeId(IdUtils.parse(request.typeId(), "typeId"))
                .setImages(request.images())
                .setArea(request.area())
                .setAddress(request.address())
                .setX(request.longitude())
                .setY(request.latitude())
                .setAvgPrice(request.avgPrice())
                .setSold(request.sold() == null ? 0 : request.sold())
                .setComments(request.comments() == null ? 0 : request.comments())
                .setScore(request.score() == null ? 0 : request.score())
                .setOpenHours(request.openHours());
        save(shop);
        return shop.getId();
    }

    /** 更新商户并删除旧缓存。 */
    @Transactional
    @Override
    public void updateShop(Long id, UpdateShopDTO request) {
        if (getById(id) == null) throw BusinessException.notFound("SHOP_NOT_FOUND", "商户不存在");
        Shop shop = new Shop()
                .setId(id)
                .setName(request.name())
                .setImages(request.images())
                .setArea(request.area())
                .setAddress(request.address())
                .setX(request.longitude())
                .setY(request.latitude())
                .setAvgPrice(request.avgPrice())
                .setSold(request.sold())
                .setComments(request.comments())
                .setScore(request.score())
                .setOpenHours(request.openHours());
        if (request.typeId() != null) shop.setTypeId(IdUtils.parse(request.typeId(), "typeId"));
        updateById(shop);
        redis.delete(CACHE_SHOP_KEY + id);
    }

    private PageResult<ShopVO> listByLocation(Long typeId, int page, int size, double longitude, double latitude) {
        int from = (page - 1) * size;
        int end = page * size;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo()
                .search(
                        SHOP_GEO_KEY + typeId,
                        GeoReference.fromCoordinate(longitude, latitude),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                .includeDistance()
                                .limit(end));
        if (results == null || results.getContent().size() <= from) return new PageResult<>(List.of(), page, size, 0);
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> slice =
                results.getContent().stream().skip(from).limit(size).toList();
        List<Long> ids = new ArrayList<>(slice.size());
        Map<String, Distance> distances = new HashMap<>();
        slice.forEach(item -> {
            ids.add(Long.valueOf(item.getContent().getName()));
            distances.put(item.getContent().getName(), item.getDistance());
        });
        String order = StrUtil.join(",", ids);
        List<Shop> shops =
                query().in("id", ids).last("ORDER BY FIELD(id," + order + ")").list();
        shops.forEach(
                shop -> shop.setDistance(distances.get(shop.getId().toString()).getValue()));
        long total = query().eq("type_id", typeId).count();
        return new PageResult<>(shops.stream().map(ViewMapper::toShop).toList(), page, size, total);
    }
}
