package com.ray.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 静态资源与 Sa-Token 路由鉴权配置。 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    private final String uploadDir;

    public MvcConfig(@Value("${ray.upload.image-dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/blogs/**")
                .addResourceLocations(
                        Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString())
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
                    if (!isPublic(
                            SaHolder.getRequest().getMethod(),
                            SaHolder.getRequest().getRequestPath())) StpUtil.checkLogin();
                }))
                .addPathPatterns("/v1/**");
    }

    static boolean isPublic(String method, String path) {
        if ("POST".equals(method) && ("/v1/auth/sms-codes".equals(path) || "/v1/auth/sessions".equals(path)))
            return true;
        if (!"GET".equals(method)) return false;
        if (path.equals("/v1/shops")
                || path.matches("/v1/shops/[^/]+")
                || path.matches("/v1/shops/[^/]+/vouchers")) return true;
        if (path.equals("/v1/shop-types")
                || path.equals("/v1/blogs")
                || path.matches("/v1/blogs/[^/]+")
                || path.matches("/v1/blogs/[^/]+/likes")) return true;
        return path.matches("/v1/users/(?!me$)[^/]+")
                || path.matches("/v1/users/(?!me/)[^/]+/profile")
                || path.matches("/v1/users/(?!me/)[^/]+/blogs");
    }
}
