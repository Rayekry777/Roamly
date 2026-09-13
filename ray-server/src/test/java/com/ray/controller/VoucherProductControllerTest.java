package com.ray.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.ray.result.PageResult;
import com.ray.service.BusinessMediaService;
import com.ray.service.LocationService;
import com.ray.service.VoucherProductService;
import com.ray.shared.config.MockMvcTestConfiguration;
import java.util.List;
import com.ray.vo.LocationContextVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

/** 消费者团购商品发现接口的参数与公开响应测试。 */
class VoucherProductControllerTest {
    private VoucherProductService service;
    private BusinessMediaService mediaService;
    private LocationService locationService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(VoucherProductService.class);
        mediaService = mock(BusinessMediaService.class);
        locationService = mock(LocationService.class);
        when(locationService.resolve(any()))
                .thenReturn(new LocationContextVO(
                        "310100", "上海", null, null, null,
                        "上海", 121.47, 31.23, 30D, "READY"));
        mvc = MockMvcTestConfiguration.standalone(
                new VoucherProductController(service, mediaService));
    }

    @Test
    void retiredGlobalProductFeedIsNotMapped() throws Exception {
        mvc.perform(get("/v1/voucher-products")).andExpect(status().isNotFound());
    }

    @Test
    void servesOnlyServiceApprovedPublicMediaResult() throws Exception {
        when(mediaService.readPublicVoucherContent(1001L, 2002L))
                .thenReturn(new BusinessMediaService.BusinessMediaContent(
                        new byte[] {1, 2, 3}, "image/png", "cover.png"));

        mvc.perform(get("/v1/voucher-products/1001/media/2002/content"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));

        verify(mediaService).readPublicVoucherContent(1001L, 2002L);
    }
}
