package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.entity.ShopType;
import com.ray.vo.ShopTypeVO;
import java.util.List;

/** 商户分类查询业务。 */
public interface ShopTypeService extends IService<ShopType> {
    /** 按展示顺序查询全部商户分类。 */
    List<ShopTypeVO> listTypes();
}
