package com.ray.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.entity.City;
import com.ray.enums.EnableStatus;
import com.ray.mapper.CityMapper;
import com.ray.service.CityService;
import com.ray.utils.cache.CacheNames;
import com.ray.vo.CityVO;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** 从城市字典读取对客户端开放的城市。 */
@Service
public class CityServiceImpl extends ServiceImpl<CityMapper, City> implements CityService {
    /** 查询全部启用城市并保持稳定排序。 */
    @Override
    @Cacheable(cacheNames = CacheNames.CITIES, key = "'all'", sync = true)
    public List<CityVO> listEnabledCities() {
        return query()
                .eq("status", EnableStatus.ENABLED.code())
                .orderByAsc("sort", "id")
                .list()
                .stream()
                .map(city -> new CityVO(city.getCode(), city.getName()))
                .toList();
    }
}
