package com.ray.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ray.handler.GlobalExceptionHandler;
import com.ray.result.PageResult;
import com.ray.service.ShopService;
import com.ray.vo.ShopVO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** 商户列表 Controller 的筛选参数与输入校验测试。 */
class ShopControllerTest {
    private ShopService shopService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        shopService = mock(ShopService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ShopController(shopService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    /** cityCode 是城市隔离的必填条件。 */
    @Test
    void rejectsMissingCityCode() throws Exception {
        mockMvc.perform(get("/v1/shops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    /** 仅允许契约声明的三种排序方式。 */
    @Test
    void rejectsUnsupportedSort() throws Exception {
        mockMvc.perform(get("/v1/shops").param("cityCode", "330100").param("sort", "NEWEST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    /** 经纬度必须成对提交，具体业务规则由服务层统一处理。 */
    @Test
    void delegatesCompleteCoordinatesAndFilters() throws Exception {
        when(shopService.listShops("330100", 2L, "咖啡", "DISTANCE", 2, 10, 120.1, 30.2))
                .thenReturn(new PageResult<>(List.of(), 2, 10, 0));

        mockMvc.perform(get("/v1/shops")
                        .param("cityCode", "330100")
                        .param("typeId", "2")
                        .param("keyword", "咖啡")
                        .param("sort", "DISTANCE")
                        .param("page", "2")
                        .param("longitude", "120.1")
                        .param("latitude", "30.2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        verify(shopService).listShops("330100", 2L, "咖啡", "DISTANCE", 2, 10, 120.1, 30.2);
    }

    /** 商户详情将可选坐标透传到服务层，以便返回距离。 */
    @Test
    void delegatesDetailCoordinates() throws Exception {
        when(shopService.getShop(4L, 120.1, 30.2))
                .thenReturn(new ShopVO("4", "咖啡店", "2", "", null, null, 120.1, 30.2,
                        null, 0, 0, 0, null, 0D));

        mockMvc.perform(get("/v1/shops/4").param("longitude", "120.1").param("latitude", "30.2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.distance").value(0D));

        verify(shopService).getShop(4L, 120.1, 30.2);
    }
}
