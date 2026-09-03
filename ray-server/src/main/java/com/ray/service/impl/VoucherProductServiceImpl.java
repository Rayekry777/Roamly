package com.ray.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.entity.Shop;
import com.ray.entity.VoucherProduct;
import com.ray.enums.VoucherProductStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.ShopMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.service.VoucherProductService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.ShopSummaryVO;
import com.ray.vo.VoucherProductDetailVO;
import com.ray.vo.VoucherProductVO;
import java.util.List;
import org.springframework.stereotype.Service;

/** 团购商品公开查询实现。 */
@Service
public class VoucherProductServiceImpl extends ServiceImpl<VoucherProductMapper, VoucherProduct>
        implements VoucherProductService {
    private final ShopMapper shopMapper;

    public VoucherProductServiceImpl(ShopMapper shopMapper) {
        this.shopMapper = shopMapper;
    }

    /** 查询指定商户商品；默认仅返回在售商品。 */
    @Override
    public List<VoucherProductVO> listByShop(Long shopId, String status) {
        if (shopMapper.selectById(shopId) == null) throw BusinessException.notFound("SHOP_NOT_FOUND", "商户不存在");
        if (status != null && !status.isBlank() && !VoucherProductStatus.ON_SALE.name().equals(status))
            throw BusinessException.badRequest("INVALID_STATUS", "公开接口仅支持查询 ON_SALE 商品");
        return lambdaQuery().eq(VoucherProduct::getShopId, shopId)
                .eq(VoucherProduct::getStatus, VoucherProductStatus.ON_SALE.name())
                .orderByAsc(VoucherProduct::getId).list().stream().map(this::toVO).toList();
    }

    /** 查询商品详情及商户摘要。 */
    @Override
    public VoucherProductDetailVO getDetail(Long productId) {
        VoucherProduct product = getById(productId);
        if (product == null || !VoucherProductStatus.ON_SALE.name().equals(product.getStatus()))
            throw BusinessException.notFound("VOUCHER_PRODUCT_NOT_FOUND", "团购商品不存在");
        Shop shop = shopMapper.selectById(product.getShopId());
        if (shop == null) throw BusinessException.notFound("SHOP_NOT_FOUND", "商户不存在");
        return new VoucherProductDetailVO(toVO(product), toShopSummary(shop));
    }

    /** 将商品实体转换为接口模型。 */
    VoucherProductVO toVO(VoucherProduct product) {
        Long discount = product.getOriginalPrice() == null || product.getPayPrice() == null
                ? null : product.getOriginalPrice() - product.getPayPrice();
        String validity = "FIXED_RANGE".equals(product.getValidityType())
                ? String.valueOf(product.getValidBeginTime()) + " 至 " + product.getValidEndTime()
                : product.getValidDays() == null ? null : "购买后 " + product.getValidDays() + " 天内有效";
        return new VoucherProductVO(
                IdUtils.format(product.getId()), IdUtils.format(product.getShopId()), product.getTitle(),
                product.getSubTitle(), product.getCover(), product.getPayPrice(), product.getOriginalPrice(),
                discount, product.getAvailableStock(), product.getSoldCount(), product.getPurchaseLimit(),
                product.getSaleType(), product.getStatus(), product.getSaleBeginTime(), product.getSaleEndTime(),
                validity, product.getRules());
    }

    private ShopSummaryVO toShopSummary(Shop shop) {
        String cover = shop.getImages() == null ? null : shop.getImages().split(",")[0];
        return new ShopSummaryVO(IdUtils.format(shop.getId()), shop.getName(), IdUtils.format(shop.getTypeId()),
                cover, shop.getAddress(), shop.getScore() == null ? 0 : shop.getScore());
    }
}
