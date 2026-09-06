package com.ray.voucher.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ray.entity.UserVoucher;
import com.ray.entity.UserVoucherQrCode;
import com.ray.enums.UserVoucherStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.UserVoucherMapper;
import com.ray.mapper.UserVoucherQrCodeMapper;
import com.ray.service.CurrentUserProvider;
import com.ray.service.impl.VoucherQrTokenServiceImpl;
import com.ray.vo.VoucherQrTokenVO;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 固定二维码签发、签名和有效期校验测试。 */
class VoucherQrTokenServiceImplTest {
    private UserVoucherMapper voucherMapper;
    private UserVoucherQrCodeMapper qrMapper;
    private CurrentUserProvider currentUser;
    private VoucherQrTokenServiceImpl service;
    private UserVoucher voucher;
    private UserVoucherQrCode qrCode;

    @BeforeEach
    void setUp() {
        voucherMapper = mock(UserVoucherMapper.class);
        qrMapper = mock(UserVoucherQrCodeMapper.class);
        currentUser = mock(CurrentUserProvider.class);
        when(currentUser.requireUserId()).thenReturn(7L);
        voucher = new UserVoucher().setId(100L).setUserId(7L).setStatus(UserVoucherStatus.UNUSED.name())
                .setValidBeginTime(LocalDateTime.now().minusMinutes(1))
                .setExpireTime(LocalDateTime.now().plusHours(1));
        qrCode = new UserVoucherQrCode().setId(1L).setVoucherId(100L).setUserId(7L)
                .setTokenKey("01234567890123456789012345678901").setTokenVersion(1)
                .setExpireTime(voucher.getExpireTime());
        when(voucherMapper.selectOne(any())).thenReturn(voucher);
        when(qrMapper.findByVoucherId(100L)).thenReturn(qrCode);
        when(qrMapper.findByTokenKey(qrCode.getTokenKey())).thenReturn(qrCode);
        service = new VoucherQrTokenServiceImpl(voucherMapper, qrMapper, currentUser, "test-secret");
    }

    @Test
    void returnsTheSameTokenForRepeatedIssue() {
        VoucherQrTokenVO first = service.issue(100L);
        VoucherQrTokenVO second = service.issue(100L);
        assertEquals(first.token(), second.token());
        assertEquals(first.expiresAt(), second.expiresAt());
    }

    @Test
    void rejectsTamperedSignature() {
        String token = service.issue(100L).token();
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");
        BusinessException exception = assertThrows(BusinessException.class, () -> service.resolve(tampered));
        assertEquals("QR_TOKEN_INVALID", exception.code());
    }

    @Test
    void rejectsExpiredQrCode() {
        qrCode.setExpireTime(LocalDateTime.now().minusSeconds(1));
        String token = service.issue(100L).token();
        BusinessException exception = assertThrows(BusinessException.class, () -> service.resolve(token));
        assertEquals(true, exception.code().startsWith("QR_TOKEN_"));
    }
}
