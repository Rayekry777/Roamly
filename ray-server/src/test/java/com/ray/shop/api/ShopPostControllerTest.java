package com.ray.shop.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ray.controller.ShopPostController;
import com.ray.result.CursorPageResult;
import com.ray.service.PostService;
import com.ray.shared.config.MockMvcTestConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

/** 商户探店动态 Controller 的路径与游标参数测试。 */
class ShopPostControllerTest {
    private PostService postService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        postService = mock(PostService.class);
        mockMvc = MockMvcTestConfiguration.standalone(new ShopPostController(postService));
    }

    @Test
    void listsShopPostsWithStringIdAndCursor() throws Exception {
        when(postService.listShopPosts(4L, 1760000000000L, 2, 10))
                .thenReturn(new CursorPageResult<>(List.of(), 0, 0, false));

        mockMvc.perform(get("/v1/shops/4/posts").param("cursor", "1760000000000").param("offset", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isArray());

        verify(postService).listShopPosts(4L, 1760000000000L, 2, 10);
    }
}
