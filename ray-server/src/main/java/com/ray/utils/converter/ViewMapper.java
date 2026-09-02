package com.ray.utils.converter;

import com.ray.entity.Blog;
import com.ray.entity.Shop;
import com.ray.entity.ShopType;
import com.ray.entity.User;
import com.ray.entity.UserInfo;
import com.ray.entity.Voucher;
import com.ray.vo.BlogVO;
import com.ray.vo.ShopTypeVO;
import com.ray.vo.ShopVO;
import com.ray.vo.UserInfoVO;
import com.ray.vo.UserVO;
import com.ray.vo.VoucherVO;

/** 持久化模型到接口视图的集中映射。 */
public final class ViewMapper {
    private ViewMapper() {}

    public static UserVO toUser(User user) {
        return new UserVO(IdUtils.format(user.getId()), user.getNickName(), user.getIcon());
    }

    public static UserInfoVO toUserInfo(UserInfo info) {
        return new UserInfoVO(
                IdUtils.format(info.getUserId()),
                info.getCity(),
                info.getIntroduce(),
                info.getFans(),
                info.getFollowee(),
                info.getGender(),
                info.getBirthday(),
                info.getCredits(),
                info.getLevel());
    }

    public static ShopTypeVO toShopType(ShopType type) {
        return new ShopTypeVO(IdUtils.format(type.getId()), type.getName(), type.getIcon(), type.getSort());
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

    public static BlogVO toBlog(Blog blog) {
        return new BlogVO(
                IdUtils.format(blog.getId()),
                IdUtils.format(blog.getShopId()),
                IdUtils.format(blog.getUserId()),
                blog.getIcon(),
                blog.getName(),
                blog.getIsLike(),
                blog.getTitle(),
                blog.getImages(),
                blog.getContent(),
                blog.getLiked(),
                blog.getComments(),
                blog.getCreateTime());
    }

    public static VoucherVO toVoucher(Voucher voucher) {
        return new VoucherVO(
                IdUtils.format(voucher.getId()),
                IdUtils.format(voucher.getShopId()),
                voucher.getTitle(),
                voucher.getSubTitle(),
                voucher.getRules(),
                voucher.getPayValue(),
                voucher.getActualValue(),
                voucher.getType(),
                voucher.getStatus(),
                voucher.getStock(),
                voucher.getBeginTime(),
                voucher.getEndTime());
    }
}
