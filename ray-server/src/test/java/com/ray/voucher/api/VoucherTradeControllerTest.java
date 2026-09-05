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
import com.ray.service.BusinessMediaService;
import com.ray.service.VoucherTradeService;
import com.ray.shared.config.MockMvcTestConfiguration;
import com.ray.vo.VoucherOrderVO;
import com.ray.vo.VoucherOrderConfirmationVO;
import java.time.LocalDateTime;
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
                new VoucherProductController(
                        mock(VoucherProductService.class), mock(BusinessMediaService.class)),
                new VoucherTradeController(tradeService));
    }

    @Test
    void rejectsInvalidQuantityBeforeCreatingOrder() throws Exception {
        mockMvc.perform(post("/v1/voucher-products/1001/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void confirmsServerCalculatedOrderSnapshot() throws Exception {
        when(tradeService.confirmOrder(1001L, 2)).thenReturn(new VoucherOrderConfirmationVO(
                "1001", "4", "套餐", 8000L, 2, 1, 3, 16000L, 2000L, 16000L, 3,
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(15)));
        mockMvc.perform(post("/v1/voucher-products/1001/order-confirmations")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxQuantity").value(3))
                .andExpect(jsonPath("$.data.payAmount").value(16000));
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
        when(tradeService.createOrder(org.mockito.ArgumentMatchers.eq(1001L), any(), org.mockito.ArgumentMatchers.eq("stage22-order-6")))
                .thenReturn(new VoucherOrderVO("10", "10", "7", "4", "1001", "套餐", 1, 8000L, 8000L,
                        8000L, "PENDING_PAYMENT", null, null, null, null));
                mockMvc.perform(post("/v1/voucher-products/1001/orders").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "stage22-order-6")
                        .content("{\"quantity\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.productId").value("1001"));
    }

    @Test
    void exposesOrderCoordinationErrors() throws Exception {
        when(tradeService.createOrder(org.mockito.ArgumentMatchers.eq(1001L), any(), org.mockito.ArgumentMatchers.eq("stage22-order-7")))
                .thenThrow(BusinessException.conflict("ORDER_REQUEST_BUSY", "订单正在处理中，请稍后重试"));
                mockMvc.perform(post("/v1/voucher-products/1001/orders").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "stage22-order-7")
                        .content("{\"quantity\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_REQUEST_BUSY"));

        when(tradeService.createOrder(org.mockito.ArgumentMatchers.eq(1001L), any(), org.mockito.ArgumentMatchers.eq("stage22-order-8")))
                .thenThrow(new BusinessException(
                        503, "ORDER_COORDINATION_UNAVAILABLE", "订单协调服务暂不可用，请稍后重试"));
                mockMvc.perform(post("/v1/voucher-products/1001/orders").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "stage22-order-8")
                        .content("{\"quantity\":1}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ORDER_COORDINATION_UNAVAILABLE"));
    }
}
