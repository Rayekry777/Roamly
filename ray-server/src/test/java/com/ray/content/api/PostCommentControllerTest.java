package com.ray.content.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ray.controller.PostCommentController;
import com.ray.result.CursorPageResult;
import com.ray.service.PostCommentService;
import com.ray.shared.config.MockMvcTestConfiguration;
import com.ray.vo.CommentThreadVO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class PostCommentControllerTest {
    private PostCommentService commentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        commentService = mock(PostCommentService.class);
        mockMvc = MockMvcTestConfiguration.standalone(new PostCommentController(commentService));
    }

    @Test
    void listsCommentsWithStringPostIdAndCursor() throws Exception {
        when(commentService.listThreads(9L, "HOT", null, 0, 10))
                .thenReturn(new CursorPageResult<CommentThreadVO>(List.of(), 0, 0, false));

        mockMvc.perform(get("/v1/posts/9/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
        verify(commentService).listThreads(9L, "HOT", null, 0, 10);
    }

    @Test
    void rejectsBlankCommentBodyBeforeCallingService() throws Exception {
        mockMvc.perform(post("/v1/posts/9/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void deleteCommentReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/v1/comments/11"))
                .andExpect(status().isNoContent());
        verify(commentService).delete(11L);
    }
}
