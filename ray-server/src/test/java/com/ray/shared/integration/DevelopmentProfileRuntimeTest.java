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

/** 验证开发配置重建后，种子入驻申请及其私有对象可以真实访问。 */
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
                "roamly:merchant:sms-code:" + PHONE,
                "roamly:merchant:sms-limit:" + PHONE));
    }

    @Test
    void seededPendingApplicationProvidesPrivateLicenseContent() throws Exception {
        ResponseEntity<Void> code = http.postForEntity(
                "/v1/merchant/auth/sms-codes", Map.of("phone", PHONE), Void.class);
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

        ResponseEntity<Void> logout = http.exchange(
                "/v1/merchant/auth/logout", HttpMethod.POST, new HttpEntity<>(headers), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
