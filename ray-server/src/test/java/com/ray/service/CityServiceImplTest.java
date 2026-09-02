package com.ray.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ray.entity.City;
import com.ray.mapper.CityMapper;
import com.ray.service.impl.CityServiceImpl;
import com.ray.vo.CityVO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CityServiceImplTest {
    @Test
    void returnsEnabledCitiesFromMapperOrder() {
        CityMapper mapper = mock(CityMapper.class);
        CityServiceImpl service = new CityServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectList(any())).thenReturn(List.of(new City().setCode("330100").setName("杭州")));

        CityVO city = service.listEnabledCities().getFirst();

        assertEquals("330100", city.code());
        assertEquals("杭州", city.name());
    }
}
