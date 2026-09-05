package com.ray.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.ray.enums.VoucherProductSort;
import com.ray.result.PageResult;
import com.ray.service.BusinessMediaService;
import com.ray.service.VoucherProductService;
import com.ray.shared.config.MockMvcTestConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

/** 消费者团购商品发现接口的参数与公开响应测试。 */
class VoucherProductControllerTest {
    private VoucherProductService service;
    private BusinessMediaService mediaService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(VoucherProductService.class);
        mediaService = mock(BusinessMediaService.class);
        mvc = MockMvcTestConfiguration.standalone(
                new VoucherProductController(service, mediaService));
    }

    @Test
    void forwardsDiscoveryFiltersAndCoordinates() throws Exception {
        when(service.listPublic(
                eq("310100"), eq(12L), eq("咖啡"), eq(VoucherProductSort.DISTANCE),
                eq(2), eq(20), eq(121.47), eq(31.23)))
                .thenReturn(new PageResult<>(List.of(), 2, 20, 0));

        mvc.perform(get("/v1/voucher-products")
                        .param("cityCode", "310100")
                        .param("typeId", "12")
                        .param("keyword", "咖啡")
                        .param("sort", "DISTANCE")
                        .param("page", "2")
                        .param("size", "20")
                        .param("longitude", "121.47")
                        .param("latitude", "31.23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.items").isArray());

        verify(service).listPublic(
                "310100", 12L, "咖啡", VoucherProductSort.DISTANCE,
                2, 20, 121.47, 31.23);
    }

    @Test
    void rejectsInvalidPagingAndSort() throws Exception {
        mvc.perform(get("/v1/voucher-products")
                        .param("cityCode", "310100")
                        .param("page", "0"))
                .andExpect(status().isBadRequest());
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
