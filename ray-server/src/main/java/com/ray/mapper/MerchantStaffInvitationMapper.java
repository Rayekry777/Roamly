package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.MerchantStaffInvitation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 商户员工短时邀请数据访问接口。 */
public interface MerchantStaffInvitationMapper extends BaseMapper<MerchantStaffInvitation> {
    /** 按目标手机号和凭证摘要加锁读取邀请。 */
    @Select("""
            SELECT * FROM merchant_staff_invitation
            WHERE target_phone=#{phone} AND credential_digest=#{digest}
            ORDER BY create_time DESC LIMIT 1 FOR UPDATE
            """)
    MerchantStaffInvitation selectByCredentialForUpdate(
            @Param("phone") String phone, @Param("digest") String digest);

    /** 按租户签发幂等键读取既有邀请。 */
    @Select("""
            SELECT * FROM merchant_staff_invitation
            WHERE inviter_account_id=#{inviterAccountId} AND issue_idempotency_key=#{idempotencyKey}
            LIMIT 1
            """)
    MerchantStaffInvitation selectByIssueKey(
            @Param("inviterAccountId") Long inviterAccountId,
            @Param("idempotencyKey") String idempotencyKey);

    /** 锁定目标手机号当前仍有效的邀请。 */
    @Select("""
            SELECT * FROM merchant_staff_invitation
            WHERE target_phone=#{phone} AND status='PENDING' AND expire_time>#{now}
            ORDER BY create_time DESC LIMIT 1 FOR UPDATE
            """)
    MerchantStaffInvitation selectActiveByPhoneForUpdate(
            @Param("phone") String phone, @Param("now") java.time.LocalDateTime now);
}
