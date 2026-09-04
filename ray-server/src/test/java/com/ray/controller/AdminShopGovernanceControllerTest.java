package com.ray.controller;

import static com.ray.shared.config.MockMvcTestConfiguration.standalone;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ray.service.AdminMerchantGovernanceService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class AdminShopGovernanceControllerTest {
    private final AdminMerchantGovernanceService service = mock(AdminMerchantGovernanceService.class);
    private final MockMvc mvc = standalone(new AdminShopGovernanceController(service));

    @Test
    void rejectsUnsupportedShopStatus() throws Exception {
        mvc.perform(get("/v1/admin/shops").queryParam("status", "ENABLED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void rejectsBlankGovernanceReason() throws Exception {
        mvc.perform(post("/v1/admin/shops/1/suspension")
                        .header("Idempotency-Key", "stage19-suspension")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsMissingGovernanceIdempotencyKey() throws Exception {
        mvc.perform(post("/v1/admin/shops/1/activation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"reason\":\"复核通过\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }
}
