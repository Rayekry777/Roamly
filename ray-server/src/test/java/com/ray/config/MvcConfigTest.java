package com.ray.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MvcConfigTest {
    @Test
    void publicRoutesAreMethodSensitive() {
        assertTrue(MvcConfig.isPublic("GET", "/v1/shops"));
        assertTrue(MvcConfig.isPublic("GET", "/v1/blogs/1"));
        assertTrue(MvcConfig.isPublic("POST", "/v1/auth/sessions"));
        assertTrue(MvcConfig.isPublic("GET", "/v1/users/1/profile"));
        assertTrue(MvcConfig.isPublic("GET", "/v1/users/not-a-number"));
        assertTrue(MvcConfig.isPublic("GET", "/v1/shops/1/vouchers"));
        assertFalse(MvcConfig.isPublic("POST", "/v1/shops"));
        assertFalse(MvcConfig.isPublic("PUT", "/v1/shops/1"));
        assertFalse(MvcConfig.isPublic("GET", "/v1/users/me"));
        assertFalse(MvcConfig.isPublic("GET", "/v1/users/me/blogs"));
        assertFalse(MvcConfig.isPublic("POST", "/v1/auth/session"));
    }
}
