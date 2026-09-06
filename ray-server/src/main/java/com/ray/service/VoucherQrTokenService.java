package com.ray.service;
import com.ray.vo.VoucherQrResolution;
import com.ray.vo.VoucherQrTokenVO;

/** 为用户券提供固定二维码，并在商户扫码时完成签名解析。 */
public interface VoucherQrTokenService {
    /** 获取或创建当前用户券的固定二维码。 */
    VoucherQrTokenVO issue(Long voucherId);

    /** 解析并验证固定二维码，不改变二维码凭证状态。 */
    VoucherQrResolution resolve(String token);
}
