package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.entity.City;
import com.ray.vo.CityVO;
import java.util.List;

/** 提供可用城市查询能力。 */
public interface CityService extends IService<City> {
    /** 按平台配置顺序返回全部启用城市。 */
    List<CityVO> listEnabledCities();
}
