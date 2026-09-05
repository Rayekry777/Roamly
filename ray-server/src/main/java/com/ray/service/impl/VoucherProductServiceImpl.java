package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ray.dto.BusinessDayHoursDTO;
import com.ray.entity.City;
import com.ray.entity.Shop;
import com.ray.entity.VoucherPackageItem;
import com.ray.entity.VoucherProduct;
import com.ray.enums.EnableStatus;
import com.ray.enums.ShopStatus;
import com.ray.enums.VoucherProductSort;
import com.ray.enums.VoucherProductType;
import com.ray.enums.VoucherReviewStatus;
import com.ray.enums.VoucherSaleStatus;
import com.ray.enums.VoucherValidityType;
import com.ray.exception.BusinessException;
import com.ray.mapper.ShopMapper;
import com.ray.mapper.VoucherPackageItemMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.result.PageResult;
import com.ray.service.CityService;
import com.ray.service.VoucherProductService;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.converter.VoucherProductPresentation;
import com.ray.vo.ShopSummaryVO;
import com.ray.vo.VoucherPackageItemVO;
import com.ray.vo.VoucherProductDetailVO;
import com.ray.vo.VoucherProductListItemVO;
import com.ray.vo.VoucherProductVO;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 实现消费者团购商品发现、详情和展示规则读取。 */
@Service
public class VoucherProductServiceImpl extends ServiceImpl<VoucherProductMapper, VoucherProduct>
        implements VoucherProductService {
    private static final TypeReference<List<BusinessDayHoursDTO>> RULES_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<LocalDate>> DATES_TYPE = new TypeReference<>() {};

    private final ShopMapper shopMapper;
    private final CityService cityService;
    private final VoucherPackageItemMapper itemMapper;
    private final ObjectMapper objectMapper;

    public VoucherProductServiceImpl(
            ShopMapper shopMapper,
            CityService cityService,
            VoucherPackageItemMapper itemMapper,
            ObjectMapper objectMapper) {
        this.shopMapper = shopMapper;
        this.cityService = cityService;
        this.itemMapper = itemMapper;
        this.objectMapper = objectMapper;
    }

    /** 按城市、分类、关键词和排序分页读取当前可售商品。 */
    @Override
    public PageResult<VoucherProductListItemVO> listPublic(
            String cityCode, Long typeId, String keyword, VoucherProductSort sort,
            int page, int size, Double longitude, Double latitude) {
        String normalizedCityCode = requireEnabledCity(cityCode);
        validateCoordinates(longitude, latitude);
        if (sort == VoucherProductSort.DISTANCE && longitude == null) {
            throw BusinessException.badRequest(
                    "DISTANCE_REQUIRES_COORDINATES", "DISTANCE 排序必须同时提供 longitude 和 latitude");
        }
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        int offset = Math.multiplyExact(page - 1, size);
        List<VoucherProduct> rows = baseMapper.selectPublicPage(
                normalizedCityCode, typeId, normalizedKeyword, sort.name(),
                longitude, latitude, offset, size);
        long total = baseMapper.countPublic(normalizedCityCode, typeId, normalizedKeyword);
        return new PageResult<>(rows.stream().map(this::toListItem).toList(), page, size, total);
    }

    /** 查询指定商户商品；默认仅返回在售商品。 */
    @Override
    public List<VoucherProductVO> listByShop(Long shopId, String status) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) throw BusinessException.notFound("SHOP_NOT_FOUND", "商户不存在");
        if (status != null && !status.isBlank() && !VoucherSaleStatus.ON_SALE.name().equals(status)) {
            throw BusinessException.badRequest("INVALID_STATUS", "公开接口仅支持查询 ON_SALE 商品");
        }
        return lambdaQuery().eq(VoucherProduct::getShopId, shopId)
                .eq(VoucherProduct::getReviewStatus, VoucherReviewStatus.APPROVED.name())
                .orderByAsc(VoucherProduct::getId).list().stream()
                .filter(product -> effectiveSaleStatus(product, shop) == VoucherSaleStatus.ON_SALE)
                .map(product -> toVO(product, shop, null)).toList();
    }

    /** 查询商品详情及所属门店摘要。 */
    @Override
    public VoucherProductDetailVO getDetail(Long productId) {
        VoucherProduct product = getById(productId);
        Shop shop = product == null ? null : shopMapper.selectById(product.getShopId());
        if (product == null || effectiveSaleStatus(product, shop) != VoucherSaleStatus.ON_SALE) {
            throw BusinessException.notFound("VOUCHER_PRODUCT_NOT_FOUND", "团购商品不存在");
        }
        if (shop == null) throw BusinessException.notFound("SHOP_NOT_FOUND", "商户不存在");
        return new VoucherProductDetailVO(toVO(product, shop, null), toShopSummary(shop));
    }

    /** 将完整商品事实转换为消费者公开模型。 */
    VoucherProductVO toVO(VoucherProduct product) {
        Shop shop = shopMapper.selectById(product.getShopId());
        return toVO(product, shop, null);
    }

    private VoucherProductVO toVO(
            VoucherProduct product, Shop shop, VoucherSaleStatus knownSaleStatus) {
        Long discount = product.getMarketAmount() == null || product.getPriceAmount() == null
                ? null : Math.max(0L, product.getMarketAmount() - product.getPriceAmount());
        VoucherProductType type = VoucherProductType.valueOf(product.getProductType());
        VoucherSaleStatus sale = knownSaleStatus == null ? effectiveSaleStatus(product, shop) : knownSaleStatus;
        VoucherValidityType validity = product.getValidityType() == null
                ? null : VoucherValidityType.valueOf(product.getValidityType());
        List<VoucherPackageItemVO> items = itemMapper.selectList(
                        new LambdaQueryWrapper<VoucherPackageItem>()
                                .eq(VoucherPackageItem::getProductId, product.getId())
                                .orderByAsc(VoucherPackageItem::getSortOrder, VoucherPackageItem::getId))
                .stream()
                .map(item -> new VoucherPackageItemVO(
                        IdUtils.format(item.getId()), item.getName(), item.getQuantity(), item.getUnit(),
                        item.getUnitPriceAmount(), item.getSortOrder()))
                .toList();
        return new VoucherProductVO(
                IdUtils.format(product.getId()), IdUtils.format(product.getShopId()), product.getTitle(),
                product.getSubTitle(), publicCoverPath(product), product.getPriceAmount(), product.getMarketAmount(),
                discount, product.getAvailableStock(), product.getSoldCount(), product.getPurchaseLimit(),
                product.getProductType(), sale == null ? product.getSaleStatus() : sale.name(),
                product.getSaleBeginTime(), product.getSaleEndTime(),
                VoucherProductPresentation.validityText(product), VoucherProductPresentation.usageRules(product),
                type.label(), sale == null ? null : sale.label(), product.getFaceValueAmount(),
                product.getMinimumSpendAmount(), product.getDiscountRateBps(), product.getMaximumDiscountAmount(),
                product.getTotalUseCount(), validity == null ? null : validity.label(),
                product.getValidBeginTime(), product.getValidEndTime(), product.getValidDays(),
                readJson(product.getUsageRulesJson(), RULES_TYPE),
                readJson(product.getExcludedDatesJson(), DATES_TYPE),
                product.getReservationRequired(), product.getReservationNotice(), product.getStackable(),
                product.getRefundAnytime(), product.getRefundExpired(), items);
    }

    private VoucherProductListItemVO toListItem(VoucherProduct product) {
        ShopSummaryVO shop = new ShopSummaryVO(
                IdUtils.format(product.getShopId()), product.getShopName(), IdUtils.format(product.getShopTypeId()),
                product.getShopCover(), product.getShopAddress(),
                product.getShopScore() == null ? 0 : product.getShopScore());
        return new VoucherProductListItemVO(
                toVO(product, null, VoucherSaleStatus.ON_SALE), shop, product.getDistance());
    }

    private ShopSummaryVO toShopSummary(Shop shop) {
        String cover = shop.getImages() == null ? null : shop.getImages().split(",")[0];
        return new ShopSummaryVO(IdUtils.format(shop.getId()), shop.getName(), IdUtils.format(shop.getTypeId()),
                cover, shop.getAddress(), shop.getScore() == null ? 0 : shop.getScore());
    }

    private String publicCoverPath(VoucherProduct product) {
        return product.getCoverMediaId() == null ? null
                : "/v1/voucher-products/" + product.getId() + "/media/" + product.getCoverMediaId() + "/content";
    }

    private String requireEnabledCity(String cityCode) {
        String normalized = StringUtils.hasText(cityCode) ? cityCode.trim() : null;
        boolean enabled = normalized != null && cityService.lambdaQuery()
                .eq(City::getCode, normalized)
                .eq(City::getStatus, EnableStatus.ENABLED.code())
                .exists();
        if (!enabled) throw BusinessException.notFound("CITY_NOT_FOUND", "城市不存在或暂未开放");
        return normalized;
    }

    private void validateCoordinates(Double longitude, Double latitude) {
        if ((longitude == null) != (latitude == null)) {
            throw BusinessException.badRequest("INCOMPLETE_COORDINATES", "longitude 和 latitude 必须同时提供");
        }
        if (longitude != null
                && (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90)) {
            throw BusinessException.badRequest("INVALID_COORDINATES", "经纬度超出有效范围");
        }
    }

    private VoucherSaleStatus effectiveSaleStatus(VoucherProduct product, Shop shop) {
        if (shop == null || !ShopStatus.ACTIVE.name().equals(shop.getStatus())
                || !VoucherReviewStatus.APPROVED.name().equals(product.getReviewStatus())) return null;
        if (VoucherSaleStatus.OFF_SALE.name().equals(product.getSaleStatus())) return VoucherSaleStatus.OFF_SALE;
        if (product.getAvailableStock() == null || product.getAvailableStock() <= 0) return VoucherSaleStatus.SOLD_OUT;
        var now = java.time.LocalDateTime.now();
        if (product.getSaleBeginTime() != null && now.isBefore(product.getSaleBeginTime())) {
            return VoucherSaleStatus.SCHEDULED;
        }
        if (product.getSaleEndTime() != null && now.isAfter(product.getSaleEndTime())) {
            return VoucherSaleStatus.ENDED;
        }
        return VoucherSaleStatus.ON_SALE;
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value == null || value.isBlank() ? "[]" : value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("团购商品结构化字段损坏", exception);
        }
    }
}
