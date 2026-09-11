package com.ray.shared.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/** 验证开发配置重建后，商户申请审核、私有媒体与门店治理可以真实闭环。 */
@ActiveProfiles("dev")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.data.redis.database=15",
            "ray.storage.local.root=./target/dev-runtime-business-objects"
        })
@EnabledIfEnvironmentVariable(named = "RUN_DEV_RUNTIME_TESTS", matches = "true")
class DevelopmentProfileRuntimeTest {
    private static final String PHONE = "13900000003";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    @AfterEach
    void clearVerificationKeys() {
        redis.delete(List.of(
                "roamly:merchant:sms-code:LOGIN:" + PHONE,
                "roamly:merchant:sms-limit:LOGIN:" + PHONE,
                "roamly:merchant:sms-claim:LOGIN:" + PHONE));
    }

    @Test
    void seededPendingApplicationCanBeReviewedAndItsShopCanBeGoverned() throws Exception {
        ResponseEntity<Void> code = http.postForEntity(
                "/v1/merchant/auth/sms-codes", Map.of("phone", PHONE, "scene", "LOGIN"), Void.class);
        assertThat(code.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> session = http.postForEntity(
                "/v1/merchant/auth/login",
                Map.of("phone", PHONE, "code", "123456"),
                String.class);
        assertThat(session.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = objectMapper.readTree(session.getBody()).path("data").path("accessToken").asText();
        assertThat(token).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> application = http.exchange(
                "/v1/merchant/application", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(application.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode applicationData = objectMapper.readTree(application.getBody()).path("data");
        assertThat(applicationData.path("status").asText()).isEqualTo("PENDING");
        assertThat(applicationData.path("statusLabel").asText()).isEqualTo("审核中");
        assertThat(applicationData.path("licenseMedia").path("byteSize").asLong()).isEqualTo(543L);
        String contentPath = applicationData.path("licenseMedia").path("contentPath").asText();

        ResponseEntity<byte[]> content = http.exchange(
                contentPath, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        assertThat(content.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(content.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(content.getBody()).hasSize(543);

        ResponseEntity<String> initialAdmin = http.postForEntity(
                "/v1/admin/auth/login",
                Map.of("username", "admin", "password", "Roamly123"),
                String.class);
        assertThat(initialAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);
        String initialAdminToken = data(initialAdmin).path("token").asText();
        ResponseEntity<String> passwordChanged = exchange(
                "/v1/admin/auth/password",
                HttpMethod.PUT,
                Map.of("currentPassword", "Roamly123", "newPassword", "AdminPass9"),
                initialAdminToken);
        assertThat(passwordChanged.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> adminSession = http.postForEntity(
                "/v1/admin/auth/login",
                Map.of("username", "admin", "password", "AdminPass9"),
                String.class);
        assertThat(adminSession.getStatusCode()).isEqualTo(HttpStatus.OK);
        String adminToken = data(adminSession).path("token").asText();
        ResponseEntity<String> reviewDetail = exchange(
                "/v1/admin/merchant-applications/9001", HttpMethod.GET, null, adminToken);
        assertThat(reviewDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(reviewDetail).path("contactPhone").asText()).isEqualTo(PHONE);
        String adminContentPath = data(reviewDetail).path("licenseMedia").path("contentPath").asText();
        ResponseEntity<byte[]> adminContent = exchangeBytes(adminContentPath, adminToken);
        assertThat(adminContent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminContent.getBody()).hasSize(543);

        ResponseEntity<String> approved = exchangeCommand(
                "/v1/admin/merchant-applications/9001/approval",
                "stage19-dev-approval",
                Map.of("version", 1),
                adminToken);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(approved).path("status").asText()).isEqualTo("APPROVED");
        String shopId = data(approved).path("shop").path("id").asText();
        assertThat(shopId).isNotBlank();
        assertThat(exchange("/v1/merchant/auth/me", HttpMethod.GET, null, token).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        clearVerificationKeys();
        String activeMerchantToken = loginMerchant();
        assertThat(data(exchange("/v1/merchant/auth/me", HttpMethod.GET, null, activeMerchantToken))
                        .path("status")
                        .asText())
                .isEqualTo("ACTIVE");
        ResponseEntity<String> suspended = exchangeCommand(
                "/v1/admin/shops/" + shopId + "/suspension",
                "stage19-dev-suspend",
                Map.of("version", 0, "reason", "开发环境治理验证"),
                adminToken);
        assertThat(suspended.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(suspended).path("status").asText()).isEqualTo("SUSPENDED");
        assertThat(exchange("/v1/merchant/auth/me", HttpMethod.GET, null, activeMerchantToken).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        clearVerificationKeys();
        String suspendedMerchantToken = loginMerchant();
        ResponseEntity<String> suspendedAccess = exchange(
                "/v1/merchant/orders", HttpMethod.GET, null, suspendedMerchantToken);
        assertThat(suspendedAccess.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(objectMapper.readTree(suspendedAccess.getBody()).path("code").asText())
                .isEqualTo("MERCHANT_SHOP_SUSPENDED");

        ResponseEntity<String> activated = exchangeCommand(
                "/v1/admin/shops/" + shopId + "/activation",
                "stage19-dev-activate",
                Map.of("version", 1, "reason", "开发环境治理验证完成"),
                adminToken);
        assertThat(activated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(activated).path("status").asText()).isEqualTo("ACTIVE");

        clearVerificationKeys();
        String restoredMerchantToken = loginMerchant();
        assertThat(data(exchange("/v1/merchant/auth/me", HttpMethod.GET, null, restoredMerchantToken))
                        .path("status")
                        .asText())
                .isEqualTo("ACTIVE");
    }

    private String loginMerchant() throws Exception {
        ResponseEntity<Void> code = http.postForEntity(
                "/v1/merchant/auth/sms-codes", Map.of("phone", PHONE, "scene", "LOGIN"), Void.class);
        assertThat(code.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> session = http.postForEntity(
                "/v1/merchant/auth/login",
                Map.of("phone", PHONE, "code", "123456"),
                String.class);
        assertThat(session.getStatusCode()).isEqualTo(HttpStatus.OK);
        return data(session).path("accessToken").asText();
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> exchangeCommand(String path, String key, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<byte[]> exchangeBytes(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    private JsonNode data(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody()).path("data");
    }
}
