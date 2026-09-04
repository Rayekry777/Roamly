package com.ray.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.MerchantStaffInvitation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
public interface MerchantStaffInvitationMapper extends BaseMapper<MerchantStaffInvitation> {
    @Select("SELECT * FROM merchant_staff_invitation WHERE invite_token_digest=#{digest} LIMIT 1")
    MerchantStaffInvitation findByDigest(@Param("digest") String digest);
}
