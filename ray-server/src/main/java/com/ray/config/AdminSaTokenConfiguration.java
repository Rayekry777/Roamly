package com.ray.config;

import cn.dev33.satoken.stp.StpLogic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 管理端独立 Sa-Token 登录域。 */
@Configuration
public class AdminSaTokenConfiguration {
    @Bean("adminStpLogic")
    StpLogic adminStpLogic() {
        return new StpLogic("admin");
    }
}
