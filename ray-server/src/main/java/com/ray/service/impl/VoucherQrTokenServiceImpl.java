package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ray.entity.UserVoucher;
import com.ray.entity.UserVoucherQrCode;
import com.ray.enums.UserVoucherStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.UserVoucherMapper;
import com.ray.mapper.UserVoucherQrCodeMapper;
import com.ray.service.CurrentUserProvider;
import com.ray.service.VoucherQrTokenService;
import com.ray.vo.VoucherQrResolution;
import com.ray.vo.VoucherQrTokenVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 为用户券签发有效期内固定不变的签名二维码，并负责无状态解析。 */
@Service
public class VoucherQrTokenServiceImpl implements VoucherQrTokenService {
    private static final String TOKEN_PREFIX = "rq1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final UserVoucherMapper voucherMapper;
    private final UserVoucherQrCodeMapper qrCodeMapper;
    private final CurrentUserProvider currentUser;
    private final String hmacSecret;

    public VoucherQrTokenServiceImpl(UserVoucherMapper voucherMapper,
            UserVoucherQrCodeMapper qrCodeMapper, CurrentUserProvider currentUser,
            @Value("${ray.qr.hmac-secret}") String hmacSecret) {
        this.voucherMapper = voucherMapper;
        this.qrCodeMapper = qrCodeMapper;
        this.currentUser = currentUser;
        this.hmacSecret = hmacSecret;
    }

    /** 获取或创建当前用户券的固定二维码，券有效期内重复返回相同内容。 */
    @Override
    @Transactional
    public VoucherQrTokenVO issue(Long voucherId) {
        Long userId = currentUser.requireUserId();
        UserVoucher voucher = voucherMapper.selectOne(new QueryWrapper<UserVoucher>()
                .eq("id", voucherId).eq("user_id", userId));
        validateConsumerVoucher(voucher);

        UserVoucherQrCode qrCode = qrCodeMapper.findByVoucherId(voucherId);
        if (qrCode == null) {
            qrCode = new UserVoucherQrCode().setVoucherId(voucherId).setUserId(userId)
                    .setTokenKey(UUID.randomUUID().toString().replace("-", ""))
                    .setTokenVersion(1).setExpireTime(voucher.getExpireTime());
            try {
                qrCodeMapper.insert(qrCode);
            } catch (DuplicateKeyException exception) {
                // 并发首次打开同一张券时由唯一索引仲裁，随后读取已创建的记录。
                qrCode = qrCodeMapper.findByVoucherId(voucherId);
                if (qrCode == null) throw exception;
            }
        }
        if (!userId.equals(qrCode.getUserId())) {
            throw BusinessException.forbidden("QR_TOKEN_FORBIDDEN", "二维码不属于当前用户");
        }
        return new VoucherQrTokenVO(sign(qrCode), qrCode.getExpireTime());
    }

    /** 解析并验证固定二维码；二维码本身不会因扫码而删除或消费。 */
    @Override
    public VoucherQrResolution resolve(String token) {
        if (token == null || token.isBlank()) {
            throw BusinessException.badRequest("QR_TOKEN_INVALID", "二维码令牌无效");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !TOKEN_PREFIX.equals(parts[0]) || parts[1].isBlank() || parts[2].isBlank()) {
            throw BusinessException.badRequest("QR_TOKEN_INVALID", "二维码令牌格式无效");
        }
        UserVoucherQrCode qrCode = qrCodeMapper.findByTokenKey(parts[1]);
        if (qrCode == null) {
            throw BusinessException.conflict("QR_TOKEN_INVALID", "二维码令牌无效");
        }
        String expected = sign(qrCode);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                parts[2].getBytes(StandardCharsets.US_ASCII))) {
            throw BusinessException.conflict("QR_TOKEN_INVALID", "二维码签名无效");
        }
        if (qrCode.getExpireTime() != null && !qrCode.getExpireTime().isAfter(LocalDateTime.now())) {
            throw BusinessException.conflict("QR_TOKEN_EXPIRED", "二维码已过期");
        }
        return new VoucherQrResolution(qrCode.getVoucherId(), qrCode.getUserId());
    }

    private void validateConsumerVoucher(UserVoucher voucher) {
        if (voucher == null) throw BusinessException.notFound("USER_VOUCHER_NOT_FOUND", "用户券不存在");
        if (!UserVoucherStatus.UNUSED.name().equals(voucher.getStatus())
                && !UserVoucherStatus.PARTIALLY_USED.name().equals(voucher.getStatus())) {
            throw BusinessException.conflict("VOUCHER_NOT_USABLE", "当前券状态不可核销");
        }
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getValidBeginTime() != null && voucher.getValidBeginTime().isAfter(now)
                || voucher.getExpireTime() != null && !voucher.getExpireTime().isAfter(now)) {
            throw BusinessException.conflict("VOUCHER_NOT_IN_VALIDITY", "券不在有效期");
        }
    }

    private String sign(UserVoucherQrCode qrCode) {
        String payload = qrCode.getTokenKey() + "|" + qrCode.getVoucherId() + "|" + qrCode.getUserId()
                + "|" + qrCode.getTokenVersion() + "|" + expiryEpoch(qrCode.getExpireTime());
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("二维码签名算法不可用", exception);
        }
    }

    private long expiryEpoch(LocalDateTime expireTime) {
        return expireTime == null ? 0L : expireTime.toEpochSecond(ZoneOffset.UTC);
    }
}
