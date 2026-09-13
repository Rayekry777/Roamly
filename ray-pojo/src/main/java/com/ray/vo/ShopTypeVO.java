package com.ray.vo;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "门店分类，parentId 为空表示一级分类")
public record ShopTypeVO(String id, String name, String icon, Integer sort, String parentId) {
    public ShopTypeVO(String id, String name, String icon, Integer sort) { this(id, name, icon, sort, null); }
}
