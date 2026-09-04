package com.ray.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SaTokenConfigurationTest {
    @Test
    void threeLoginDomainsUseIndependentTypes() {
        AdminSaTokenConfiguration configuration = new AdminSaTokenConfiguration();

        assertEquals("login", configuration.consumerStpLogic().getLoginType());
        assertEquals("admin", configuration.adminStpLogic().getLoginType());
        assertEquals("merchant", configuration.merchantStpLogic().getLoginType());
    }

    @Test
    void sessionPolicyAndBearerHeaderAreDeclared() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertTrue(yaml.contains("token-name: Authorization"));
        assertTrue(yaml.contains("token-prefix: Bearer"));
        assertTrue(yaml.contains("timeout: 2592000"));
        assertTrue(yaml.contains("active-timeout: 604800"));
        assertTrue(yaml.contains("auto-renew: true"));
        assertTrue(yaml.contains("is-concurrent: true"));
        assertTrue(yaml.contains("is-share: false"));
        assertTrue(yaml.contains("is-read-header: true"));
        assertTrue(yaml.contains("is-read-cookie: false"));
        assertTrue(yaml.contains("is-read-body: false"));
        assertTrue(yaml.contains("logout-range: TOKEN"));
    }

    @Test
    void productionDisablesAllInteractiveDocumentation() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application-prod.yml"));
        assertTrue(yaml.contains("knife4j:"));
        assertTrue(yaml.contains("enable: false"));
        assertTrue(yaml.contains("api-docs:"));
        assertTrue(yaml.contains("swagger-ui:"));
    }
}
