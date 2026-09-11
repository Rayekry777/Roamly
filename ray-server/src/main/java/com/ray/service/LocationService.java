package com.ray.service;

import com.ray.dto.LocationContextDTO;
import com.ray.vo.LocationContextVO;

/** 真实坐标解析为可用于本地发现的地域上下文。 */
public interface LocationService {
    /** 根据客户端真实坐标解析已开放的城市和区县上下文。 */
    LocationContextVO resolve(LocationContextDTO request);
}
