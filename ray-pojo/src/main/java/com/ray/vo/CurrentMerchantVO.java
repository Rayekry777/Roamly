package com.ray.vo;

import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.MerchantRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "当前登录商户账号")
public record CurrentMerchantVO(
        @Schema(description = "字符串商户账号 ID") String id,
        @Schema(description = "脱敏手机号", example = "138****8000") String maskedPhone,
        @Schema(description = "商户昵称") String nickname,
        @Schema(description = "私有头像内容路径，可为空") String avatarContentPath,
        @Schema(description = "固定商户角色") MerchantRole role,
        @Schema(description = "角色中文名") String roleLabel,
        @Schema(description = "商户账号状态") MerchantAccountStatus status,
        @Schema(description = "状态中文名") String statusLabel,
        @Schema(description = "已绑定门店摘要，未绑定时为空") MerchantShopSummaryVO shop,
        @Schema(description = "当前账号是否可接受员工邀请") boolean canAcceptStaffInvitation,
        @Schema(description = "固定权限码集合") List<String> permissions) {
    public CurrentMerchantVO {
        permissions = List.copyOf(permissions);
    }
}
