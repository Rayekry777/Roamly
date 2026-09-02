package com.ray.config;

import com.ray.utils.LoginInterceptor;
import com.ray.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import java.nio.file.Path;
import java.time.Duration;

import jakarta.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Value("${ray.upload.image-dir}")
    private String uploadDir;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/blogs/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/*",
                        "/shop/of/type",
                        "/shop/of/name",
                        "/shop-type/list",
                        "/voucher/list/**",
                        "/blog/hot",
                        "/blog/{id:[0-9]+}",
                        "/blog/of/user",
                        "/blog/likes/**",
                        "/user/{id:[0-9]+}",
                        "/user/info/{id:[0-9]+}",
                        "/user/code",
                        "/user/login",
                        "/blogs/**",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/favicon.ico",
                        "/error"
                ).order(1);
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
