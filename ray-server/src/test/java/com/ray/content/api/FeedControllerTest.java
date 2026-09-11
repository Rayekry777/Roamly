package com.ray.content.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ray.controller.FeedController;
import com.ray.service.LocationService;
import com.ray.service.PostService;
import com.ray.shared.config.MockMvcTestConfiguration;
import com.ray.vo.LocationContextVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class FeedControllerTest {
    private PostService postService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        postService = mock(PostService.class);
        mockMvc = MockMvcTestConfiguration.standalone(new FeedController(postService));
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

    @Test
    void recommendedFeedUsesServerResolvedCityAndDistrict() throws Exception {
        LocationService locationService = mock(LocationService.class);
        when(locationService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new LocationContextVO(
                        "630100", "西宁", "630105", "城北区", null,
                        "西宁 · 城北区", 101.749746, 36.742782, 30D, "READY"));
        mockMvc = MockMvcTestConfiguration.standalone(new FeedController(postService, locationService));

        mockMvc.perform(get("/v1/feeds/recommended")
                        .param("cityCode", "330100")
                        .param("longitude", "101.749746")
                        .param("latitude", "36.742782"))
                .andExpect(status().isOk());

        verify(postService).listRecommendedFeed("630100", "630105", null, 0, 10);
    }

    @Test
    void recommendedFeedWithoutCoordinatesDoesNotApplyDistrictFilter() throws Exception {
        mockMvc.perform(get("/v1/feeds/recommended")
                        .param("cityCode", "330100"))
                .andExpect(status().isOk());

        verify(postService).listRecommendedFeed("330100", null, null, 0, 10);
    }
}
