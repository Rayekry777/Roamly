package com.ray.voucher.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ray.controller.VoucherProductController;
import com.ray.controller.VoucherTradeController;
import com.ray.exception.BusinessException;
import com.ray.result.PageResult;
import com.ray.service.VoucherProductService;
import com.ray.service.VoucherTradeService;
import com.ray.shared.config.MockMvcTestConfiguration;
import com.ray.vo.VoucherOrderVO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 团购订单 Controller 的字符串 ID 和请求校验测试。 */
class VoucherTradeControllerTest {
    private VoucherTradeService tradeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tradeService = mock(VoucherTradeService.class);
        mockMvc = MockMvcTestConfiguration.standalone(
                new VoucherProductController(mock(VoucherProductService.class)),
                new VoucherTradeController(tradeService));
    }

    @Test
    void rejectsQuantityOtherThanOneBeforeCreatingOrder() throws Exception {
        mockMvc.perform(post("/v1/voucher-products/1001/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void listsAndCancelsMyOrdersWithStringId() throws Exception {
        when(tradeService.listOrders(null, 1, 10)).thenReturn(new PageResult<>(List.of(), 1, 10, 0));
        mockMvc.perform(get("/v1/users/me/orders")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
        mockMvc.perform(delete("/v1/users/me/orders/10001")).andExpect(status().isNoContent());
        verify(tradeService).listOrders(null, 1, 10);
        verify(tradeService).cancelOrder(10001L);
    }

    @Test
    void createsOrderUsingProductStringId() throws Exception {
        when(tradeService.createOrder(org.mockito.ArgumentMatchers.eq(1001L), any()))
                .thenReturn(new VoucherOrderVO("10", "10", "7", "4", "1001", "套餐", 1, 8000L, 8000L,
                        8000L, "PENDING_PAYMENT", null, null, null, null));
        mockMvc.perform(post("/v1/voucher-products/1001/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.productId").value("1001"));
    }

    @Test
    void exposesOrderCoordinationErrors() throws Exception {
        when(tradeService.createOrder(org.mockito.ArgumentMatchers.eq(1001L), any()))
                .thenThrow(BusinessException.conflict("ORDER_REQUEST_BUSY", "订单正在处理中，请稍后重试"));
        mockMvc.perform(post("/v1/voucher-products/1001/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_REQUEST_BUSY"));

        when(tradeService.createOrder(org.mockito.ArgumentMatchers.eq(1001L), any()))
                .thenThrow(new BusinessException(
                        503, "ORDER_COORDINATION_UNAVAILABLE", "订单协调服务暂不可用，请稍后重试"));
        mockMvc.perform(post("/v1/voucher-products/1001/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ORDER_COORDINATION_UNAVAILABLE"));
    }
}
