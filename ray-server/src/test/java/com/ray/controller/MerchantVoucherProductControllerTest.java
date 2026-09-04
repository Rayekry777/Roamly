package com.ray.controller;

import static com.ray.shared.config.MockMvcTestConfiguration.standalone;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ray.service.MerchantVoucherProductService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class MerchantVoucherProductControllerTest {
    private final MerchantVoucherProductService service = mock(MerchantVoucherProductService.class);
    private final MockMvc mvc = standalone(new MerchantVoucherProductController(service));

    @Test
    void rejectsMissingProductTypeWhenCreatingDraft() throws Exception {
        mvc.perform(post("/v1/merchant/voucher-products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsIncompleteUpdateSnapshot() throws Exception {
        mvc.perform(put("/v1/merchant/voucher-products/3101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsMissingSubmissionIdempotencyKey() throws Exception {
        mvc.perform(post("/v1/merchant/voucher-products/3101/submission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }
}
