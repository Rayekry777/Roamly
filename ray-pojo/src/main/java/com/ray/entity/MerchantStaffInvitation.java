package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 商户员工邀请。 */
@Data @Accessors(chain = true) @TableName("merchant_staff_invitation")
public class MerchantStaffInvitation {
    @TableId(value="id", type=IdType.INPUT) private Long id;
    private Long shopId; private Long inviterAccountId; private String inviteTokenDigest;
    private String targetPhone; private String targetRole; private String status;
    private LocalDateTime expireTime; private LocalDateTime acceptedTime; private LocalDateTime revokedTime;
    private Long acceptedAccountId; private LocalDateTime createTime; private LocalDateTime updateTime;
}
