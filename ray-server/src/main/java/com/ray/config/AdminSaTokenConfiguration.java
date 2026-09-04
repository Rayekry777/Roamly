package com.ray.config;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** 消费者、管理端与商户端相互隔离的 Sa-Token 登录域配置。 */
@Configuration
public class AdminSaTokenConfiguration {
    /** 提供 `CONSUMER`（消费者端）默认会话逻辑。 */
    @Bean("consumerStpLogic")
    @Primary
    StpLogic consumerStpLogic() {
        return new StpLogic(StpUtil.TYPE);
    }

    /** 提供 `ADMIN`（管理端）独立会话逻辑。 */
    @Bean("adminStpLogic")
    StpLogic adminStpLogic() {
        return new StpLogic("admin");
    }

    /** 提供阶段 17 将使用的 `MERCHANT`（商户端）独立会话逻辑。 */
    @Bean("merchantStpLogic")
    StpLogic merchantStpLogic() {
        return new StpLogic("merchant");
    }
}
