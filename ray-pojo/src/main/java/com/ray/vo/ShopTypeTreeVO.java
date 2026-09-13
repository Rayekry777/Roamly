package com.ray.vo;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "两级店铺分类树")
public record ShopTypeTreeVO(String id, String name, String icon, List<ShopTypeVO> children) {
    public ShopTypeTreeVO { children = List.copyOf(children); }
}
