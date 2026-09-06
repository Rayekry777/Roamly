package com.ray.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.constant.AdminPermissions;
import com.ray.dto.VoucherRedemptionConfirmDTO;
import com.ray.dto.VoucherRedemptionPreviewDTO;
import com.ray.dto.VoucherRedemptionReversalDTO;
import com.ray.entity.MerchantAccount;
import com.ray.entity.UserVoucher;
import com.ray.entity.VoucherProduct;
import com.ray.entity.VoucherRedemption;
import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.MerchantRole;
import com.ray.enums.UserVoucherStatus;
import com.ray.enums.VoucherProductType;
import com.ray.exception.BusinessException;
import com.ray.mapper.UserVoucherMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.mapper.VoucherRedemptionMapper;
import com.ray.realtime.RealtimeEventPublisher;
import com.ray.result.PageResult;
import com.ray.service.AdminAuthService;
import com.ray.service.FinanceService;
import com.ray.service.MerchantAuditService;
import com.ray.service.MerchantAuthService;
import com.ray.service.VoucherRedemptionService;
import com.ray.service.VoucherQrTokenService;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.converter.VoucherProductPresentation;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.VoucherRedemptionPreviewVO;
import com.ray.vo.VoucherRedemptionVO;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 商户核销服务；负责券履约，不采集顾客线下消费金额。 */
@Service
public class VoucherRedemptionServiceImpl implements VoucherRedemptionService {
    private static final String PREVIEW = "roamly:redemption:preview:";

    private final UserVoucherMapper voucherMapper;
    private final VoucherRedemptionMapper redemptionMapper;
    private final VoucherProductMapper productMapper;
    private final MerchantAuthService merchantAuth;
    private final AdminAuthService adminAuth;
    private final StringRedisTemplate redis;
    private final RedisIdWorker idWorker;
    private final MerchantAuditService audit;
    private final FinanceService finance;
    private final VoucherQrTokenService qrTokens;
    private RealtimeEventPublisher realtimeEvents;

    public VoucherRedemptionServiceImpl(UserVoucherMapper voucherMapper,
            VoucherRedemptionMapper redemptionMapper, VoucherProductMapper productMapper,
            MerchantAuthService merchantAuth, AdminAuthService adminAuth, StringRedisTemplate redis,
            RedisIdWorker idWorker, MerchantAuditService audit, FinanceService finance,
            VoucherQrTokenService qrTokens) {
        this.voucherMapper = voucherMapper;
        this.redemptionMapper = redemptionMapper;
        this.productMapper = productMapper;
        this.merchantAuth = merchantAuth;
        this.adminAuth = adminAuth;
        this.redis = redis;
        this.idWorker = idWorker;
        this.audit = audit;
        this.finance = finance;
        this.qrTokens = qrTokens;
    }

    @Autowired(required = false)
    void setRealtimeEvents(RealtimeEventPublisher realtimeEvents) {
        this.realtimeEvents = realtimeEvents;
    }

    @Override
    public VoucherRedemptionPreviewVO preview(VoucherRedemptionPreviewDTO request) {
        MerchantAccount account = operator();
        UserVoucher voucher = voucherMapper.findByCodeHmac(DigestUtil.sha256Hex(request.code()));
        validateVoucher(account, voucher);
        VoucherProduct product = productMapper.selectById(voucher.getProductId());
        String token = UUID.randomUUID().toString().replace("-", "");
        String context = voucher.getId() + "|" + account.getShopId() + "|" + account.getId();
        redis.opsForValue().set(PREVIEW + token, context, 5, TimeUnit.MINUTES);
        return toPreview(token, voucher, product);
    }

    @Override
    public VoucherRedemptionPreviewVO previewByQrToken(String token) {
        var resolution = qrTokens.resolve(token);
        UserVoucher voucher = voucherMapper.selectById(resolution.voucherId());
        if (voucher == null) {
            throw BusinessException.notFound("VOUCHER_NOT_FOUND", "券不存在");
        }
        if (!resolution.userId().equals(voucher.getUserId())) {
            throw BusinessException.conflict("QR_TOKEN_INVALID", "二维码绑定关系无效");
        }
        MerchantAccount account = operator();
        validateVoucher(account, voucher);
        VoucherProduct product = productMapper.selectById(voucher.getProductId());
        String previewToken = UUID.randomUUID().toString().replace("-", "");
        String context = voucher.getId() + "|" + account.getShopId() + "|" + account.getId();
        redis.opsForValue().set(PREVIEW + previewToken, context, 5, TimeUnit.MINUTES);
        return toPreview(previewToken, voucher, product);
    }

    @Override
    @Transactional
    public VoucherRedemptionVO confirm(VoucherRedemptionConfirmDTO request, String idempotencyKey) {
        MerchantAccount account = operator();
        VoucherRedemption existing = redemptionMapper.selectOne(new QueryWrapper<VoucherRedemption>()
                .eq("shop_id", account.getShopId()).eq("idempotency_key", idempotencyKey));
        if (existing != null) {
            return toVO(existing);
        }
        String value = redis.opsForValue().getAndDelete(PREVIEW + request.previewToken());
        if (value == null) {
            throw BusinessException.conflict("REDEMPTION_PREVIEW_EXPIRED", "核销预览已过期");
        }
        String[] parts = value.split("\\|");
        if (parts.length != 3) {
            throw BusinessException.conflict("REDEMPTION_PREVIEW_INVALID", "核销预览无效");
        }
        Long voucherId = Long.valueOf(parts[0]);
        if (!account.getShopId().equals(Long.valueOf(parts[1]))
                || !account.getId().equals(Long.valueOf(parts[2]))) {
            throw BusinessException.forbidden("REDEMPTION_PREVIEW_FORBIDDEN", "核销预览不属于当前账号");
        }
        UserVoucher voucher = voucherMapper.selectById(voucherId);
        validateVoucher(account, voucher);
        int before = remaining(voucher);
        int after = before - 1;
        int changed = voucherMapper.update(null, new UpdateWrapper<UserVoucher>().eq("id", voucherId)
                .eq("remaining_use_count", before)
                .in("status", UserVoucherStatus.UNUSED.name(), UserVoucherStatus.PARTIALLY_USED.name())
                .set("remaining_use_count", after)
                .set("status", after == 0 ? UserVoucherStatus.USED.name() : UserVoucherStatus.PARTIALLY_USED.name())
                .set(after == 0, "use_time", LocalDateTime.now()));
        if (changed != 1) {
            throw BusinessException.conflict("VOUCHER_ALREADY_REDEEMED", "券状态已变化");
        }
        VoucherRedemption redemption = new VoucherRedemption()
                .setId(idWorker.nextId("voucher-redemption"))
                .setVoucherId(voucherId).setShopId(account.getShopId())
                .setMerchantAccountId(account.getId()).setUseCount(1)
                .setStatus("SUCCEEDED").setIdempotencyKey(idempotencyKey)
                .setRedeemedTime(LocalDateTime.now());
        redemptionMapper.insert(redemption);
        finance.recognizeRedemption(redemption.getId(), voucher, redemption.getRedeemedTime());
        audit.record(account.getId(), "VOUCHER_REDEEMED", "USER_VOUCHER", voucherId.toString(), "SUCCEEDED", null);
        if (realtimeEvents != null) {
            realtimeEvents.publish("VOUCHER_REDEEMED", redemption.getId().toString(), account.getShopId());
        }
        return toVO(redemption);
    }

    @Override
    @Transactional
    public VoucherRedemptionVO reverse(Long id, VoucherRedemptionReversalDTO request, String idempotencyKey) {
        MerchantAccount account = operator();
        if (MerchantRole.VERIFIER.name().equals(account.getRole())) {
            throw BusinessException.forbidden("REDEMPTION_REVERSAL_FORBIDDEN", "核销员无权撤销");
        }
        VoucherRedemption redemption = redemptionMapper.selectById(id);
        if (redemption == null || !account.getShopId().equals(redemption.getShopId())) {
            throw BusinessException.notFound("REDEMPTION_NOT_FOUND", "核销记录不存在");
        }
        if (!"SUCCEEDED".equals(redemption.getStatus())) {
            return toVO(redemption);
        }
        if (finance.isRedemptionSettled(id)) {
            throw BusinessException.conflict("REDEMPTION_ALREADY_SETTLED", "核销已进入结算，不能撤销");
        }
        UserVoucher voucher = voucherMapper.selectById(redemption.getVoucherId());
        int total = voucher == null || voucher.getTotalUseCount() == null ? 1 : voucher.getTotalUseCount();
        int restored = Math.min(total, remaining(voucher) + 1);
        voucherMapper.update(null, new UpdateWrapper<UserVoucher>().eq("id", voucher.getId())
                .set("remaining_use_count", restored)
                .set("status", restored == total ? UserVoucherStatus.UNUSED.name() : UserVoucherStatus.PARTIALLY_USED.name())
                .set("use_time", null));
        redemption.setStatus("REVERSED").setReversalReason(request.reason())
                .setReversedByAccountId(account.getId()).setReversedTime(LocalDateTime.now());
        redemptionMapper.updateById(redemption);
        finance.reverseRedemption(redemption.getId(), voucher, redemption.getReversedTime());
        audit.record(account.getId(), "REDEMPTION_REVERSED", "VOUCHER_REDEMPTION", id.toString(), "SUCCEEDED", request.reason());
        if (realtimeEvents != null) {
            realtimeEvents.publish("REDEMPTION_REVERSED", id.toString(), account.getShopId());
        }
        return toVO(redemption);
    }

    @Override
    public PageResult<VoucherRedemptionVO> list(int page, int size, boolean admin) {
        QueryWrapper<VoucherRedemption> query = new QueryWrapper<>();
        if (admin) {
            adminAuth.requirePermission(AdminPermissions.TRADE_READ);
        } else {
            query.eq("shop_id", operator().getShopId());
        }
        Page<VoucherRedemption> result = redemptionMapper.selectPage(new Page<>(page, size), query.orderByDesc("redeemed_time", "id"));
        return new PageResult<>(result.getRecords().stream().map(this::toVO).toList(), page, size, result.getTotal());
    }

    @Override
    public VoucherRedemptionVO get(Long id, boolean admin) {
        VoucherRedemption redemption = redemptionMapper.selectById(id);
        if (admin) {
            adminAuth.requirePermission(AdminPermissions.TRADE_READ);
        } else if (redemption == null || !operator().getShopId().equals(redemption.getShopId())) {
            throw BusinessException.notFound("REDEMPTION_NOT_FOUND", "核销记录不存在");
        }
        if (redemption == null) {
            throw BusinessException.notFound("REDEMPTION_NOT_FOUND", "核销记录不存在");
        }
        return toVO(redemption);
    }

    private MerchantAccount operator() {
        MerchantAccount account = merchantAuth.requireCurrentAccount();
        if (!MerchantAccountStatus.ACTIVE.name().equals(account.getStatus()) || account.getShopId() == null) {
            throw BusinessException.forbidden("MERCHANT_ACTIVATION_REQUIRED", "商户账号尚未激活");
        }
        return account;
    }

    private void validateVoucher(MerchantAccount account, UserVoucher voucher) {
        if (voucher == null || !account.getShopId().equals(voucher.getShopId())) {
            throw BusinessException.notFound("VOUCHER_NOT_FOUND", "券不存在或不属于当前门店");
        }
        if (!UserVoucherStatus.UNUSED.name().equals(voucher.getStatus())
                && !UserVoucherStatus.PARTIALLY_USED.name().equals(voucher.getStatus())) {
            throw BusinessException.conflict("VOUCHER_NOT_REDEEMABLE", "券当前不可核销");
        }
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getValidBeginTime() != null && voucher.getValidBeginTime().isAfter(now)
                || voucher.getExpireTime() != null && !voucher.getExpireTime().isAfter(now)) {
            throw BusinessException.conflict("VOUCHER_NOT_IN_VALIDITY", "券不在有效期");
        }
    }

    private VoucherRedemptionPreviewVO toPreview(String token, UserVoucher voucher, VoucherProduct product) {
        VoucherProductType type = product == null || product.getProductType() == null ? null : VoucherProductType.valueOf(product.getProductType());
        return new VoucherRedemptionPreviewVO(token, IdUtils.format(voucher.getId()), voucher.getVoucherCodeLast4(),
                product == null ? null : product.getTitle(), type == null ? null : type.name(),
                type == null ? null : type.label(), benefitText(product, type),
                product == null ? null : VoucherProductPresentation.validityText(product),
                product == null ? null : VoucherProductPresentation.usageRules(product), remaining(voucher),
                LocalDateTime.now().plusMinutes(5));
    }

    private String benefitText(VoucherProduct product, VoucherProductType type) {
        if (product == null || type == null) return null;
        return switch (type) {
            case CASH -> "面值 " + yuan(product.getFaceValueAmount()) + " 元"
                    + (product.getMinimumSpendAmount() == null ? "" : "，最低消费 " + yuan(product.getMinimumSpendAmount()) + " 元");
            case DISCOUNT -> "到店核销";
            case MULTI_USE -> "共 " + (product.getTotalUseCount() == null ? 1 : product.getTotalUseCount()) + " 次";
            case PACKAGE -> "按套餐明细使用";
        };
    }

    private String yuan(Long fen) {
        return fen == null ? "0.00" : String.format(Locale.ROOT, "%.2f", fen / 100.0);
    }

    private int remaining(UserVoucher voucher) {
        return voucher.getRemainingUseCount() == null ? 1 : voucher.getRemainingUseCount();
    }

    private VoucherRedemptionVO toVO(VoucherRedemption redemption) {
        UserVoucher voucher = voucherMapper.selectById(redemption.getVoucherId());
        return new VoucherRedemptionVO(IdUtils.format(redemption.getId()), IdUtils.format(redemption.getVoucherId()),
                IdUtils.format(redemption.getShopId()), IdUtils.format(redemption.getMerchantAccountId()),
                redemption.getStatus(), redemption.getUseCount(), voucher == null ? null : remaining(voucher),
                redemption.getRedeemedTime(), redemption.getReversedTime(), redemption.getReversalReason());
    }
}
