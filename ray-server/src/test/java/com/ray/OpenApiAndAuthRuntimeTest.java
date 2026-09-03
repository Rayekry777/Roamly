package com.ray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 依赖隔离 MySQL/Redis 的 OpenAPI 与 Sa-Token 最小运行时验收。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=test", "spring.sql.init.mode=never"})
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class OpenApiAndAuthRuntimeTest {
    private final long loginId = 9_000_000_000_000_001L;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SaTokenConfig tokenConfig;

    @AfterEach
    void cleanTestLogin() {
        StpUtil.logout(loginId);
    }

    @Test
    void openApiDeclaresV1OpaqueBearerAndResolvableSchemas() throws Exception {
        ResponseEntity<String> response = http.getForEntity("/v3/api-docs", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode document = objectMapper.readTree(response.getBody());
        JsonNode bearer = document.at("/components/securitySchemes/BearerAuth");
        assertEquals("http", bearer.path("type").asText());
        assertEquals("bearer", bearer.path("scheme").asText());
        assertEquals("opaque", bearer.path("bearerFormat").asText());
        assertEquals("/api", document.at("/servers/0/url").asText());

        Set<String> operationIds = new HashSet<>();
        Set<String> operations = new HashSet<>();
        document.path("paths")
                .properties()
                .forEach(path -> path.getValue().properties().forEach(operation -> {
                    JsonNode id = operation.getValue().path("operationId");
                    if (!id.isMissingNode())
                        assertTrue(operationIds.add(id.asText()), "operationId 必须唯一: " + id.asText());
                    operations.add(operation.getKey().toUpperCase() + " " + path.getKey());
                    assertTrue(
                            operation.getValue().path("responses").path("400").isObject());
                    assertTrue(
                            operation.getValue().path("responses").path("500").isObject());
                }));
        assertEquals(expectedOperations(), operations);
        assertTrue(document.at("/paths/~1v1~1auth~1sessions/post/security").isArray());
        assertEquals(
                0, document.at("/paths/~1v1~1auth~1sessions/post/security").size());
        assertEquals(
                "#/components/schemas/ErrorResult",
                document.at("/paths/~1v1~1users~1me/get/responses/401/content/application~1json/schema/$ref")
                        .asText());
        assertTrue(document.at("/paths/~1v1~1seckill-vouchers~1{voucherId}~1orders/post/responses/409")
                .isObject());
        assertTrue(
                document.at("/paths/~1v1~1blog-images/post/responses/413").isObject());
        assertTrue(
                document.at("/paths/~1v1~1media~1images/post/responses/413").isObject());
        assertTrue(
                document.at("/paths/~1v1~1media~1images~1{mediaId}/delete/responses/403").isObject());
        assertTrue(
                document.at("/paths/~1v1~1media~1images~1{mediaId}/delete/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1posts/post/responses/403").isObject());
        assertTrue(document.at("/paths/~1v1~1posts/post/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1posts~1{postId}/put/responses/403").isObject());
        assertTrue(document.at("/paths/~1v1~1posts~1{postId}/put/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1posts~1{postId}/delete/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1posts~1{postId}~1comments/post/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1comments~1{commentId}/delete/responses/403").isObject());
        assertTrue(document.at("/paths/~1v1~1comments~1{commentId}~1like/put/security").isArray());
        assertTrue(document.at("/paths/~1v1~1shops~1{shopId}/posts/get/responses/404").isObject());
        assertTrue(document.at("/paths/~1v1~1shops~1{shopId}/reviews/post/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1voucher-products~1{productId}/orders/post/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1users~1me~1orders~1{orderId}/delete/responses/409").isObject());
        assertParameterType(document, "/v1/shops/{shopId}", "get", "longitude", "number");
        java.util.List<String> schemaNames = new ArrayList<>();
        document.at("/components/schemas").properties().forEach(entry -> schemaNames.add(entry.getKey()));
        assertTrue(schemaNames.contains("Result"));
        assertTrue(schemaNames.contains("ErrorResult"));
        assertTrue(schemaNames.contains("PageResult"));
        assertTrue(schemaNames.contains("CursorPageResult"));
        assertTrue(schemaNames.contains("PostCreateDTO"));
        assertTrue(schemaNames.contains("PostDetailVO"));
        assertTrue(schemaNames.contains("CommentCreateDTO"));
        assertTrue(schemaNames.contains("CommentVO"));
        assertTrue(schemaNames.contains("CommentThreadVO"));
        assertTrue(schemaNames.contains("ShopReviewCreateDTO"));
        assertTrue(schemaNames.contains("ShopReviewVO"));
        assertTrue(schemaNames.contains("VoucherProductVO"));
        assertTrue(schemaNames.contains("VoucherOrderCreateDTO"));
        assertTrue(schemaNames.contains("UserVoucherVO"));
        assertFalse(schemaNames.contains("PostCreateRequest"));
        assertFalse(schemaNames.contains("PostUpdateRequest"));
        assertFalse(schemaNames.contains("ApiResponse"));
        assertFalse(schemaNames.contains("ApiErrorResponse"));
        assertSchemaProperties(document, "Result", Set.of("code", "message", "data"));
        assertSchemaProperties(document, "ErrorResult", Set.of("code", "message", "fieldErrors"));
        assertSchemaProperties(document, "PageResult", Set.of("items", "page", "size", "total"));
        assertSchemaProperties(
                document,
                "CursorPageResult",
                Set.of("items", "nextCursor", "nextOffset", "hasMore"));
        assertSchemaProperties(document, "CommentCreateDTO", Set.of("content"));
        assertSchemaProperties(document, "CommentVO", Set.of("id", "rootId", "author", "replyToUser", "content", "deleted", "postAuthor", "likedCount", "likedByMe", "deletable", "createdTime"));
        assertSchemaProperties(document, "CommentThreadVO", Set.of("root", "previewReplies", "replyCount", "hasMoreReplies", "nextReplyCursor", "nextReplyOffset"));
        assertRefsResolve(document, document, schemaNames);
    }

    private Set<String> expectedOperations() {
        return Set.of(
                "POST /v1/auth/sms-codes",
                "POST /v1/auth/sessions",
                "DELETE /v1/auth/session",
                "GET /v1/cities",
                "GET /v1/sections",
                "GET /v1/sections/{sectionId}",
                "GET /v1/sections/{sectionId}/posts",
                "PUT /v1/users/me/section-follows/{sectionId}",
                "DELETE /v1/users/me/section-follows/{sectionId}",
                "POST /v1/media/images",
                "DELETE /v1/media/images/{mediaId}",
                "POST /v1/posts",
                "GET /v1/posts/{postId}",
                "PUT /v1/posts/{postId}",
                "DELETE /v1/posts/{postId}",
                "GET /v1/users/me/posts",
                "GET /v1/users/{userId}/posts",
                "PUT /v1/posts/{postId}/like",
                "DELETE /v1/posts/{postId}/like",
                "GET /v1/posts/{postId}/likes",
                "GET /v1/posts/{postId}/comments",
                "POST /v1/posts/{postId}/comments",
                "GET /v1/comments/{commentId}/replies",
                "POST /v1/comments/{commentId}/replies",
                "DELETE /v1/comments/{commentId}",
                "PUT /v1/comments/{commentId}/like",
                "DELETE /v1/comments/{commentId}/like",
                "GET /v1/feeds/recommended",
                "GET /v1/users/me",
                "GET /v1/users/{userId}",
                "GET /v1/users/{userId}/profile",
                "PUT /v1/users/me/check-ins/today",
                "GET /v1/users/me/check-ins/streak",
                "PUT /v1/users/me/following/{userId}",
                "DELETE /v1/users/me/following/{userId}",
                "GET /v1/users/me/following/{userId}",
                "GET /v1/users/{userId}/common-following",
                "GET /v1/shops",
                "POST /v1/shops",
                "GET /v1/shops/{shopId}",
                "PUT /v1/shops/{shopId}",
                "GET /v1/shops/{shopId}/posts",
                "GET /v1/shops/{shopId}/reviews",
                "POST /v1/shops/{shopId}/reviews",
                "PUT /v1/shops/{shopId}/reviews/me",
                "DELETE /v1/shops/{shopId}/reviews/me",
                "GET /v1/shop-types",
                "GET /v1/blogs",
                "POST /v1/blogs",
                "GET /v1/blogs/{blogId}",
                "GET /v1/blogs/{blogId}/likes",
                "PUT /v1/blogs/{blogId}/like",
                "DELETE /v1/blogs/{blogId}/like",
                "GET /v1/users/me/blogs",
                "GET /v1/users/{userId}/blogs",
                "GET /v1/feeds/following",
                "POST /v1/vouchers",
                "POST /v1/seckill-vouchers",
                "GET /v1/shops/{shopId}/vouchers",
                "POST /v1/seckill-vouchers/{voucherId}/orders",
                "GET /v1/shops/{shopId}/voucher-products",
                "GET /v1/voucher-products/{productId}",
                "POST /v1/voucher-products/{productId}/orders",
                "GET /v1/users/me/orders",
                "GET /v1/users/me/orders/{orderId}",
                "DELETE /v1/users/me/orders/{orderId}",
                "GET /v1/users/me/vouchers",
                "GET /v1/users/me/vouchers/{userVoucherId}",
                "POST /v1/blog-images",
                "DELETE /v1/blog-images");
    }

    @Test
    void knife4jIsAvailableAndSwaggerUiIsDisabled() {
        assertTrue(http.getForEntity("/doc.html", String.class).getStatusCode().is2xxSuccessful());
        assertEquals(
                HttpStatus.NOT_FOUND,
                http.getForEntity("/swagger-ui/index.html", String.class).getStatusCode());
    }

    @Test
    void bearerPolicySupportsIndependentTokensAndCurrentTokenLogout() {
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                http.getForEntity("/v1/users/me", String.class).getStatusCode());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exchange("/v1/users/me", "Token invalid", HttpMethod.GET).getStatusCode());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exchange("/v1/users/me", "Bearer invalid", HttpMethod.GET).getStatusCode());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exchange("/v1/sections", "Bearer invalid", HttpMethod.GET).getStatusCode());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exchange(
                                "/v1/feeds/recommended?cityCode=330100",
                                "Bearer invalid",
                                HttpMethod.GET)
                        .getStatusCode());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exchange("/v1/sections/1/posts", "Bearer invalid", HttpMethod.GET)
                        .getStatusCode());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exchange("/v1/posts/1", "Bearer invalid", HttpMethod.GET).getStatusCode());

        String first = StpUtil.getStpLogic().createLoginSession(loginId);
        String second = StpUtil.getStpLogic().createLoginSession(loginId);
        assertNotEquals(first, second);
        assertEquals(
                HttpStatus.NO_CONTENT,
                exchange("/v1/auth/session", "Bearer " + first, HttpMethod.DELETE)
                        .getStatusCode());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exchange("/v1/auth/session", "Bearer " + first, HttpMethod.DELETE)
                        .getStatusCode());
        assertEquals(
                HttpStatus.NO_CONTENT,
                exchange("/v1/auth/session", "Bearer " + second, HttpMethod.DELETE)
                        .getStatusCode());
        assertTrue(SaManager.getSaTokenDao() instanceof SaTokenDaoForRedisTemplate);
    }

    @Test
    void sessionTimeoutsAndHeaderOnlyPolicyAreBound() {
        assertEquals(2_592_000L, tokenConfig.getTimeout());
        assertEquals(604_800L, tokenConfig.getActiveTimeout());
        assertEquals("Authorization", tokenConfig.getTokenName());
        assertEquals("Bearer", tokenConfig.getTokenPrefix());
        assertEquals("uuid", tokenConfig.getTokenStyle());
        assertTrue(tokenConfig.getAutoRenew());
        assertTrue(tokenConfig.getIsConcurrent());
        assertFalse(tokenConfig.getIsShare());
        assertTrue(tokenConfig.getIsReadHeader());
        assertFalse(tokenConfig.getIsReadCookie());
        assertFalse(tokenConfig.getIsReadBody());
    }

    @Test
    void publicValidationUsesUnifiedErrorResult() throws Exception {
        ResponseEntity<String> feed =
                http.getForEntity("/v1/feeds/recommended?cityCode=330100&size=21", String.class);
        assertEquals(HttpStatus.BAD_REQUEST, feed.getStatusCode());
        assertEquals(
                "VALIDATION_FAILED",
                objectMapper.readTree(feed.getBody()).path("code").asText());

        ResponseEntity<String> sectionFeed =
                http.getForEntity("/v1/sections/1/posts?size=21", String.class);
        assertEquals(HttpStatus.BAD_REQUEST, sectionFeed.getStatusCode());
        assertEquals(
                "VALIDATION_FAILED",
                objectMapper.readTree(sectionFeed.getBody()).path("code").asText());

        ResponseEntity<String> page = http.getForEntity("/v1/blogs?page=0&size=101", String.class);
        assertEquals(HttpStatus.BAD_REQUEST, page.getStatusCode());
        assertEquals(
                "VALIDATION_FAILED",
                objectMapper.readTree(page.getBody()).path("code").asText());

        ResponseEntity<String> type = http.getForEntity("/v1/blogs?page=abc", String.class);
        assertEquals(HttpStatus.BAD_REQUEST, type.getStatusCode());
        assertEquals(
                "INVALID_PARAMETER",
                objectMapper.readTree(type.getBody()).path("code").asText());

        ResponseEntity<String> id = http.getForEntity("/v1/users/not-a-number", String.class);
        assertEquals(HttpStatus.BAD_REQUEST, id.getStatusCode());
        assertEquals(
                "INVALID_ID", objectMapper.readTree(id.getBody()).path("code").asText());
    }

    private ResponseEntity<String> exchange(String path, String authorization, HttpMethod method) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        return http.exchange(path, method, new HttpEntity<>(headers), String.class);
    }

    private void assertSchemaProperties(JsonNode document, String schemaName, Set<String> expectedProperties) {
        Set<String> actualProperties = new HashSet<>();
        document.at("/components/schemas/" + schemaName + "/properties")
                .properties()
                .forEach(property -> actualProperties.add(property.getKey()));
        assertEquals(expectedProperties, actualProperties, schemaName + " 字段必须保持稳定");
    }

    private void assertParameterType(
            JsonNode document, String path, String method, String parameterName, String expectedType) {
        JsonNode parameters = document.path("paths").path(path).path(method).path("parameters");
        JsonNode parameter = java.util.stream.StreamSupport.stream(parameters.spliterator(), false)
                .filter(item -> parameterName.equals(item.path("name").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少参数: " + parameterName));
        assertEquals(expectedType, parameter.path("schema").path("type").asText());
    }

    private void assertRefsResolve(JsonNode root, JsonNode node, java.util.List<String> schemaNames) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                if ("$ref".equals(field.getKey())) {
                    String ref = field.getValue().asText();
                    assertTrue(ref.startsWith("#/components/schemas/"), "不支持的引用: " + ref);
                    assertFalse(
                            root.at(ref.substring(1)).isMissingNode(),
                            "Schema 引用不存在: " + ref + "，现有 Schema: " + schemaNames);
                } else {
                    assertRefsResolve(root, field.getValue(), schemaNames);
                }
            }
        } else if (node.isArray()) {
            node.forEach(child -> assertRefsResolve(root, child, schemaNames));
        }
    }
}
