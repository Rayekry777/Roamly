package com.ray.shared.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.ActiveProfiles;

/**
 * 真实基础设施测试的统一配置。
 *
 * <p>所有需要 MySQL/Redis 的测试都通过该注解启用 test Profile；具体差异配置统一
 * 维护在 {@code src/test/resources/application-test.yml}。
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
public @interface IntegrationTest {}
