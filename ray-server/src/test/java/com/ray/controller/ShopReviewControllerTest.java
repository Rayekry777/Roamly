package com.ray.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ray.handler.GlobalExceptionHandler;
import com.ray.result.PageResult;
import com.ray.service.ShopReviewService;
import com.ray.vo.ShopReviewVO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ShopReviewControllerTest {
    private ShopReviewService reviewService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reviewService = mock(ShopReviewService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ShopReviewController(reviewService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listsReviewsWithStringShopId() throws Exception {
        when(reviewService.list(4L, 1, 10, "LATEST"))
                .thenReturn(new PageResult<>(List.of(), 1, 10, 0));

        mockMvc.perform(get("/v1/shops/4/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
        verify(reviewService).list(4L, 1, 10, "LATEST");
    }

    @Test
    void rejectsInvalidScoreBeforeCallingService() throws Exception {
        mockMvc.perform(post("/v1/shops/4/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":6,\"content\":\"内容\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateAndDeleteUseMeRoutes() throws Exception {
        mockMvc.perform(put("/v1/shops/4/reviews/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":5,\"content\":\"很好\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/v1/shops/4/reviews/me"))
                .andExpect(status().isNoContent());
        verify(reviewService).update(org.mockito.ArgumentMatchers.eq(4L), org.mockito.ArgumentMatchers.any());
        verify(reviewService).delete(4L);
    }
}
