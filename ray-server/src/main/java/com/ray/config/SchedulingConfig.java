package com.ray.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 启用临时媒体等后台维护任务。 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
