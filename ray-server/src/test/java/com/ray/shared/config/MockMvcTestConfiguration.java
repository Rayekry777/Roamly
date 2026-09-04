package com.ray.shared.config;

import com.ray.handler.GlobalExceptionHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** 为轻量 Controller 测试构建一致的 standalone MockMvc。 */
public final class MockMvcTestConfiguration {
    private static final Validator VALIDATOR = createValidator();

    private MockMvcTestConfiguration() {}

    /** 创建带全局异常处理和 Jakarta Bean Validation 的独立 MVC 测试客户端。 */
    public static MockMvc standalone(Object... controllers) {
        return MockMvcBuilders.standaloneSetup(controllers)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(VALIDATOR)
                .build();
    }

    private static Validator createValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }
}
