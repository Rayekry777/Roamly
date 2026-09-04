package com.ray.content.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ray.controller.PostController;
import com.ray.service.PostService;
import com.ray.shared.config.MockMvcTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class PostControllerTest {
    private PostService postService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        postService = mock(PostService.class);
        mockMvc = MockMvcTestConfiguration.standalone(new PostController(postService));
    }

    @Test
    void createsPostWithStringIdResponse() throws Exception {
        when(postService.createPost(any())).thenReturn(9_007_199_254_740_993L);

        mockMvc.perform(post("/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "今天在杭州散步",
                                  "mediaIds": [],
                                  "shopVisit": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("9007199254740993"));
    }

    @Test
    void rejectsInvalidCreateRequestBeforeCallingService() throws Exception {
        mockMvc.perform(post("/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "   ",
                                  "mediaIds": [],
                                  "shopVisit": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void deleteLikeReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/v1/posts/9/like"))
                .andExpect(status().isNoContent());

        verify(postService).unlikePost(9L);
    }
}
