package com.ray.controller;

import static com.ray.shared.config.MockMvcTestConfiguration.standalone;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ray.service.AdminMerchantGovernanceService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class AdminMerchantApplicationControllerTest {
    private final AdminMerchantGovernanceService service = mock(AdminMerchantGovernanceService.class);
    private final MockMvc mvc = standalone(new AdminMerchantApplicationController(service));

    @Test
    void rejectsInvalidPaginationShape() throws Exception {
        mvc.perform(get("/v1/admin/merchant-applications")
                        .queryParam("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void rejectsMissingApprovalIdempotencyKey() throws Exception {
        mvc.perform(post("/v1/admin/merchant-applications/9001/approval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void rejectsBlankRejectionReason() throws Exception {
        mvc.perform(post("/v1/admin/merchant-applications/9001/rejection")
                        .header("Idempotency-Key", "stage19-rejection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void returnsPrivateImageHeadersAndBytes() throws Exception {
        when(service.readApplicationMedia("9001", "8001"))
                .thenReturn(new AdminMerchantGovernanceService.AdminMediaContent(
                        new byte[] {1, 2, 3}, "image/png", "营业执照.png"));

        mvc.perform(get("/v1/admin/merchant-applications/9001/media/8001/content"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[] {1, 2, 3}))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=300, private"));
    }
}
