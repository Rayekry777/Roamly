package com.ray.vo;

import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.MerchantRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "当前商户账号个人信息")
public record MerchantAccountProfileVO(
        @Schema(description = "字符串商户账号 ID", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
                String id,
        @Schema(description = "商户账号昵称", requiredMode = Schema.RequiredMode.REQUIRED) String nickname,
        @Schema(description = "完整中国大陆手机号", example = "13900000001", requiredMode = Schema.RequiredMode.REQUIRED)
                String phone,
        @Schema(description = "Bearer 鉴权的私有头像内容路径", nullable = true) String avatarContentPath,
        @Schema(description = "固定商户角色", requiredMode = Schema.RequiredMode.REQUIRED) MerchantRole role,
        @Schema(description = "角色中文名", requiredMode = Schema.RequiredMode.REQUIRED) String roleLabel,
        @Schema(description = "商户账号状态", requiredMode = Schema.RequiredMode.REQUIRED) MerchantAccountStatus status,
        @Schema(description = "状态中文名", requiredMode = Schema.RequiredMode.REQUIRED) String statusLabel,
        @Schema(description = "所属门店摘要，未绑定门店时为空", nullable = true) MerchantShopSummaryVO shop) {}
