package com.ray.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档元信息配置。
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Roamly 本地生活服务 API",
                version = "v1.0.0",
                description = "提供商户、探店笔记、优惠券和用户服务接口"
        )
)
public class OpenApiConfig {
}
