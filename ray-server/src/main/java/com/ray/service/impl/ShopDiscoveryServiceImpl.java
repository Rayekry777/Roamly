package com.ray.service.impl;
import com.ray.service.*;
import com.ray.service.discovery.*;
import com.ray.mapper.ShopDiscoveryMapper;
import com.ray.mapper.ShopTypeMapper;
import com.ray.entity.City;
import com.ray.dto.LocationContextDTO;
import com.ray.exception.BusinessException;
import com.ray.result.PageResult;
import com.ray.vo.*;
import com.ray.utils.converter.ViewMapper;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
/** 分类推荐的规则排序与固定次数批量装配实现。 */
@Service @RequiredArgsConstructor
public class ShopDiscoveryServiceImpl implements ShopDiscoveryService {
    private final ShopDiscoveryMapper mapper;
    private final ShopTypeMapper typeMapper;
    private final LocationService locationService;
    private final CityService cityService;
    /** 读事务让分页、数量与券摘要使用同一数据库快照。 */
    @Override @Transactional(readOnly=true)
    public PageResult<ShopDiscoveryVO> discover(String cityCode, Long categoryId, Long typeId, String keyword,
            String sort, int page, int size, Double longitude, Double latitude) {
        if (page < 1 || size < 1 || size > 100) throw BusinessException.badRequest("VALIDATION_FAILED", "分页参数不合法");
        if ((longitude == null) != (latitude == null)) throw BusinessException.badRequest("INCOMPLETE_COORDINATES", "请同时提供经纬度");
        if (longitude != null && (!Double.isFinite(longitude) || !Double.isFinite(latitude) || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90))
            throw BusinessException.badRequest("INVALID_COORDINATES", "经纬度超出有效范围");
        if (!Set.of("RECOMMENDED","DISTANCE","SALES","SCORE").contains(sort)) throw BusinessException.badRequest("INVALID_SHOP_SORT", "店铺排序方式无效");
        if (sort.equals("DISTANCE") && longitude == null) throw BusinessException.badRequest("DISTANCE_REQUIRES_COORDINATES", "请恢复定位后使用距离排序");
        var root = typeMapper.selectById(categoryId);
        if (root == null || root.getParentId() != null || !Set.of(1L,2L).contains(categoryId)) throw BusinessException.badRequest("INVALID_CATEGORY", "请选择美食或休闲娱乐");
        if (typeId != null) {
            var leaf = typeMapper.selectById(typeId);
            if (leaf == null || !categoryId.equals(leaf.getParentId())) throw BusinessException.badRequest("INVALID_CATEGORY", "二级分类不属于当前分区");
        }
        String districtCode = null;
        if (longitude != null) {
            var context = locationService.resolve(new LocationContextDTO(longitude,latitude,null));
            cityCode = context.cityCode(); districtCode = context.districtCode();
        }
        if (!cityService.lambdaQuery().eq(City::getCode,cityCode).eq(City::getStatus,1).exists()) throw BusinessException.notFound("CITY_NOT_FOUND", "城市尚未开放");
        String term = keyword == null || keyword.isBlank() ? null : keyword.trim();
        var query = new DiscoveryQuery(cityCode,categoryId,typeId,term,sort,longitude,latitude,districtCode,
                Math.multiplyExact(page-1,size),size,LocalDateTime.now());
        var shops = mapper.page(query);
        long total = mapper.count(query);
        Map<Long,List<DiscoveryVoucherRow>> byShop = shops.isEmpty() ? Map.of() : mapper.vouchers(
                shops.stream().map(DiscoveryShopRow::getId).toList(),query).stream().collect(Collectors.groupingBy(DiscoveryVoucherRow::getShopId));
        return new PageResult<>(shops.stream().map(shop -> {
            var candidates = byShop.getOrDefault(shop.getId(),List.of());
            var summaries = selectVouchers(candidates).stream().map(v -> new ShopVoucherSummaryVO(
                    v.getId().toString(),v.getTitle(),v.getProductType(),v.getPriceAmount(),v.getMarketAmount(),v.getTotalUseCount(),v.getSoldCount())).toList();
            return new ShopDiscoveryVO(ViewMapper.toShop(shop),shop.getCategoryId().toString(),shop.getCategoryName(),shop.getTypeName(),
                    shop.getTotalSold(),candidates.isEmpty()?0:candidates.getFirst().getAvailableCount(),summaries);
        }).toList(),page,size,total);
    }
    /** 搜索命中优先，在候选集合中选择不重复的热销券与低价券。 */
    public static List<DiscoveryVoucherRow> selectVouchers(List<DiscoveryVoucherRow> candidates) {
        Comparator<DiscoveryVoucherRow> match = Comparator.comparing(v -> !Boolean.TRUE.equals(v.getKeywordMatched()));
        var hot = match.thenComparing(DiscoveryVoucherRow::getSoldCount,Comparator.reverseOrder())
                .thenComparing(DiscoveryVoucherRow::getPriceAmount).thenComparing(DiscoveryVoucherRow::getId);
        var cheap = match.thenComparing(DiscoveryVoucherRow::getPriceAmount)
                .thenComparing(DiscoveryVoucherRow::getSoldCount,Comparator.reverseOrder()).thenComparing(DiscoveryVoucherRow::getId);
        var first = candidates.stream().min(hot);
        if (first.isEmpty()) return List.of();
        var result = new ArrayList<DiscoveryVoucherRow>(); result.add(first.get());
        candidates.stream().filter(v -> !v.getId().equals(first.get().getId())).min(cheap).ifPresent(result::add);
        return List.copyOf(result);
    }
}
