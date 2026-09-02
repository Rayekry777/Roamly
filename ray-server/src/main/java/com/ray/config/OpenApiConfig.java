package com.ray.config;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.ray.dto.PostCreateDTO;
import com.ray.dto.PostUpdateDTO;
import com.ray.result.CursorPageResult;
import com.ray.result.ErrorResult;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.vo.HighlightCommentVO;
import com.ray.vo.PostCardVO;
import com.ray.vo.PostDetailVO;
import com.ray.vo.PostMediaVO;
import com.ray.vo.ShopSummaryVO;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 元信息、Bearer 安全方案及通用错误响应配置。 */
@Configuration
public class OpenApiConfig {
    public static final String SECURITY_SCHEME = "BearerAuth";

    @Bean
    OpenAPI roamlyOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(
                        SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("opaque"));
        registerSchema(components, "Result", Result.class);
        registerSchema(components, "ErrorResult", ErrorResult.class);
        registerSchema(components, "PageResult", PageResult.class);
        registerSchema(components, "CursorPageResult", CursorPageResult.class);
        registerPostSchemas(components);
        return new OpenAPI()
                .info(new Info()
                        .title("Roamly 本地生活服务 API")
                        .version("1.0.0")
                        .description("Roamly 小程序使用的版本化 HTTP API"))
                .servers(List.of(new Server().url("/api").description("Nginx 对外 API 前缀")))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }

    @Bean
    OpenApiCustomizer errorResponses() {
        return openApi -> {
            registerSchema(openApi.getComponents(), "Result", Result.class);
            registerSchema(openApi.getComponents(), "ErrorResult", ErrorResult.class);
            registerSchema(openApi.getComponents(), "PageResult", PageResult.class);
            registerSchema(openApi.getComponents(), "CursorPageResult", CursorPageResult.class);
            registerPostSchemas(openApi.getComponents());
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                addError(operation.getResponses(), "400", "请求参数错误");
                addError(operation.getResponses(), "500", "服务器内部错误");
                if ((operation.getSecurity() != null && !operation.getSecurity().isEmpty())
                        || isOptionalAuthentication(path)) {
                    addError(operation.getResponses(), "401", "登录无效或已过期");
                }
                if (mayReturnNotFound(method, path)) addError(operation.getResponses(), "404", "资源不存在");
                if (mayReturnForbidden(method, path)) addError(operation.getResponses(), "403", "无权操作该资源");
                if (method == HttpMethod.POST && path.matches("/v1/seckill-vouchers/\\{[^/]+}/orders")) {
                    addError(operation.getResponses(), "409", "库存不足或重复下单");
                }
                if ((method == HttpMethod.POST && path.equals("/v1/posts"))
                        || (method == HttpMethod.PUT && path.equals("/v1/posts/{postId}"))) {
                    addError(operation.getResponses(), "409", "媒体已绑定或动态状态冲突");
                }
                if ((method == HttpMethod.DELETE && path.equals("/v1/posts/{postId}"))
                        || ((method == HttpMethod.PUT || method == HttpMethod.DELETE)
                                && path.equals("/v1/posts/{postId}/like"))) {
                    addError(operation.getResponses(), "409", "动态状态冲突");
                }
                if (method == HttpMethod.DELETE && path.equals("/v1/media/images/{mediaId}")) {
                    addError(operation.getResponses(), "409", "媒体已经绑定业务");
                }
                if (method == HttpMethod.POST
                        && (path.equals("/v1/blog-images") || path.equals("/v1/media/images"))) {
                    addError(operation.getResponses(), "413", "文件过大");
                }
            }));
        };
    }

    private void registerSchema(Components components, String name, Class<?> type) {
        ResolvedSchema resolvedSchema = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(false));
        resolvedSchema.referencedSchemas.forEach(components::addSchemas);
        components.addSchemas(name, resolvedSchema.schema);
    }

    private void registerPostSchemas(Components components) {
        registerSchema(components, "PostCreateRequest", PostCreateDTO.class);
        registerSchema(components, "PostUpdateRequest", PostUpdateDTO.class);
        registerSchema(components, "PostMediaVO", PostMediaVO.class);
        registerSchema(components, "HighlightCommentVO", HighlightCommentVO.class);
        registerSchema(components, "ShopSummaryVO", ShopSummaryVO.class);
        registerSchema(components, "PostCardVO", PostCardVO.class);
        registerSchema(components, "PostDetailVO", PostDetailVO.class);
    }

    private void addError(ApiResponses responses, String status, String description) {
        responses.addApiResponse(
                status,
                new ApiResponse()
                        .description(description)
                        .content(new Content()
                                .addMediaType(
                                        APPLICATION_JSON_VALUE,
                                        new MediaType()
                                                .schema(new Schema<>()
                                                        .$ref("#/components/schemas/ErrorResult")))));
    }

    private boolean mayReturnNotFound(HttpMethod method, String path) {
        if (path.matches("/v1/users/\\{userId}/profile") && method == HttpMethod.GET) return true;
        if (path.matches("/v1/users/\\{userId}") && method == HttpMethod.GET) return true;
        if (path.matches("/v1/shops/\\{shopId}") && (method == HttpMethod.GET || method == HttpMethod.PUT))
            return true;
        if (path.equals("/v1/sections/{sectionId}")
                || path.equals("/v1/sections/{sectionId}/posts")) {
            return method == HttpMethod.GET;
        }
        if (path.equals("/v1/feeds/recommended")) return method == HttpMethod.GET;
        if (path.equals("/v1/users/me/section-follows/{sectionId}"))
            return method == HttpMethod.PUT || method == HttpMethod.DELETE;
        if (path.equals("/v1/media/images/{mediaId}")) return method == HttpMethod.DELETE;
        if (path.equals("/v1/posts")) return method == HttpMethod.POST;
        if (path.equals("/v1/posts/{postId}"))
            return method == HttpMethod.GET || method == HttpMethod.PUT || method == HttpMethod.DELETE;
        if (path.equals("/v1/posts/{postId}/like"))
            return method == HttpMethod.PUT || method == HttpMethod.DELETE;
        if (path.equals("/v1/posts/{postId}/likes")) return method == HttpMethod.GET;
        if (path.equals("/v1/users/{userId}/posts")) return method == HttpMethod.GET;
        return path.matches("/v1/blogs/\\{blogId}(/like|/likes)?")
                && (method == HttpMethod.GET || method == HttpMethod.PUT || method == HttpMethod.DELETE);
    }

    private boolean mayReturnForbidden(HttpMethod method, String path) {
        if (method == HttpMethod.DELETE && path.equals("/v1/media/images/{mediaId}")) return true;
        if (method == HttpMethod.POST && path.equals("/v1/posts")) return true;
        return path.equals("/v1/posts/{postId}")
                && (method == HttpMethod.PUT || method == HttpMethod.DELETE);
    }

    private boolean isOptionalAuthentication(String path) {
        return path.equals("/v1/sections")
                || path.equals("/v1/sections/{sectionId}")
                || path.equals("/v1/sections/{sectionId}/posts")
                || path.equals("/v1/feeds/recommended")
                || path.equals("/v1/posts/{postId}")
                || path.equals("/v1/posts/{postId}/likes")
                || path.equals("/v1/users/{userId}/posts");
    }
}
