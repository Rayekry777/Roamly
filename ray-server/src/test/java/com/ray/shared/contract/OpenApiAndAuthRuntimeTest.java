package com.ray.shared.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.StpLogic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ray.config.SmsProperties;
import com.ray.exception.BusinessException;
import com.ray.service.CityService;
import com.ray.service.ShopService;
import com.ray.shared.config.IntegrationTest;
import com.ray.utils.cache.CacheNames;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 依赖隔离 MySQL/Redis 的 OpenAPI 与 Sa-Token 最小运行时验收。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@IntegrationTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class OpenApiAndAuthRuntimeTest {
    private final long loginId = 9_000_000_000_000_001L;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SaTokenConfig tokenConfig;

    @Autowired
    private SmsProperties smsProperties;

    @Autowired
    private CityService cityService;

    @Autowired
    private ShopService shopService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    @Qualifier("adminStpLogic")
    private StpLogic adminStpLogic;

    @Autowired
    @Qualifier("merchantStpLogic")
    private StpLogic merchantStpLogic;

    @AfterEach
    void cleanTestLogin() {
        StpUtil.logout(loginId);
        adminStpLogic.logout(loginId);
        merchantStpLogic.logout(loginId);
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
        assertEquals(137, operationIds.size());
        assertEquals(0, document.at("/paths/~1v1~1admin~1auth~1login/post/security").size());
        assertTrue(document.at("/paths/~1v1~1admin~1auth~1login/post/responses/429").isObject());
        assertTrue(document.at("/paths/~1v1~1admin~1auth~1login/post/responses/503").isObject());
        assertTrue(document.at("/paths/~1v1~1admin~1users/get/responses/403").isObject());
        assertTrue(document.at("/paths/~1v1~1admin~1merchant-applications/get/responses/403").isObject());
        assertTrue(document.at("/paths/~1v1~1admin~1merchant-applications~1{applicationId}/get/responses/404").isObject());
        assertTrue(document.at("/paths/~1v1~1admin~1merchant-applications~1{applicationId}~1media~1{mediaId}~1content/get/responses/503").isObject());
        assertTrue(document.at("/paths/~1v1~1admin~1merchant-applications~1{applicationId}~1approval/post/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1admin~1merchant-applications~1{applicationId}~1rejection/post/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1admin~1shops/get/responses/403").isObject());
        assertTrue(document.at("/paths/~1v1~1admin~1shops~1{shopId}/get/responses/404").isObject());
        assertTrue(document.at("/paths/~1v1~1admin~1shops~1{shopId}~1suspension/post/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1admin~1shops~1{shopId}~1activation/post/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1admin~1voucher-reviews/get/responses/403").isObject());
        assertTrue(document.at("/paths/~1v1~1admin~1voucher-reviews~1{productId}/get/responses/404").isObject());
        assertTrue(document.at("/paths/~1v1~1admin~1voucher-reviews~1{productId}~1approval/post/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1admin~1voucher-reviews~1{productId}~1rejection/post/responses/409").isObject());
        assertRequiredParameter(
                document,
                "/v1/admin/merchant-applications/{applicationId}/approval",
                "post",
                "Idempotency-Key");
        assertRequiredParameter(
                document,
                "/v1/admin/merchant-applications/{applicationId}/rejection",
                "post",
                "Idempotency-Key");
        assertRequiredParameter(
                document, "/v1/admin/shops/{shopId}/suspension", "post", "Idempotency-Key");
        assertRequiredParameter(
                document, "/v1/admin/shops/{shopId}/activation", "post", "Idempotency-Key");
        assertRequiredParameter(
                document, "/v1/admin/voucher-reviews/{productId}/approval", "post", "Idempotency-Key");
        assertRequiredParameter(
                document, "/v1/admin/voucher-reviews/{productId}/rejection", "post", "Idempotency-Key");
        assertRequiredParameter(
                document, "/v1/voucher-products/{productId}/orders", "post", "Idempotency-Key");
        assertTrue(document.path("paths").has("/v1/voucher-products/{productId}/order-confirmations"));
        assertEquals(0, document.at("/paths/~1v1~1merchant~1auth~1login/post/security").size());
        assertTrue(document.at("/paths/~1v1~1merchant~1auth~1sms-codes/post/responses/429").isObject());
        assertTrue(document.at("/paths/~1v1~1merchant~1auth~1sms-codes/post/responses/503").isObject());
        assertTrue(document.at("/paths/~1v1~1merchant~1auth~1me/get/responses/401").isObject());
        assertTrue(document.at("/paths/~1v1~1merchant~1application/post/responses/405").isMissingNode());
        assertTrue(document.at("/paths/~1v1~1merchant~1application~1submission/post/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1merchant~1business-media~1images/post/responses/413").isObject());
        assertTrue(document.at("/paths/~1v1~1merchant~1business-media~1images/post/responses/503").isObject());
        assertTrue(document.at("/paths/~1v1~1merchant~1voucher-products/post/responses/403").isObject());
        assertTrue(document.at("/paths/~1v1~1merchant~1voucher-products~1{productId}/put/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1merchant~1voucher-products~1{productId}/put/responses/503").isObject());
        assertTrue(document.at("/paths/~1v1~1merchant~1voucher-products~1{productId}~1copies/post/responses/503").isObject());
        assertTrue(document.at("/paths/~1v1~1merchant~1voucher-products~1{productId}~1submission/post/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1merchant~1voucher-products~1{productId}~1off-sale/post/responses/409").isObject());
        assertRequiredParameter(
                document,
                "/v1/merchant/voucher-products/{productId}/submission",
                "post",
                "Idempotency-Key");
        assertTrue(document.at("/paths/~1v1~1auth~1sessions/post/security").isArray());
        assertEquals(
                0, document.at("/paths/~1v1~1auth~1sessions/post/security").size());
        assertEquals(
                "#/components/schemas/ErrorResult",
                document.at("/paths/~1v1~1users~1me/get/responses/401/content/application~1json/schema/$ref")
                        .asText());
        assertTrue(
                document.at("/paths/~1v1~1media~1images/post/responses/413").isObject());
        assertTrue(
                document.at("/paths/~1v1~1auth~1sms-codes/post/responses/503").isObject());
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
        assertTrue(
                document.at("/paths/~1v1~1shops~1{shopId}~1posts/get/responses/404").isObject());
        assertTrue(document.at("/paths/~1v1~1shops~1{shopId}~1reviews/post/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1voucher-products~1{productId}~1orders/post/responses/409").isObject());
        assertTrue(document.at("/paths/~1v1~1voucher-products~1{productId}~1orders/post/responses/503").isObject());
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
        assertTrue(schemaNames.contains("VoucherProductDetailVO"));
        assertTrue(schemaNames.contains("VoucherOrderCreateDTO"));
        assertTrue(schemaNames.contains("VoucherOrderConfirmationVO"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("VoucherOrderDetailVO"));
        assertTrue(schemaNames.contains("UserVoucherVO"));
        assertTrue(schemaNames.contains("AdminAuditLogVO"));
        assertTrue(schemaNames.contains("CurrentMerchantVO"));
        assertTrue(schemaNames.contains("MerchantShopSummaryVO"));
        assertTrue(schemaNames.contains("MerchantApplicationSaveDTO"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("MerchantApplicationVO"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("BusinessMediaVO"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("MerchantVoucherProductCreateRequest"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("MerchantVoucherProductUpdateRequest"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("MerchantVoucherProductSubmitRequest"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("MerchantVoucherPackageItemRequest"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("MerchantVoucherPackageItemVO"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("MerchantVoucherProductVO"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("MerchantApplicationApprovalRequest"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("MerchantApplicationRejectionRequest"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("ShopGovernanceRequest"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("MerchantApplicationReviewDetailVO"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("MerchantApplicationReviewListItemVO"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("MerchantApplicationReviewResultVO"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("AdminShopDetailVO"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("AdminShopListItemVO"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("AdminShopGovernanceResultVO"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("VoucherReviewApprovalRequest"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("VoucherReviewRejectionRequest"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("MerchantVoucherProductOffSaleRequest"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("AdminVoucherReviewListItemVO"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("AdminVoucherReviewDetailVO"), "schemas=" + schemaNames);
        assertTrue(schemaNames.contains("AdminVoucherReviewResultVO"), "schemas=" + schemaNames);
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
        assertSchemaProperties(document, "MerchantApplicationApprovalRequest", Set.of("version"));
        assertSchemaProperties(document, "MerchantApplicationRejectionRequest", Set.of("version", "reason"));
        assertSchemaProperties(document, "ShopGovernanceRequest", Set.of("version", "reason"));
        assertSchemaProperties(
                document,
                "MerchantApplicationReviewResultVO",
                Set.of(
                        "applicationId",
                        "status",
                        "statusLabel",
                        "decision",
                        "decisionLabel",
                        "reviewerAdminId",
                        "reviewerDisplayName",
                        "reviewedAt",
                        "version",
                        "shop"));
        assertSchemaProperties(
                document,
                "AdminShopGovernanceResultVO",
                Set.of(
                        "shopId",
                        "status",
                        "statusLabel",
                        "version",
                        "reason",
                        "operatedByAdminId",
                        "operatedByAdminName",
                        "operatedAt",
                        "affectedAccountCount"));
        assertSchemaProperties(document, "VoucherReviewApprovalRequest", Set.of("version"));
        assertSchemaProperties(document, "VoucherReviewRejectionRequest", Set.of("version", "reason"));
        assertSchemaProperties(document, "MerchantVoucherProductOffSaleRequest", Set.of("version", "reason"));
        assertRefsResolve(document, document, schemaNames);
    }

    private Set<String> expectedOperations() {
        return Set.of(
                "POST /v1/auth/sms-codes",
                "POST /v1/auth/sessions",
                "DELETE /v1/auth/session",
                "POST /v1/admin/auth/login",
                "GET /v1/admin/auth/me",
                "PUT /v1/admin/auth/password",
                "POST /v1/admin/auth/logout",
                "GET /v1/admin/users",
                "POST /v1/admin/users",
                "GET /v1/admin/users/{adminUserId}",
                "PUT /v1/admin/users/{adminUserId}",
                "POST /v1/admin/users/{adminUserId}/activation",
                "POST /v1/admin/users/{adminUserId}/disablement",
                "POST /v1/admin/users/{adminUserId}/password-reset",
                "GET /v1/admin/merchant-applications",
                "GET /v1/admin/merchant-applications/{applicationId}",
                "GET /v1/admin/merchant-applications/{applicationId}/media/{mediaId}/content",
                "POST /v1/admin/merchant-applications/{applicationId}/approval",
                "POST /v1/admin/merchant-applications/{applicationId}/rejection",
                "GET /v1/admin/shops",
                "GET /v1/admin/shops/{shopId}",
                "POST /v1/admin/shops/{shopId}/suspension",
                "POST /v1/admin/shops/{shopId}/activation",
                "GET /v1/admin/voucher-reviews",
                "GET /v1/admin/voucher-reviews/{productId}",
                "POST /v1/admin/voucher-reviews/{productId}/approval",
                "POST /v1/admin/voucher-reviews/{productId}/rejection",
                "POST /v1/merchant/auth/sms-codes",
                "POST /v1/merchant/auth/login",
                "GET /v1/merchant/auth/me",
                "POST /v1/merchant/auth/logout",
                "POST /v1/merchant/business-media/images",
                "DELETE /v1/merchant/business-media/images/{mediaId}",
                "GET /v1/merchant/business-media/images/{mediaId}/content",
                "GET /v1/merchant/voucher-products",
                "POST /v1/merchant/voucher-products",
                "GET /v1/merchant/voucher-products/{productId}",
                "PUT /v1/merchant/voucher-products/{productId}",
                "DELETE /v1/merchant/voucher-products/{productId}",
                "POST /v1/merchant/voucher-products/{productId}/copies",
                "POST /v1/merchant/voucher-products/{productId}/submission",
                "POST /v1/merchant/voucher-products/{productId}/off-sale",
                "GET /v1/merchant/application",
                "PUT /v1/merchant/application",
                "POST /v1/merchant/application/submission",
                "GET /v1/merchant/reference/cities",
                "GET /v1/merchant/reference/shop-types",
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
                "GET /v1/shops/{shopId}",
                "GET /v1/shops/{shopId}/posts",
                "GET /v1/shops/{shopId}/reviews",
                "POST /v1/shops/{shopId}/reviews",
                "PUT /v1/shops/{shopId}/reviews/me",
                "DELETE /v1/shops/{shopId}/reviews/me",
                "GET /v1/shop-types",
                "GET /v1/feeds/following",
                "GET /v1/shops/{shopId}/voucher-products",
                "GET /v1/voucher-products/{productId}",
                "POST /v1/voucher-products/{productId}/order-confirmations",
                "POST /v1/voucher-products/{productId}/orders",
                "GET /v1/users/me/orders",
                "GET /v1/users/me/orders/{orderId}",
                "DELETE /v1/users/me/orders/{orderId}",
                "GET /v1/users/me/vouchers",
                "GET /v1/users/me/vouchers/{userVoucherId}",
                "GET /v1/users/me/refunds",
                "GET /v1/users/me/refunds/{id}",
                "POST /v1/users/me/vouchers/{voucherId}/refunds",
                "POST /v1/users/me/orders/{orderId}/payments",
                "GET /v1/merchant/staff",
                "POST /v1/merchant/staff-invitations",
                "POST /v1/merchant/staff-invitations/acceptance",
                "POST /v1/merchant/staff-invitations/{id}/revocation",
                "POST /v1/merchant/staff/{id}/activation",
                "POST /v1/merchant/staff/{id}/disablement",
                "POST /v1/merchant/redemptions/previews/by-code",
                "POST /v1/merchant/redemptions/previews/by-qr-token",
                "POST /v1/merchant/redemptions",
                "POST /v1/merchant/redemptions/{id}/reversal",
                "GET /v1/merchant/redemptions",
                "POST /v1/users/me/vouchers/{voucherId}/qr-tokens",
                "GET /v1/admin/refunds",
                "GET /v1/admin/refunds/{id}",
                "POST /v1/admin/refunds/{id}/approval",
                "POST /v1/admin/refunds/{id}/rejection",
                "POST /v1/admin/refunds/{id}/retry",
                "GET /v1/admin/redemptions",
                "GET /v1/admin/redemptions/{id}",
                "GET /v1/admin/commission-rules",
                "PUT /v1/admin/commission-rules",
                "GET /v1/admin/ledger-entries",
                "GET /v1/merchant/finance/summary",
                "GET /v1/admin/settlements",
                "GET /v1/admin/settlements/{id}",
                "POST /v1/admin/settlements/{id}/retry",
                "GET /v1/merchant/settlements",
                "GET /v1/merchant/settlements/{id}",
                "GET /v1/merchant/orders",
                "POST /v1/admin/{resource}/export",
                "POST /v1/admin/event-tickets",
                "GET /v1/admin/events",
                "GET /v1/admin/orders",
                "GET /v1/admin/orders/{id}",
                "GET /v1/admin/audit-logs");
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
    void consumerAdminAndMerchantLoginDomainsDoNotShareTokens() {
        String consumerToken = StpUtil.getStpLogic().createLoginSession(loginId);
        String adminToken = adminStpLogic.createLoginSession(loginId);
        String merchantToken = merchantStpLogic.createLoginSession(loginId);

        assertNotEquals(consumerToken, adminToken);
        assertNotEquals(adminToken, merchantToken);
        assertEquals(null, adminStpLogic.getLoginIdByToken(consumerToken));
        assertEquals(null, merchantStpLogic.getLoginIdByToken(adminToken));
        assertEquals(null, StpUtil.getStpLogic().getLoginIdByToken(merchantToken));
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exchange("/v1/admin/auth/me", "Bearer " + consumerToken, HttpMethod.GET).getStatusCode());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exchange("/v1/users/me", "Bearer " + adminToken, HttpMethod.GET).getStatusCode());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exchange("/v1/merchant/auth/me", "Bearer " + consumerToken, HttpMethod.GET).getStatusCode());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exchange("/v1/merchant/auth/me", "Bearer " + adminToken, HttpMethod.GET).getStatusCode());
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

        ResponseEntity<String> page = http.getForEntity("/v1/shops?cityCode=330100&page=0&size=101", String.class);
        assertEquals(HttpStatus.BAD_REQUEST, page.getStatusCode());
        assertEquals(
                "VALIDATION_FAILED",
                objectMapper.readTree(page.getBody()).path("code").asText());

        ResponseEntity<String> type = http.getForEntity("/v1/shops?cityCode=330100&page=abc", String.class);
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

    @Test
    void springCacheUsesConfiguredRedisTtlAndRestoresCachedNull() {
        long missingShopId = 9_999_999_999_999_999L;
        cacheManager.getCache(CacheNames.CITIES).clear();
        cacheManager.getCache(CacheNames.SHOP_BY_ID).evict(missingShopId);
        try {
            cityService.listEnabledCities();
            Long dictionaryTtl = redis.getExpire("roamly:cache:v1:cities:all", TimeUnit.SECONDS);
            assertTrue(dictionaryTtl != null && dictionaryTtl > 21_000 && dictionaryTtl <= 21_600);

            assertEquals("SHOP_NOT_FOUND", assertThrows(
                    BusinessException.class,
                    () -> shopService.getShop(missingShopId, null, null)).code());
            assertEquals("SHOP_NOT_FOUND", assertThrows(
                    BusinessException.class,
                    () -> shopService.getShop(missingShopId, null, null)).code());
            Long nullTtl = redis.getExpire(
                    "roamly:cache:v1:shop-by-id:" + missingShopId, TimeUnit.SECONDS);
            assertTrue(nullTtl != null && nullTtl > 0 && nullTtl <= 120);
        } finally {
            cacheManager.getCache(CacheNames.CITIES).clear();
            cacheManager.getCache(CacheNames.SHOP_BY_ID).evict(missingShopId);
        }
    }

    @Test
    void disabledSmsModeReturnsDocumentedServiceUnavailableResponse() throws Exception {
        SmsProperties.Mode previous = smsProperties.getMode();
        smsProperties.setMode(SmsProperties.Mode.DISABLED);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = http.postForEntity(
                    "/v1/auth/sms-codes", new HttpEntity<>("{\"phone\":\"13800138000\"}", headers), String.class);
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
            assertEquals(
                    "SMS_SERVICE_UNAVAILABLE",
                    objectMapper.readTree(response.getBody()).path("code").asText());
            ResponseEntity<String> merchantResponse = http.postForEntity(
                    "/v1/merchant/auth/sms-codes",
                    new HttpEntity<>("{\"phone\":\"13900000001\"}", headers),
                    String.class);
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, merchantResponse.getStatusCode());
            assertEquals(
                    "SMS_SERVICE_UNAVAILABLE",
                    objectMapper.readTree(merchantResponse.getBody()).path("code").asText());
        } finally {
            smsProperties.setMode(previous);
        }
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

    private void assertRequiredParameter(
            JsonNode document, String path, String method, String parameterName) {
        JsonNode parameters = document.path("paths").path(path).path(method).path("parameters");
        JsonNode parameter = java.util.stream.StreamSupport.stream(parameters.spliterator(), false)
                .filter(item -> parameterName.equals(item.path("name").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少参数: " + parameterName));
        assertTrue(parameter.path("required").asBoolean(), parameterName + " 必须为必填参数");
        assertEquals("header", parameter.path("in").asText());
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
