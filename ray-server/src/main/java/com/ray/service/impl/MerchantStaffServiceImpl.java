package com.ray.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.dto.MerchantStaffAcceptanceDTO;
import com.ray.dto.MerchantStaffInvitationCreateDTO;
import com.ray.entity.MerchantAccount;
import com.ray.entity.MerchantStaffInvitation;
import com.ray.enums.MerchantAccountDisabledSource;
import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.MerchantRole;
import com.ray.exception.BusinessException;
import com.ray.mapper.MerchantAccountMapper;
import com.ray.mapper.MerchantStaffInvitationMapper;
import com.ray.result.PageResult;
import com.ray.service.MerchantAuthService;
import com.ray.service.MerchantStaffService;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.MerchantStaffInvitationVO;
import com.ray.vo.MerchantStaffVO;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantStaffServiceImpl implements MerchantStaffService {
    private final MerchantStaffInvitationMapper invitationMapper;
    private final MerchantAccountMapper accountMapper;
    private final MerchantAuthService authService;
    private final RedisIdWorker idWorker;
    public MerchantStaffServiceImpl(MerchantStaffInvitationMapper invitationMapper, MerchantAccountMapper accountMapper, MerchantAuthService authService, RedisIdWorker idWorker){this.invitationMapper=invitationMapper;this.accountMapper=accountMapper;this.authService=authService;this.idWorker=idWorker;}
    private MerchantAccount owner(){ MerchantAccount a=authService.requireCurrentAccount(); if(!MerchantRole.OWNER.name().equals(a.getRole())||!MerchantAccountStatus.ACTIVE.name().equals(a.getStatus())) throw BusinessException.forbidden("MERCHANT_STAFF_FORBIDDEN","仅激活店主可管理员工"); return a; }
    @Override public PageResult<MerchantStaffVO> list(int page,int size){ MerchantAccount o=owner(); Page<MerchantAccount> p=accountMapper.selectPage(new Page<>(page,size),new QueryWrapper<MerchantAccount>().eq("shop_id",o.getShopId()).orderByAsc("id")); return new PageResult<>(p.getRecords().stream().map(this::toStaff).toList(),page,size,p.getTotal()); }
    @Override @Transactional public MerchantStaffInvitationVO invite(MerchantStaffInvitationCreateDTO req,String key){ MerchantAccount o=owner(); if(req.role()==MerchantRole.OWNER) throw BusinessException.badRequest("MERCHANT_STAFF_ROLE_INVALID","员工角色只能为店长或核销员"); MerchantAccount existing=accountMapper.selectOne(new QueryWrapper<MerchantAccount>().eq("phone",req.phone())); if(existing!=null && existing.getShopId()!=null) throw BusinessException.conflict("MERCHANT_STAFF_ALREADY_BOUND","该手机号已绑定门店"); String token=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""); LocalDateTime now=LocalDateTime.now(); MerchantStaffInvitation i=new MerchantStaffInvitation().setId(idWorker.nextId("merchant-invitation")).setShopId(o.getShopId()).setInviterAccountId(o.getId()).setInviteTokenDigest(DigestUtil.sha256Hex(token)).setTargetPhone(req.phone()).setTargetRole(req.role().name()).setStatus("PENDING").setExpireTime(now.plusHours(24)); invitationMapper.insert(i); return toInvitation(i,token); }
    @Override @Transactional public MerchantStaffInvitationVO revoke(Long id,String key){ MerchantAccount o=owner(); MerchantStaffInvitation i=invitationMapper.selectById(id); if(i==null||!o.getShopId().equals(i.getShopId())) throw BusinessException.notFound("INVITATION_NOT_FOUND","邀请不存在"); if(!"PENDING".equals(i.getStatus())) throw BusinessException.conflict("INVITATION_STATUS_CONFLICT","邀请状态已变化"); i.setStatus("REVOKED").setRevokedTime(LocalDateTime.now()); invitationMapper.updateById(i); return toInvitation(i,null); }
    @Override @Transactional public MerchantStaffVO accept(MerchantStaffAcceptanceDTO req,String key){ MerchantAccount current=authService.requireCurrentAccount(); MerchantStaffInvitation i=invitationMapper.findByDigest(DigestUtil.sha256Hex(req.token())); if(i==null||!i.getTargetPhone().equals(current.getPhone())) throw BusinessException.notFound("INVITATION_NOT_FOUND","邀请不存在"); if(!"PENDING".equals(i.getStatus())||i.getExpireTime().isBefore(LocalDateTime.now())) throw BusinessException.conflict("INVITATION_EXPIRED","邀请已过期或不可用"); if(current.getShopId()!=null) throw BusinessException.conflict("MERCHANT_STAFF_ALREADY_BOUND","当前账号已绑定门店"); int changed=invitationMapper.update(null,new UpdateWrapper<MerchantStaffInvitation>().eq("id",i.getId()).eq("status","PENDING").set("status","ACCEPTED").set("accepted_time",LocalDateTime.now()).set("accepted_account_id",current.getId())); if(changed!=1) throw BusinessException.conflict("INVITATION_STATUS_CONFLICT","邀请已被其他请求接受"); current.setShopId(i.getShopId()).setRole(i.getTargetRole()).setStatus(MerchantAccountStatus.ACTIVE.name()).setVersion((current.getVersion()==null?0:current.getVersion())+1); accountMapper.updateById(current); return toStaff(current); }
    @Override @Transactional public MerchantStaffVO setEnabled(Long id,boolean enabled,String key){ MerchantAccount o=owner(); MerchantAccount target=accountMapper.selectById(id); if(target==null||!o.getShopId().equals(target.getShopId())||MerchantRole.OWNER.name().equals(target.getRole())) throw BusinessException.notFound("MERCHANT_STAFF_NOT_FOUND","员工不存在"); if(enabled){ target.setStatus(MerchantAccountStatus.ACTIVE.name()).setDisabledSource(null).setDisabledReason(null).setDisabledAt(null);}else{target.setStatus(MerchantAccountStatus.DISABLED.name()).setDisabledSource(MerchantAccountDisabledSource.STAFF_MANAGEMENT.name()).setDisabledReason("店主停用员工").setDisabledAt(LocalDateTime.now());} accountMapper.updateById(target); authService.invalidateAllSessions(java.util.List.of(target.getId())); return toStaff(target); }
    private MerchantStaffVO toStaff(MerchantAccount a){MerchantRole r=MerchantRole.valueOf(a.getRole());MerchantAccountStatus s=MerchantAccountStatus.valueOf(a.getStatus());return new MerchantStaffVO(IdUtils.format(a.getId()),a.getPhone(),a.getNickname(),r.name(),r.label(),s.name(),s.label());}
    private MerchantStaffInvitationVO toInvitation(MerchantStaffInvitation i,String token){MerchantRole r=MerchantRole.valueOf(i.getTargetRole());return new MerchantStaffInvitationVO(IdUtils.format(i.getId()),i.getTargetPhone(),r.name(),r.label(),i.getStatus(),i.getExpireTime(),token);}
}
