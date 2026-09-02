package com.ray.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ray.handler.GlobalExceptionHandler;
import com.ray.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class FeedControllerTest {
    private PostService postService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        postService = mock(PostService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new FeedController(postService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void recommendedFeedRequiresCity() throws Exception {
        mockMvc.perform(get("/v1/feeds/recommended"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void followingFeedDelegatesOpaqueCursor() throws Exception {
        mockMvc.perform(get("/v1/feeds/following")
                        .param("cursor", "1760000000000")
                        .param("offset", "2")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(postService).listFollowingFeed(1_760_000_000_000L, 2, 10);
    }
}
