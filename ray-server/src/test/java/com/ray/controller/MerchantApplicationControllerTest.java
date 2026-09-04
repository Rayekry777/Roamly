package com.ray.controller;

import static com.ray.shared.config.MockMvcTestConfiguration.standalone;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ray.service.MerchantApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class MerchantApplicationControllerTest {
    private final MerchantApplicationService service = mock(MerchantApplicationService.class);
    private final MockMvc mvc = standalone(new MerchantApplicationController(service));

    @Test
    void rejectsMissingVersionInDraftSnapshot() throws Exception {
        mvc.perform(put("/v1/merchant/application")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsMissingSubmissionIdempotencyKey() throws Exception {
        mvc.perform(post("/v1/merchant/application/submission"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }
}
