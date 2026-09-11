package com.ray.service;

import com.ray.dto.MerchantStaffAcceptanceDTO;
import com.ray.dto.MerchantStaffInvitationCreateDTO;
import com.ray.result.PageResult;
import com.ray.vo.MerchantStaffInvitationVO;
import com.ray.vo.MerchantStaffVO;

/** 提供租户员工查询、短时邀请和员工状态管理能力。 */
public interface MerchantStaffService {
    /** 查询当前租户公司的店长与核销员。 */
    PageResult<MerchantStaffVO> list(int page, int size);

    /** 校验目标游客账号并签发六位、60 秒有效的邀请凭证。 */
    MerchantStaffInvitationVO invite(MerchantStaffInvitationCreateDTO request, String idempotencyKey);

    /** 撤销当前租户公司尚未消费的邀请。 */
    MerchantStaffInvitationVO revoke(Long id, String idempotencyKey);

    /** 由目标手机号当前登录的游客接受邀请并加入公司。 */
    MerchantStaffVO accept(MerchantStaffAcceptanceDTO request, String idempotencyKey);

    /** 启用或停用当前租户公司中的店长或核销员。 */
    MerchantStaffVO setEnabled(Long id, boolean enabled, String idempotencyKey);
}
