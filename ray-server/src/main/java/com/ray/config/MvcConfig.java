package com.ray.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.ray.service.AdminAuthService;
import com.ray.service.MerchantAuthService;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 静态资源与 Sa-Token 路由鉴权配置。 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    private final String uploadDir;
    private final AdminAuthService adminAuthService;
    private final MerchantAuthService merchantAuthService;

    public MvcConfig(
            @Value("${ray.upload.image-dir}") String uploadDir,
            AdminAuthService adminAuthService,
            MerchantAuthService merchantAuthService) {
        this.uploadDir = uploadDir;
        this.adminAuthService = adminAuthService;
        this.merchantAuthService = merchantAuthService;
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
                    String method = SaHolder.getRequest().getMethod();
                    String path = SaHolder.getRequest().getRequestPath();
                    if (path.startsWith("/v1/admin/")) {
                        if (!isAdminPublic(method, path)) adminAuthService.assertRequestAllowed(method, path);
                    } else if (path.startsWith("/v1/merchant/")) {
                        if (!isMerchantPublic(method, path)) merchantAuthService.assertRequestAllowed(method, path);
                    } else if (isOptionalAuthentication(method, path)) {
                        if (StringUtils.hasText(SaHolder.getRequest().getHeader("Authorization"))) {
                            StpUtil.checkLogin();
                        }
                    } else if (!isPublic(method, path)) {
                        StpUtil.checkLogin();
                    }
                }))
                .addPathPatterns("/v1/**");
    }

    static boolean isPublic(String method, String path) {
        if (isAdminPublic(method, path)) return true;
        if (isMerchantPublic(method, path)) return true;
        if ("POST".equals(method)
                && ("/v1/auth/sms-codes".equals(path)
                        || "/v1/auth/sessions".equals(path)
                        || "/v1/auth/password-sessions".equals(path)
                        || "/v1/auth/registrations".equals(path)))
            return true;
        if (!"GET".equals(method)) return false;
        if (path.equals("/v1/cities")
                || path.equals("/v1/shops")
                || path.matches("/v1/shops/[^/]+")) return true;
        if (path.equals("/v1/shop-types")) return true;
        if (path.matches("/v1/posts/[^/]+/comments")
                || path.matches("/v1/comments/[^/]+/replies")) return true;
        if ("GET".equals(method) && path.matches("/v1/shops/[^/]+/reviews")) return true;
        if ("GET".equals(method) && (path.matches("/v1/shops/[^/]+/voucher-products")
                || path.equals("/v1/voucher-products")
                || path.matches("/v1/voucher-products/[^/]+")
                || path.matches("/v1/voucher-products/[^/]+/media/[^/]+/content"))) return true;
        if ("GET".equals(method) && path.matches("/v1/shops/[^/]+/posts")) return true;
        return path.matches("/v1/users/(?!me$)[^/]+")
                || path.matches("/v1/users/(?!me/)[^/]+/profile");
    }

    static boolean isAdminPublic(String method, String path) {
        return ("POST".equals(method) && "/v1/admin/auth/login".equals(path))
                || ("GET".equals(method) && "/v1/admin/events".equals(path));
    }

    static boolean isMerchantPublic(String method, String path) {
        return "POST".equals(method)
                && ("/v1/merchant/auth/sms-codes".equals(path) || "/v1/merchant/auth/login".equals(path));
    }

    static boolean isOptionalAuthentication(String method, String path) {
        return "GET".equals(method)
                && (path.equals("/v1/sections")
                        || path.matches("/v1/sections/[^/]+")
                        || path.matches("/v1/sections/[^/]+/posts")
                        || path.equals("/v1/feeds/recommended")
                        || path.matches("/v1/posts/[^/]+")
                        || path.matches("/v1/posts/[^/]+/comments")
                        || path.matches("/v1/comments/[^/]+/replies")
                        || path.matches("/v1/posts/[^/]+/likes")
                || path.matches("/v1/users/(?!me/)[^/]+/posts"));
    }
}
