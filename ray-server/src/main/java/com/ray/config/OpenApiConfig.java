package com.ray.config;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.ray.dto.PostCreateDTO;
import com.ray.dto.PostUpdateDTO;
import com.ray.dto.CommentCreateDTO;
import com.ray.dto.BusinessDayHoursDTO;
import com.ray.dto.BusinessPeriodDTO;
import com.ray.dto.MerchantApplicationSaveDTO;
import com.ray.result.CursorPageResult;
import com.ray.result.ErrorResult;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.vo.HighlightCommentVO;
import com.ray.vo.CommentThreadVO;
import com.ray.vo.CommentVO;
import com.ray.vo.CurrentMerchantVO;
import com.ray.vo.BusinessMediaVO;
import com.ray.vo.AdminShopDetailVO;
import com.ray.vo.AdminShopGovernanceResultVO;
import com.ray.vo.AdminShopListItemVO;
import com.ray.vo.MerchantApplicationVO;
import com.ray.vo.MerchantApplicationReviewDetailVO;
import com.ray.vo.MerchantApplicationReviewListItemVO;
import com.ray.vo.MerchantApplicationReviewResultVO;
import com.ray.vo.MerchantShopSummaryVO;
import com.ray.vo.PostCardVO;
import com.ray.vo.PostDetailVO;
import com.ray.vo.PostMediaVO;
import com.ray.vo.ReviewMediaVO;
import com.ray.vo.ShopSummaryVO;
import com.ray.vo.ShopReviewVO;
import com.ray.vo.VoucherOrderDetailVO;
import com.ray.vo.VoucherOrderConfirmationVO;
import com.ray.vo.VoucherOrderVO;
import com.ray.vo.VoucherPaymentVO;
import com.ray.vo.VoucherProductDetailVO;
import com.ray.vo.VoucherProductVO;
import com.ray.vo.VoucherQrTokenVO;
import com.ray.vo.VoucherRedemptionPreviewVO;
import com.ray.vo.VoucherRedemptionVO;
import com.ray.vo.VoucherRefundVO;
import com.ray.vo.UserVoucherVO;
import com.ray.vo.AdminVoucherReviewDetailVO;
import com.ray.vo.AdminVoucherReviewListItemVO;
import com.ray.vo.AdminVoucherReviewResultVO;
import com.ray.vo.MerchantVoucherPackageItemVO;
import com.ray.vo.MerchantVoucherProductVO;
import com.ray.vo.VoucherProductSectionVO;
import com.ray.vo.VoucherProductTagVO;
import com.ray.vo.VoucherProductCashRuleVO;
import com.ray.vo.VoucherProductDiscountRuleVO;
import com.ray.vo.VoucherProductMultiUseRuleVO;
import com.ray.dto.VoucherProductDetailDTO;
import com.ray.dto.VoucherProductTagDTO;
import com.ray.dto.VoucherProductCashRuleDTO;
import com.ray.dto.VoucherProductDiscountRuleDTO;
import com.ray.dto.VoucherProductMultiUseRuleDTO;
import com.ray.dto.VoucherOrderCreateDTO;
import com.ray.dto.VoucherPaymentDTO;
import com.ray.dto.VoucherRefundDTO;
import com.ray.dto.ConsumerRefundDTO;
import com.ray.dto.MerchantRefundDTO;
import com.ray.dto.AdminRefundDTO;
import com.ray.dto.MerchantStaffAcceptanceDTO;
import com.ray.dto.MerchantStaffInvitationCreateDTO;
import com.ray.dto.VoucherRedemptionConfirmDTO;
import com.ray.dto.VoucherRedemptionPreviewDTO;
import com.ray.dto.VoucherRedemptionReversalDTO;
import com.ray.dto.VoucherQrTokenPreviewDTO;
import com.ray.dto.VoucherReviewApprovalDTO;
import com.ray.dto.VoucherReviewRejectionDTO;
import com.ray.dto.MerchantVoucherProductOffSaleDTO;
import com.ray.dto.CommissionRuleUpdateDTO;
import com.ray.dto.RegistrationDTO;
import com.ray.dto.PasswordLoginDTO;
import com.ray.dto.UserProfileUpdateDTO;
import com.ray.dto.NicknameUpdateDTO;
import com.ray.dto.AvatarUpdateDTO;
import com.ray.dto.PhoneChangeDTO;
import com.ray.dto.PhoneSmsCodeDTO;
import com.ray.dto.PasswordChangeDTO;
import com.ray.dto.SmsCodeDTO;
import com.ray.vo.AdminEventTicketVO;
import com.ray.vo.AdminAuditLogVO;
import com.ray.vo.CurrentUserProfileVO;
import com.ray.vo.PublicUserProfileVO;
import com.ray.vo.CommissionRuleVO;
import com.ray.vo.FundLedgerEntryVO;
import com.ray.vo.MerchantFinanceSummaryVO;
import com.ray.vo.MerchantStaffInvitationVO;
import com.ray.vo.MerchantStaffVO;
import com.ray.vo.SettlementBatchVO;
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
        registerMerchantSchemas(components);
        registerAdminMerchantGovernanceSchemas(components);
        registerVoucherReviewSchemas(components);
        registerStage23To29Schemas(components);
        registerSchema(components, "AdminAuditLogVO", AdminAuditLogVO.class);
        registerConsumerAccountSchemas(components);
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
            registerMerchantSchemas(openApi.getComponents());
            registerAdminMerchantGovernanceSchemas(openApi.getComponents());
            registerVoucherReviewSchemas(openApi.getComponents());
            registerStage23To29Schemas(openApi.getComponents());
            registerSchema(openApi.getComponents(), "AdminAuditLogVO", AdminAuditLogVO.class);
            registerConsumerAccountSchemas(openApi.getComponents());
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                if (path.equals("/v1/admin/events") && method == HttpMethod.GET) {
                    operation.setSecurity(List.of());
                }
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
                if (path.startsWith("/v1/admin/")
                        && !path.equals("/v1/admin/auth/login")
                        && !path.equals("/v1/admin/events")) {
                    addError(operation.getResponses(), "403", "管理员权限不足或必须先修改密码");
                }
                if (path.equals("/v1/admin/auth/login")) {
                    addError(operation.getResponses(), "401", "用户名、密码或账号状态无效");
                    addError(operation.getResponses(), "429", "管理员登录失败次数过多");
                    addError(operation.getResponses(), "503", "管理员认证依赖服务暂不可用");
                }
                if (path.startsWith("/v1/auth/") || path.startsWith("/v1/users/me/")) {
                    addError(operation.getResponses(), "409", "账号、验证码或状态冲突");
                    addError(operation.getResponses(), "429", "操作过于频繁或密码登录已受限");
                }
                if (path.matches("/v1/admin/users/\\{[^/]+}(/activation|/disablement|/password-reset)?")) {
                    addError(operation.getResponses(), "404", "管理员账号不存在");
                    addError(operation.getResponses(), "409", "管理员状态冲突或最后平台管理员保护");
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
                if (method == HttpMethod.POST && path.equals("/v1/media/images")) {
                    addError(operation.getResponses(), "413", "文件过大");
                }
                if (path.startsWith("/v1/merchant/business-media/images")) {
                    addError(operation.getResponses(), "403", "经营媒体不属于当前商户或账号不可编辑");
                    addError(operation.getResponses(), "404", "经营媒体不存在");
                    addError(operation.getResponses(), "409", "经营媒体已绑定或已过期");
                    if (method == HttpMethod.POST) addError(operation.getResponses(), "413", "文件过大");
                    if (method == HttpMethod.POST || method == HttpMethod.GET) {
                        addError(operation.getResponses(), "503", "对象存储暂不可用");
                    }
                }
                if (path.startsWith("/v1/merchant/application")) {
                    addError(operation.getResponses(), "403", "当前商户角色无权访问入驻申请");
                    if (method == HttpMethod.PUT || method == HttpMethod.POST) {
                        addError(operation.getResponses(), "409", "入驻申请版本、状态或幂等键冲突");
                    }
                }
                if (path.startsWith("/v1/merchant/voucher-products")) {
                    addError(operation.getResponses(), "403", "当前商户角色无权管理团购券");
                    addError(operation.getResponses(), "404", "团购券不存在");
                    if (method == HttpMethod.POST && (path.endsWith("/submission") || path.endsWith("/off-sale"))) {
                        addError(operation.getResponses(), "409", "团购券版本、状态或幂等键冲突");
                    }
                }
                if (path.startsWith("/v1/admin/voucher-reviews")) {
                    addError(operation.getResponses(), "403", "无团购券审核权限");
                    addError(operation.getResponses(), "404", "团购券不存在");
                    if (method == HttpMethod.POST) addError(operation.getResponses(), "409", "团购券审核状态、版本或幂等键冲突");
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
        registerSchema(components, "VoucherPaymentDTO", VoucherPaymentDTO.class);
        registerSchema(components, "VoucherRefundDTO", VoucherRefundDTO.class);
        registerSchema(components, "ConsumerRefundDTO", ConsumerRefundDTO.class);
        registerSchema(components, "MerchantRefundDTO", MerchantRefundDTO.class);
        registerSchema(components, "AdminRefundDTO", AdminRefundDTO.class);
        registerSchema(components, "VoucherProductVO", VoucherProductVO.class);
        registerSchema(components, "VoucherProductDetailVO", VoucherProductDetailVO.class);
        registerSchema(components, "VoucherOrderConfirmationVO", VoucherOrderConfirmationVO.class);
        registerSchema(components, "VoucherOrderVO", VoucherOrderVO.class);
        registerSchema(components, "VoucherOrderDetailVO", VoucherOrderDetailVO.class);
        registerSchema(components, "VoucherPaymentVO", VoucherPaymentVO.class);
        registerSchema(components, "VoucherRefundVO", VoucherRefundVO.class);
        registerSchema(components, "VoucherQrTokenVO", VoucherQrTokenVO.class);
        registerSchema(components, "UserVoucherVO", UserVoucherVO.class);
    }

    private void registerStage23To29Schemas(Components components) {
        registerSchema(components, "MerchantStaffInvitationCreateDTO", MerchantStaffInvitationCreateDTO.class);
        registerSchema(components, "MerchantStaffAcceptanceDTO", MerchantStaffAcceptanceDTO.class);
        registerSchema(components, "MerchantStaffInvitationVO", MerchantStaffInvitationVO.class);
        registerSchema(components, "MerchantStaffVO", MerchantStaffVO.class);
        registerSchema(components, "VoucherRedemptionPreviewDTO", VoucherRedemptionPreviewDTO.class);
        registerSchema(components, "VoucherQrTokenPreviewDTO", VoucherQrTokenPreviewDTO.class);
        registerSchema(components, "VoucherRedemptionConfirmDTO", VoucherRedemptionConfirmDTO.class);
        registerSchema(components, "VoucherRedemptionReversalDTO", VoucherRedemptionReversalDTO.class);
        registerSchema(components, "VoucherRedemptionPreviewVO", VoucherRedemptionPreviewVO.class);
        registerSchema(components, "VoucherRedemptionVO", VoucherRedemptionVO.class);
        registerSchema(components, "CommissionRuleUpdateDTO", CommissionRuleUpdateDTO.class);
        registerSchema(components, "CommissionRuleVO", CommissionRuleVO.class);
        registerSchema(components, "FundLedgerEntryVO", FundLedgerEntryVO.class);
        registerSchema(components, "MerchantFinanceSummaryVO", MerchantFinanceSummaryVO.class);
        registerSchema(components, "SettlementBatchVO", SettlementBatchVO.class);
        registerSchema(components, "AdminEventTicketVO", AdminEventTicketVO.class);
    }

    private void registerConsumerAccountSchemas(Components components) {
        registerSchema(components, "SmsCodeDTO", SmsCodeDTO.class);
        registerSchema(components, "RegistrationDTO", RegistrationDTO.class);
        registerSchema(components, "PasswordLoginDTO", PasswordLoginDTO.class);
        registerSchema(components, "UserProfileUpdateDTO", UserProfileUpdateDTO.class);
        registerSchema(components, "NicknameUpdateDTO", NicknameUpdateDTO.class);
        registerSchema(components, "AvatarUpdateDTO", AvatarUpdateDTO.class);
        registerSchema(components, "PhoneSmsCodeDTO", PhoneSmsCodeDTO.class);
        registerSchema(components, "PhoneChangeDTO", PhoneChangeDTO.class);
        registerSchema(components, "PasswordChangeDTO", PasswordChangeDTO.class);
        registerSchema(components, "CurrentUserProfileVO", CurrentUserProfileVO.class);
        registerSchema(components, "PublicUserProfileVO", PublicUserProfileVO.class);
    }

    private void registerMerchantSchemas(Components components) {
        registerSchema(components, "MerchantShopSummaryVO", MerchantShopSummaryVO.class);
        registerSchema(components, "CurrentMerchantVO", CurrentMerchantVO.class);
        registerSchema(components, "BusinessPeriodDTO", BusinessPeriodDTO.class);
        registerSchema(components, "BusinessDayHoursDTO", BusinessDayHoursDTO.class);
        registerSchema(components, "MerchantApplicationSaveDTO", MerchantApplicationSaveDTO.class);
        registerSchema(components, "BusinessMediaVO", BusinessMediaVO.class);
        registerSchema(components, "MerchantApplicationVO", MerchantApplicationVO.class);
    }

    private void registerAdminMerchantGovernanceSchemas(Components components) {
        registerSchema(
                components,
                "MerchantApplicationReviewListItemVO",
                MerchantApplicationReviewListItemVO.class);
        registerSchema(
                components,
                "MerchantApplicationReviewDetailVO",
                MerchantApplicationReviewDetailVO.class);
        registerSchema(
                components,
                "MerchantApplicationReviewResultVO",
                MerchantApplicationReviewResultVO.class);
        registerSchema(components, "AdminShopListItemVO", AdminShopListItemVO.class);
        registerSchema(components, "AdminShopDetailVO", AdminShopDetailVO.class);
        registerSchema(
                components,
                "AdminShopGovernanceResultVO",
                AdminShopGovernanceResultVO.class);
    }

    private void registerVoucherReviewSchemas(Components components) {
        registerSchema(components, "VoucherReviewApprovalDTO", VoucherReviewApprovalDTO.class);
        registerSchema(components, "VoucherReviewRejectionDTO", VoucherReviewRejectionDTO.class);
        registerSchema(components, "MerchantVoucherProductOffSaleDTO", MerchantVoucherProductOffSaleDTO.class);
        registerSchema(components, "MerchantVoucherPackageItemVO", MerchantVoucherPackageItemVO.class);
        registerSchema(components, "MerchantVoucherProductVO", MerchantVoucherProductVO.class);
        registerSchema(components, "VoucherProductSectionVO", VoucherProductSectionVO.class);
        registerSchema(components, "VoucherProductTagVO", VoucherProductTagVO.class);
        registerSchema(components, "VoucherProductCashRuleVO", VoucherProductCashRuleVO.class);
        registerSchema(components, "VoucherProductDiscountRuleVO", VoucherProductDiscountRuleVO.class);
        registerSchema(components, "VoucherProductMultiUseRuleVO", VoucherProductMultiUseRuleVO.class);
        registerSchema(components, "VoucherProductDetailDTO", VoucherProductDetailDTO.class);
        registerSchema(components, "VoucherProductTagDTO", VoucherProductTagDTO.class);
        registerSchema(components, "VoucherProductCashRuleDTO", VoucherProductCashRuleDTO.class);
        registerSchema(components, "VoucherProductDiscountRuleDTO", VoucherProductDiscountRuleDTO.class);
        registerSchema(components, "VoucherProductMultiUseRuleDTO", VoucherProductMultiUseRuleDTO.class);
        registerSchema(components, "AdminVoucherReviewListItemVO", AdminVoucherReviewListItemVO.class);
        registerSchema(components, "AdminVoucherReviewDetailVO", AdminVoucherReviewDetailVO.class);
        registerSchema(components, "AdminVoucherReviewResultVO", AdminVoucherReviewResultVO.class);
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
        if (path.equals("/v1/shops/{shopId}/voucher-products")
                || path.equals("/v1/voucher-products")
                || path.equals("/v1/voucher-products/{productId}")
                || path.equals("/v1/voucher-products/{productId}/media/{mediaId}/content"))
            return method == HttpMethod.GET;
        if (path.equals("/v1/voucher-products/{productId}/orders")) return method == HttpMethod.POST;
        if (path.equals("/v1/users/me/orders/{orderId}") || path.equals("/v1/users/me/vouchers/{userVoucherId}"))
            return true;
        if (path.equals("/v1/admin/orders/{id}")) return method == HttpMethod.GET;
        if (path.equals("/v1/admin/refunds/{id}") || path.equals("/v1/admin/redemptions/{id}")
                || path.equals("/v1/admin/settlements/{id}")) return method == HttpMethod.GET || method == HttpMethod.POST;
        if (path.equals("/v1/users/{userId}/posts")) return method == HttpMethod.GET;
        return false;
    }

    private boolean mayReturnForbidden(HttpMethod method, String path) {
        if (path.equals("/v1/admin/events") && method == HttpMethod.GET) return true;
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
