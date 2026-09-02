package com.ray.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.entity.ShopType;
import com.ray.mapper.ShopTypeMapper;
import com.ray.service.ShopTypeService;
import com.ray.utils.converter.ViewMapper;
import com.ray.vo.ShopTypeVO;
import java.util.List;
import org.springframework.stereotype.Service;

/** 商户分类查询实现。 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements ShopTypeService {
    /** 按 sort 字段升序查询商户分类。 */
    @Override
    public List<ShopTypeVO> listTypes() {
        return query().orderByAsc("sort").list().stream()
                .map(ViewMapper::toShopType)
                .toList();
    }
}
