package com.ray.config;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.ray.dto.PostCreateDTO;
import com.ray.dto.PostUpdateDTO;
import com.ray.dto.CommentCreateDTO;
import com.ray.result.CursorPageResult;
import com.ray.result.ErrorResult;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.vo.HighlightCommentVO;
import com.ray.vo.CommentThreadVO;
import com.ray.vo.CommentVO;
import com.ray.vo.PostCardVO;
import com.ray.vo.PostDetailVO;
import com.ray.vo.PostMediaVO;
import com.ray.vo.ReviewMediaVO;
import com.ray.vo.ShopSummaryVO;
import com.ray.vo.ShopReviewVO;
import com.ray.vo.VoucherOrderDetailVO;
import com.ray.vo.VoucherOrderVO;
import com.ray.vo.VoucherProductDetailVO;
import com.ray.vo.VoucherProductVO;
import com.ray.vo.UserVoucherVO;
import com.ray.dto.VoucherOrderCreateDTO;
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
        registerReviewSchemas(components);
        registerVoucherSchemas(components);
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
            registerReviewSchemas(openApi.getComponents());
            registerVoucherSchemas(openApi.getComponents());
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                addError(operation.getResponses(), "400", "请求参数错误");
                addError(operation.getResponses(), "500", "服务器内部错误");
                if ((operation.getSecurity() != null && !operation.getSecurity().isEmpty())
                        || isOptionalAuthentication(path)) {
                    addError(operation.getResponses(), "401", "登录无效或已过期");
                }
                if (mayReturnNotFound(method, path)) addError(operation.getResponses(), "404", "资源不存在");
                if (mayReturnForbidden(method, path)) addError(operation.getResponses(), "403", "无权操作该资源");
                if (mayReturnCommentConflict(method, path)) addError(operation.getResponses(), "409", "评论状态冲突");
                if (mayReturnReviewConflict(method, path)) addError(operation.getResponses(), "409", "点评状态冲突或重复点评");
                if (path.matches("/v1/voucher-products/\\{[^/]+}/orders") && method == HttpMethod.POST) {
                    addError(operation.getResponses(), "409", "请求处理中、库存不足、超过限购或订单状态冲突");
                    addError(operation.getResponses(), "503", "订单协调服务暂不可用");
                }
                if (path.matches("/v1/users/me/orders/\\{[^/]+}")
                        && method == HttpMethod.DELETE) addError(operation.getResponses(), "409", "订单状态不允许取消");
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
                if (method == HttpMethod.POST && path.equals("/v1/media/images")) {
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
        registerSchema(components, "PostCreateDTO", PostCreateDTO.class);
        registerSchema(components, "PostUpdateDTO", PostUpdateDTO.class);
        registerSchema(components, "PostMediaVO", PostMediaVO.class);
        registerSchema(components, "HighlightCommentVO", HighlightCommentVO.class);
        registerSchema(components, "ShopSummaryVO", ShopSummaryVO.class);
        registerSchema(components, "PostCardVO", PostCardVO.class);
        registerSchema(components, "PostDetailVO", PostDetailVO.class);
        registerSchema(components, "CommentVO", CommentVO.class);
        registerSchema(components, "CommentThreadVO", CommentThreadVO.class);
        registerSchema(components, "CommentCreateDTO", CommentCreateDTO.class);
    }

    private void registerReviewSchemas(Components components) {
        registerSchema(components, "ShopReviewCreateDTO", com.ray.dto.ShopReviewCreateDTO.class);
        registerSchema(components, "ShopReviewUpdateDTO", com.ray.dto.ShopReviewUpdateDTO.class);
        registerSchema(components, "ReviewMediaVO", ReviewMediaVO.class);
        registerSchema(components, "ShopReviewVO", ShopReviewVO.class);
    }

    private void registerVoucherSchemas(Components components) {
        registerSchema(components, "VoucherOrderCreateDTO", VoucherOrderCreateDTO.class);
        registerSchema(components, "VoucherProductVO", VoucherProductVO.class);
        registerSchema(components, "VoucherProductDetailVO", VoucherProductDetailVO.class);
        registerSchema(components, "VoucherOrderVO", VoucherOrderVO.class);
        registerSchema(components, "VoucherOrderDetailVO", VoucherOrderDetailVO.class);
        registerSchema(components, "UserVoucherVO", UserVoucherVO.class);
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
        if (path.equals("/v1/posts/{postId}/comments")) return true;
        if (path.equals("/v1/comments/{commentId}/replies")) return true;
        if (path.equals("/v1/comments/{commentId}") || path.equals("/v1/comments/{commentId}/like")) return true;
        if (path.equals("/v1/shops/{shopId}/reviews")) return method == HttpMethod.GET || method == HttpMethod.POST;
        if (path.equals("/v1/shops/{shopId}/posts")) return method == HttpMethod.GET;
        if (path.equals("/v1/shops/{shopId}/reviews/me")) return method == HttpMethod.PUT || method == HttpMethod.DELETE;
        if (path.equals("/v1/shops/{shopId}/voucher-products") || path.equals("/v1/voucher-products/{productId}"))
            return method == HttpMethod.GET;
        if (path.equals("/v1/voucher-products/{productId}/orders")) return method == HttpMethod.POST;
        if (path.equals("/v1/users/me/orders/{orderId}") || path.equals("/v1/users/me/vouchers/{userVoucherId}"))
            return true;
        if (path.equals("/v1/users/{userId}/posts")) return method == HttpMethod.GET;
        return false;
    }

    private boolean mayReturnForbidden(HttpMethod method, String path) {
        if (method == HttpMethod.DELETE && path.equals("/v1/media/images/{mediaId}")) return true;
        if (method == HttpMethod.POST && path.equals("/v1/posts")) return true;
        if (method == HttpMethod.DELETE && path.equals("/v1/comments/{commentId}")) return true;
        return path.equals("/v1/posts/{postId}")
                && (method == HttpMethod.PUT || method == HttpMethod.DELETE);
    }

    private boolean mayReturnCommentConflict(HttpMethod method, String path) {
        return path.matches("/v1/posts/\\{postId}/comments") && method == HttpMethod.POST
                || path.matches("/v1/comments/\\{commentId}(/replies|/like)?")
                        && (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE);
    }

    private boolean mayReturnReviewConflict(HttpMethod method, String path) {
        return path.equals("/v1/shops/{shopId}/reviews") && method == HttpMethod.POST
                || path.equals("/v1/shops/{shopId}/reviews/me")
                        && (method == HttpMethod.PUT || method == HttpMethod.DELETE);
    }

    private boolean isOptionalAuthentication(String path) {
        return path.equals("/v1/sections")
                || path.equals("/v1/sections/{sectionId}")
                || path.equals("/v1/sections/{sectionId}/posts")
                || path.equals("/v1/feeds/recommended")
                || path.equals("/v1/posts/{postId}")
                || path.equals("/v1/posts/{postId}/comments")
                || path.equals("/v1/comments/{commentId}/replies")
                || path.equals("/v1/posts/{postId}/likes")
                || path.equals("/v1/users/{userId}/posts");
    }
}
