package com.ray.utils.converter;

import com.ray.entity.ContentSection;
import com.ray.entity.Shop;
import com.ray.entity.ShopType;
import com.ray.entity.User;
import com.ray.vo.SectionDetailVO;
import com.ray.vo.SectionVO;
import com.ray.vo.ShopTypeVO;
import com.ray.vo.ShopVO;
import com.ray.vo.UserVO;

/** 持久化模型到接口视图的集中映射。 */
public final class ViewMapper {
    private ViewMapper() {}

    public static UserVO toUser(User user) {
        return new UserVO(IdUtils.format(user.getId()), user.getNickName(), user.getIcon());
    }

    public static ShopTypeVO toShopType(ShopType type) {
        return new ShopTypeVO(IdUtils.format(type.getId()), type.getName(), type.getIcon(), type.getSort(), type.getParentId() == null ? null : IdUtils.format(type.getParentId()));
    }

    public static ShopVO toShop(Shop shop) {
        return new ShopVO(
                IdUtils.format(shop.getId()),
                shop.getName(),
                IdUtils.format(shop.getTypeId()),
                shop.getImages(),
                shop.getArea(),
                shop.getAddress(),
                shop.getX(),
                shop.getY(),
                shop.getAvgPrice(),
                shop.getSold(),
                shop.getComments(),
                shop.getScore(),
                shop.getOpenHours(),
                shop.getDistance());
    }

    public static SectionVO toSection(ContentSection section, boolean followedByMe) {
        return new SectionVO(
                IdUtils.format(section.getId()),
                section.getCode(),
                section.getName(),
                section.getIcon(),
                Integer.valueOf(1).equals(section.getAllowShopVisit()),
                followedByMe);
    }

    public static SectionDetailVO toSectionDetail(ContentSection section, boolean followedByMe) {
        return new SectionDetailVO(
                IdUtils.format(section.getId()),
                section.getCode(),
                section.getName(),
                section.getIcon(),
                Integer.valueOf(1).equals(section.getAllowShopVisit()),
                followedByMe,
                section.getDescription(),
                section.getCover());
    }

}
