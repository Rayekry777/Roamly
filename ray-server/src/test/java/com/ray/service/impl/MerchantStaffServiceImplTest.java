package com.ray.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ray.dto.MerchantStaffAcceptanceDTO;
import com.ray.dto.MerchantStaffInvitationCreateDTO;
import com.ray.entity.MerchantAccount;
import com.ray.entity.MerchantApplication;
import com.ray.entity.MerchantStaffInvitation;
import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.MerchantRole;
import com.ray.exception.BusinessException;
import com.ray.mapper.MerchantAccountMapper;
import com.ray.mapper.MerchantApplicationMapper;
import com.ray.mapper.MerchantStaffInvitationMapper;
import com.ray.service.MerchantAuthService;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.MerchantStaffInvitationVO;
import com.ray.vo.MerchantStaffVO;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class MerchantStaffServiceImplTest {
    private static final String SECRET = "unit-test-merchant-invitation-secret";
    private MerchantStaffInvitationMapper invitationMapper;
    private MerchantAccountMapper accountMapper;
    private MerchantApplicationMapper applicationMapper;
    private MerchantAuthService authService;
    private RedisIdWorker idWorker;
    private ValueOperations<String, String> values;
    private MerchantStaffServiceImpl service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        invitationMapper = mock(MerchantStaffInvitationMapper.class);
        accountMapper = mock(MerchantAccountMapper.class);
        applicationMapper = mock(MerchantApplicationMapper.class);
        authService = mock(MerchantAuthService.class);
        idWorker = mock(RedisIdWorker.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(idWorker.nextId("merchant-invitation")).thenReturn(9001L);
        when(accountMapper.selectByIdForUpdate(1L)).thenReturn(tenant());
        service = new MerchantStaffServiceImpl(
                invitationMapper, accountMapper, applicationMapper, authService, idWorker, redis, SECRET);
    }

    @Test
    void tenantIssuesSixDigitCredentialOnlyForRegisteredPureVisitor() {
        when(authService.requireCurrentAccount()).thenReturn(tenant());
        when(accountMapper.selectByPhoneForUpdate("13900000034")).thenReturn(visitor());

        MerchantStaffInvitationVO result = service.invite(
                new MerchantStaffInvitationCreateDTO("13900000034", "VERIFIER"), "invite-key-10001");

        assertTrue(result.credentialCode().matches("\\d{6}"));
        assertTrue(result.remainingSeconds() >= 59 && result.remainingSeconds() <= 60);
        ArgumentCaptor<MerchantStaffInvitation> captor = ArgumentCaptor.forClass(MerchantStaffInvitation.class);
        verify(invitationMapper).insert(captor.capture());
        assertEquals(64, captor.getValue().getCredentialDigest().length());
        assertNull(captor.getValue().getAcceptedAccountId());
    }

    @Test
    void unregisteredPhoneDoesNotCreateInvitation() {
        when(authService.requireCurrentAccount()).thenReturn(tenant());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.invite(
                        new MerchantStaffInvitationCreateDTO("13900000099", "MANAGER"), "invite-key-10002"));

        assertEquals("MERCHANT_INVITEE_NOT_REGISTERED", exception.code());
        verify(invitationMapper, never()).insert(any(MerchantStaffInvitation.class));
    }

    @Test
    void visitorWithOnboardingApplicationCannotBeInvited() {
        MerchantAccount visitor = visitor();
        when(authService.requireCurrentAccount()).thenReturn(tenant());
        when(accountMapper.selectByPhoneForUpdate(visitor.getPhone())).thenReturn(visitor);
        when(applicationMapper.selectByAccountForUpdate(visitor.getId())).thenReturn(new MerchantApplication());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.invite(
                        new MerchantStaffInvitationCreateDTO(visitor.getPhone(), "MANAGER"), "invite-key-10003"));

        assertEquals("MERCHANT_INVITEE_HAS_APPLICATION", exception.code());
    }

    @Test
    void sameIssueKeyAndRequestReplaysCachedCredential() {
        String code = "482731";
        MerchantAccount visitor = visitor();
        MerchantStaffInvitation invitation = new MerchantStaffInvitation()
                .setId(9001L)
                .setShopId(1L)
                .setInviterAccountId(1L)
                .setTargetPhone(visitor.getPhone())
                .setTargetRole(MerchantRole.VERIFIER.name())
                .setStatus("PENDING")
                .setExpireTime(LocalDateTime.now().plusSeconds(30))
                .setIssueIdempotencyKey("invite-key-10005")
                .setIssueRequestFingerprint(
                        cn.hutool.crypto.digest.DigestUtil.sha256Hex(visitor.getPhone() + "|VERIFIER"));
        when(authService.requireCurrentAccount()).thenReturn(tenant());
        when(accountMapper.selectByPhoneForUpdate(visitor.getPhone())).thenReturn(visitor);
        when(invitationMapper.selectByIssueKey(1L, "invite-key-10005")).thenReturn(invitation);
        when(values.get(anyString())).thenReturn(code);

        MerchantStaffInvitationVO result = service.invite(
                new MerchantStaffInvitationCreateDTO(visitor.getPhone(), "VERIFIER"), "invite-key-10005");

        assertEquals(code, result.credentialCode());
        verify(invitationMapper, never()).insert(any(MerchantStaffInvitation.class));
    }

    @Test
    void activeInvitationForSamePhoneIsRejected() {
        MerchantAccount visitor = visitor();
        when(authService.requireCurrentAccount()).thenReturn(tenant());
        when(accountMapper.selectByPhoneForUpdate(visitor.getPhone())).thenReturn(visitor);
        when(invitationMapper.selectActiveByPhoneForUpdate(anyString(), any(LocalDateTime.class)))
                .thenReturn(new MerchantStaffInvitation());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.invite(
                        new MerchantStaffInvitationCreateDTO(visitor.getPhone(), "MANAGER"),
                        "invite-key-10006"));

        assertEquals("MERCHANT_INVITATION_ALREADY_ACTIVE", exception.code());
        verify(invitationMapper, never()).insert(any(MerchantStaffInvitation.class));
    }

    @Test
    void onlyActiveTenantCanIssueCredential() {
        when(authService.requireCurrentAccount()).thenReturn(visitor());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.invite(
                        new MerchantStaffInvitationCreateDTO("13900000034", "MANAGER"), "invite-key-10004"));

        assertEquals("MERCHANT_STAFF_FORBIDDEN", exception.code());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void targetVisitorAcceptsCredentialAndBecomesManager() throws Exception {
        String code = "406218";
        MerchantAccount visitor = visitor();
        MerchantAccount manager = visitor()
                .setRole(MerchantRole.MANAGER.name())
                .setStatus(MerchantAccountStatus.ACTIVE.name())
                .setShopId(1L)
                .setVersion(1);
        MerchantStaffInvitation invitation = new MerchantStaffInvitation()
                .setId(9001L)
                .setShopId(1L)
                .setInviterAccountId(1L)
                .setCredentialDigest(hmac(visitor.getPhone(), code))
                .setTargetPhone(visitor.getPhone())
                .setTargetRole(MerchantRole.MANAGER.name())
                .setStatus("PENDING")
                .setExpireTime(LocalDateTime.now().plusSeconds(30));
        when(authService.requireCurrentAccount()).thenReturn(visitor);
        when(accountMapper.selectByIdForUpdate(visitor.getId())).thenReturn(visitor);
        when(invitationMapper.selectByCredentialForUpdate(visitor.getPhone(), invitation.getCredentialDigest()))
                .thenReturn(invitation);
        when(accountMapper.update(nullable(MerchantAccount.class), any(Wrapper.class))).thenReturn(1);
        when(invitationMapper.update(nullable(MerchantStaffInvitation.class), any(Wrapper.class))).thenReturn(1);
        when(accountMapper.selectById(visitor.getId())).thenReturn(manager);

        MerchantStaffVO result = service.accept(new MerchantStaffAcceptanceDTO(code), "accept-key-10001");

        assertEquals("MANAGER", result.role());
        assertEquals("ACTIVE", result.status());
        verify(applicationMapper).selectByAccountForUpdate(visitor.getId());
    }

    @Test
    void wrongCredentialReturnsUnifiedErrorAndCountsFailure() {
        MerchantAccount visitor = visitor();
        when(authService.requireCurrentAccount()).thenReturn(visitor);
        when(accountMapper.selectByIdForUpdate(visitor.getId())).thenReturn(visitor);
        when(values.increment(anyString())).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.accept(new MerchantStaffAcceptanceDTO("000000"), "accept-key-10002"));

        assertEquals("INVITATION_INVALID_OR_EXPIRED", exception.code());
        verify(values).increment(anyString());
    }

    @Test
    void fifthRecordedFailureBlocksFurtherAcceptanceAttempts() {
        MerchantAccount visitor = visitor();
        when(authService.requireCurrentAccount()).thenReturn(visitor);
        when(values.get(anyString())).thenReturn("5");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.accept(new MerchantStaffAcceptanceDTO("000000"), "accept-key-10003"));

        assertEquals(429, exception.status());
        assertEquals("INVITATION_ATTEMPTS_LIMITED", exception.code());
        verify(accountMapper, never()).selectByIdForUpdate(visitor.getId());
    }

    @Test
    void acceptedInvitationReplaysForSameAccountAndIdempotencyKey() throws Exception {
        String code = "406218";
        MerchantAccount manager = visitor()
                .setRole(MerchantRole.MANAGER.name())
                .setStatus(MerchantAccountStatus.ACTIVE.name())
                .setShopId(1L)
                .setVersion(1);
        MerchantStaffInvitation invitation = new MerchantStaffInvitation()
                .setId(9001L)
                .setShopId(1L)
                .setInviterAccountId(1L)
                .setCredentialDigest(hmac(manager.getPhone(), code))
                .setTargetPhone(manager.getPhone())
                .setTargetRole(MerchantRole.MANAGER.name())
                .setStatus("ACCEPTED")
                .setAcceptedAccountId(manager.getId())
                .setAcceptanceIdempotencyKey("accept-key-10004")
                .setExpireTime(LocalDateTime.now().minusSeconds(1));
        when(authService.requireCurrentAccount()).thenReturn(manager);
        when(accountMapper.selectByIdForUpdate(manager.getId())).thenReturn(manager);
        when(invitationMapper.selectByCredentialForUpdate(manager.getPhone(), invitation.getCredentialDigest()))
                .thenReturn(invitation);

        MerchantStaffVO result = service.accept(
                new MerchantStaffAcceptanceDTO(code), "accept-key-10004");

        assertEquals("MANAGER", result.role());
        verify(applicationMapper, never()).selectByAccountForUpdate(manager.getId());
    }

    private MerchantAccount tenant() {
        return new MerchantAccount()
                .setId(1L)
                .setPhone("13900000001")
                .setNickname("测试租户")
                .setRole(MerchantRole.TENANT.name())
                .setStatus(MerchantAccountStatus.ACTIVE.name())
                .setShopId(1L)
                .setVersion(0);
    }

    private MerchantAccount visitor() {
        return new MerchantAccount()
                .setId(34L)
                .setPhone("13900000034")
                .setNickname("测试游客")
                .setRole(MerchantRole.VISITOR.name())
                .setStatus(MerchantAccountStatus.NOT_APPLIED.name())
                .setVersion(0);
    }

    private String hmac(String phone, String code) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((phone + ":" + code).getBytes(StandardCharsets.UTF_8)));
    }
}
