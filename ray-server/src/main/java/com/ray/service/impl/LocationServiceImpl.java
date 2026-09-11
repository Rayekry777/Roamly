package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ray.dto.LocationContextDTO;
import com.ray.entity.City;
import com.ray.entity.District;
import com.ray.exception.BusinessException;
import com.ray.mapper.CityMapper;
import com.ray.mapper.DistrictMapper;
import com.ray.service.LocationService;
import com.ray.vo.LocationContextVO;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 使用数据库中的城市、区县和服务半径解析定位，避免信任客户端手工地域参数。
 * 后续接入地图供应商逆地理编码时仍保持城市与区县字段独立。
 */
@Service
public class LocationServiceImpl implements LocationService {
    private final DistrictMapper districtMapper;
    private final CityMapper cityMapper;

    /** 注入城市和区县字典，用于按数据库配置解析服务范围。 */
    public LocationServiceImpl(DistrictMapper districtMapper, CityMapper cityMapper) {
        this.districtMapper = districtMapper;
        this.cityMapper = cityMapper;
    }

    /** 解析坐标对应的最近开放区县，并返回城市和区县的独立字段。 */
    @Override
    public LocationContextVO resolve(LocationContextDTO request) {
        List<District> districts = districtMapper.selectList(new QueryWrapper<District>()
                .eq("status", 1)
                .isNotNull("center_longitude")
                .isNotNull("center_latitude")
                .gt("service_radius_km", 0));
        DistrictDistance nearest = districts.stream()
                .map(district -> withDistance(district, request.longitude(), request.latitude()))
                .filter(candidate -> candidate.distanceKm() <= candidate.district().getServiceRadiusKm())
                .min((left, right) -> Double.compare(left.distanceKm(), right.distanceKm()))
                .orElseThrow(() -> BusinessException.badRequest("LOCATION_CITY_UNSUPPORTED", "当前定位城市暂未开放"));

        Map<String, City> cities = cityMapper.selectList(new QueryWrapper<City>()
                        .eq("status", 1)
                        .in("code", districts.stream().map(District::getCityCode).distinct().toList()))
                .stream()
                .collect(Collectors.toMap(City::getCode, Function.identity()));
        District district = nearest.district();
        City city = cities.get(district.getCityCode());
        if (city == null) {
            throw new BusinessException(500, "LOCATION_CITY_CONFIGURATION_INVALID", "定位城市配置不完整");
        }
        String label = city.getName() + " · " + district.getName();
        return new LocationContextVO(city.getCode(), city.getName(), district.getCode(),
                district.getName(), null, label, request.longitude(), request.latitude(),
                request.accuracy(), "READY");
    }

    private DistrictDistance withDistance(District district, double longitude, double latitude) {
        double lat1 = Math.toRadians(district.getCenterLatitude());
        double lat2 = Math.toRadians(latitude);
        double dLat = Math.toRadians(latitude - district.getCenterLatitude());
        double dLon = Math.toRadians(longitude - district.getCenterLongitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double distance = 6371D * 2D * Math.atan2(Math.sqrt(a), Math.sqrt(1D - a));
        return new DistrictDistance(district, distance);
    }

    private record DistrictDistance(District district, double distanceKm) {}
}
