package com.ray.controller;

import static com.ray.shared.config.MockMvcTestConfiguration.standalone;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ray.enums.MerchantAfterSaleStage;
import com.ray.result.PageResult;
import com.ray.service.VoucherRefundService;
import com.ray.vo.MerchantRefundCandidateVO;
import com.ray.vo.VoucherRefundVO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

/** 商户售后聚合筛选、候选查询和字符串 ID 请求契约。 */
class MerchantRefundControllerTest {
    private final VoucherRefundService service = mock(VoucherRefundService.class);
    private final MockMvc mvc = standalone(new MerchantRefundController(service));

    @Test
    void listsByStageAndKeyword() throws Exception {
        when(service.merchantList(null, MerchantAfterSaleStage.PENDING, "9101", 1, 20))
                .thenReturn(new PageResult<>(List.of(), 1, 20, 0));
        mvc.perform(get("/v1/merchant/after-sales?stage=PENDING&keyword=9101"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isArray());
        verify(service).merchantList(null, MerchantAfterSaleStage.PENDING, "9101", 1, 20);
    }

    @Test
    void rejectsConflictingFilters() throws Exception {
        mvc.perform(get("/v1/merchant/after-sales?status=REQUESTED&stage=PENDING"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REFUND_FILTER"));
    }

    @Test
    void exposesCandidateSearch() throws Exception {
        when(service.merchantCandidate("7005")).thenReturn(new MerchantRefundCandidateVO(
                "6007", "6007", "套餐", "PAID", 1, 1000L, true, null, "7005", List.of()));
        mvc.perform(get("/v1/merchant/after-sales/candidate?keyword=7005"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.orderId").value("6007"));
        verify(service).merchantCandidate("7005");
    }

}
