package com.ray.service;
import com.ray.dto.MerchantStaffAcceptanceDTO;
import com.ray.dto.MerchantStaffInvitationCreateDTO;
import com.ray.result.PageResult;
import com.ray.vo.MerchantStaffInvitationVO;
import com.ray.vo.MerchantStaffVO;
public interface MerchantStaffService {
    PageResult<MerchantStaffVO> list(int page,int size);
    MerchantStaffInvitationVO invite(MerchantStaffInvitationCreateDTO request,String key);
    MerchantStaffInvitationVO revoke(Long id,String key);
    MerchantStaffVO accept(MerchantStaffAcceptanceDTO request,String key);
    MerchantStaffVO setEnabled(Long id,boolean enabled,String key);
}
