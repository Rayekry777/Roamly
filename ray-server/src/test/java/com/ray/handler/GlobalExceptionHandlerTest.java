package com.ray.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldNotWriteJsonBodyToTimedOutEventStream() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("text/event-stream");

        handler.asyncRequestTimeout(new AsyncRequestTimeoutException(), response);

        assertEquals("", response.getContentAsString());
    }

    @Test
    void shouldReturnServiceUnavailableForOtherAsyncTimeouts() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.asyncRequestTimeout(new AsyncRequestTimeoutException(), response);

        assertEquals(503, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        assertTrue(response.getContentAsString().contains("ASYNC_REQUEST_TIMEOUT"));
    }
}
