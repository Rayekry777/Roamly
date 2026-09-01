package com.ray.service.impl;

import com.ray.entity.ShopType;
import com.ray.mapper.ShopTypeMapper;
import com.ray.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

}
