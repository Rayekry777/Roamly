package com.ray.location.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ray.dto.LocationContextDTO;
import com.ray.entity.City;
import com.ray.entity.District;
import com.ray.mapper.CityMapper;
import com.ray.mapper.DistrictMapper;
import com.ray.service.impl.LocationServiceImpl;
import com.ray.vo.LocationContextVO;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocationServiceImplTest {
    @Test
    void resolvesQinghaiNormalUniversityChengbeiCampusToXining() {
        DistrictMapper districtMapper = mock(DistrictMapper.class);
        CityMapper cityMapper = mock(CityMapper.class);
        when(districtMapper.selectList(any())).thenReturn(List.of(new District()
                .setCode("630105")
                .setCityCode("630100")
                .setName("城北区")
                .setCenterLongitude(101.749746)
                .setCenterLatitude(36.742782)
                .setServiceRadiusKm(35D)
                .setStatus(1)));
        when(cityMapper.selectList(any())).thenReturn(List.of(new City()
                .setCode("630100")
                .setName("西宁")
                .setStatus(1)));
        LocationServiceImpl service = new LocationServiceImpl(districtMapper, cityMapper);

        LocationContextVO context = service.resolve(new LocationContextDTO(101.749746, 36.742782, 30D));

        assertEquals("630100", context.cityCode());
        assertEquals("西宁", context.cityName());
        assertEquals("630105", context.districtCode());
        assertEquals("城北区", context.districtName());
        assertEquals("西宁 · 城北区", context.locationLabel());
    }
}
