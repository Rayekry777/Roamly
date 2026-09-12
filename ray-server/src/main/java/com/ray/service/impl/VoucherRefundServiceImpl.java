package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.crypto.digest.DigestUtil;
import com.ray.constant.AdminPermissions;
import com.ray.dto.VoucherRefundDTO;
import com.ray.dto.MerchantRefundDTO;
import com.ray.dto.AdminRefundDTO;
import com.ray.dto.ConsumerRefundDTO;
import com.ray.entity.VoucherOrder;
import com.ray.entity.VoucherProduct;
import com.ray.entity.VoucherRefund;
import com.ray.entity.VoucherRefundAttempt;
import com.ray.entity.VoucherRefundItem;
import com.ray.entity.VoucherRedemption;
import com.ray.entity.UserVoucher;
import com.ray.enums.MerchantAfterSaleStage;
import com.ray.enums.UserVoucherStatus;
import com.ray.enums.VoucherOrderStatus;
import com.ray.enums.VoucherRefundStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.UserVoucherMapper;
import com.ray.mapper.PaymentTransactionMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.mapper.VoucherRefundMapper;
import com.ray.mapper.VoucherRefundAttemptMapper;
import com.ray.mapper.VoucherRefundItemMapper;
import com.ray.mapper.VoucherRedemptionMapper;
import com.ray.realtime.RealtimeEventPublisher;
import com.ray.result.PageResult;
import com.ray.service.AdminAuthService;
import com.ray.service.CurrentUserProvider;
import com.ray.service.VoucherRefundService;
import com.ray.service.MerchantAuthService;
import com.ray.entity.MerchantAccount;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.MerchantRefundCandidateVO;
import com.ray.vo.MerchantRefundCandidateVoucherVO;
import com.ray.vo.RefundTimelineEventVO;
import com.ray.vo.VoucherRefundItemVO;
import com.ray.vo.VoucherRefundVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/** 消费者单券退款申请与管理端审核状态流转实现。 */
@Service
public class VoucherRefundServiceImpl implements VoucherRefundService {
    private final VoucherRefundMapper refundMapper;
    private final VoucherRefundItemMapper refundItemMapper;
    private final VoucherRefundAttemptMapper refundAttemptMapper;
    private final UserVoucherMapper voucherMapper;
    private final VoucherOrderMapper orderMapper;
    private final VoucherProductMapper productMapper;
    private final VoucherRedemptionMapper redemptionMapper;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final MerchantAuthService merchantAuth;
    private final CurrentUserProvider userProvider;
    private final AdminAuthService adminAuth;
    private final RedisIdWorker idWorker;
    private final String mockScenario;
    private RealtimeEventPublisher realtimeEvents;
    public VoucherRefundServiceImpl(VoucherRefundMapper refundMapper,
            VoucherRefundItemMapper refundItemMapper, VoucherRefundAttemptMapper refundAttemptMapper,
            UserVoucherMapper voucherMapper,
            VoucherOrderMapper orderMapper, VoucherProductMapper productMapper,
            VoucherRedemptionMapper redemptionMapper, CurrentUserProvider userProvider,
            AdminAuthService adminAuth, RedisIdWorker idWorker, PaymentTransactionMapper paymentTransactionMapper,
            MerchantAuthService merchantAuth,
            @Value("${ray.refund.mock.outcome:SUCCESS}") String mockScenario) {
        this.refundMapper = refundMapper; this.refundItemMapper = refundItemMapper;
        this.refundAttemptMapper = refundAttemptMapper; this.voucherMapper = voucherMapper; this.orderMapper = orderMapper;
        this.productMapper = productMapper; this.redemptionMapper = redemptionMapper;
        this.userProvider = userProvider; this.adminAuth = adminAuth; this.idWorker = idWorker;
        this.paymentTransactionMapper = paymentTransactionMapper;
        this.merchantAuth = merchantAuth;
        this.mockScenario = mockScenario == null ? "SUCCESS" : mockScenario.trim().toUpperCase(Locale.ROOT);
    }
    @Autowired(required = false)
    void setRealtimeEvents(RealtimeEventPublisher realtimeEvents) { this.realtimeEvents = realtimeEvents; }
    /** 创建当前用户的单券退款申请并将券置为退款中。 */
    @Override @Transactional
    public VoucherRefundVO request(Long voucherId, VoucherRefundDTO request, String key) {
        Long userId = userProvider.requireUserId();
        if (request == null || request.quantity() == null || request.quantity() != 1)
            throw BusinessException.badRequest("REFUND_QUANTITY_INVALID", "单券退款数量只能为 1");
        UserVoucher voucher = voucherMapper.selectOne(new QueryWrapper<UserVoucher>()
                .eq("id", voucherId).eq("user_id", userId));
        if (voucher == null) throw BusinessException.notFound("USER_VOUCHER_NOT_FOUND", "用户券不存在");
        VoucherOrder order = orderMapper.selectById(voucher.getOrderId());
        if (order == null) throw BusinessException.conflict("VOUCHER_REFUND_NOT_ALLOWED", "订单不存在");
        return createForVouchers(order.getId(), List.of(voucherId), request.reasonCode(), request.description(),
                key, "CONSUMER", userId, null, userId);
    }
    /** 分页查询当前用户或管理端可见的退款记录。 */
    @Override public PageResult<VoucherRefundVO> list(String status, int page, int size, boolean admin) {
        if (admin) adminAuth.requirePermission(AdminPermissions.REFUND_MANAGE);
        else userProvider.requireUserId();
        QueryWrapper<VoucherRefund> q = new QueryWrapper<>();
        if (!admin) q.eq("user_id", userProvider.requireUserId());
        if (status != null && !status.isBlank()) { try { q.eq("status", VoucherRefundStatus.valueOf(status.toUpperCase(Locale.ROOT)).name()); } catch (IllegalArgumentException e) { throw BusinessException.badRequest("INVALID_STATUS", "退款状态无效"); } }
        Page<VoucherRefund> p = refundMapper.selectPage(new Page<>(page, size), q.orderByDesc("created_time", "id"));
        return new PageResult<>(p.getRecords().stream().map(this::toVO).toList(), page, size, p.getTotal());
    }
    /** 查询当前用户或管理端可见的退款详情。 */
    @Override public VoucherRefundVO get(Long id, boolean admin) {
        if (admin) adminAuth.requirePermission(AdminPermissions.REFUND_MANAGE);
        VoucherRefund r = refundMapper.selectById(id);
        if (r == null || (!admin && !r.getUserId().equals(userProvider.requireUserId()))) throw BusinessException.notFound("REFUND_NOT_FOUND", "退款记录不存在");
        return toVO(r);
    }
    /** 查询消费者或管理端可见的退款时间线。 */
    @Override public List<RefundTimelineEventVO> timeline(Long id, boolean admin) {
        if (admin) adminAuth.requirePermission(AdminPermissions.REFUND_MANAGE);
        VoucherRefund refund = refundMapper.selectById(id);
        if (refund == null || (!admin && !refund.getUserId().equals(userProvider.requireUserId()))) {
            throw BusinessException.notFound("REFUND_NOT_FOUND", "退款记录不存在");
        }
        return buildTimeline(refund);
    }
    /** 处理退款审批、驳回和失败重试状态迁移。 */
    @Override @Transactional public VoucherRefundVO decide(Long id, boolean approve, String reason, String key) {
        adminAuth.requirePermission(AdminPermissions.REFUND_MANAGE);
        requireIdempotencyKey(key);
        VoucherRefund r = refundMapper.findByIdForUpdate(id);
        if (r == null) throw BusinessException.notFound("REFUND_NOT_FOUND", "退款记录不存在");
        if (!VoucherRefundStatus.REQUESTED.name().equals(r.getStatus()) && !VoucherRefundStatus.FAILED.name().equals(r.getStatus())) return toVO(r);
        if (VoucherRefundStatus.REQUESTED.name().equals(r.getStatus())
                && refundAttemptMapper.findLatestByRefundId(r.getId()) != null) {
            return toVO(r);
        }
        Long reviewerId = adminAuth.currentAdminId();
        if (approve) {
            r.setStatus(VoucherRefundStatus.PROCESSING.name())
                    .setDecisionStatus(com.ray.enums.RefundDecisionStatus.MANUAL_APPROVED.name())
                    .setExecutionStatus(com.ray.enums.RefundExecutionStatus.WAITING_EXECUTION.name())
                    .setApprovedAmount(r.getAmount()).setApprovedTime(LocalDateTime.now())
                    .setCurrentHandlerId(reviewerId).setReviewerAdminId(reviewerId)
                    .setReviewNote(reason).setVersion(nextVersion(r.getVersion()));
        } else {
            // Keep the consumer's reason code in the public contract; the approval note is
            // intentionally not modeled as a consumer-editable refund reason.
            r.setStatus(VoucherRefundStatus.REJECTED.name()).setDecisionStatus(com.ray.enums.RefundDecisionStatus.REJECTED.name())
                    .setExecutionStatus(com.ray.enums.RefundExecutionStatus.NOT_STARTED.name())
                    .setRejectReason(reason).setProcessedTime(LocalDateTime.now())
                    .setCurrentHandlerId(reviewerId).setReviewerAdminId(reviewerId)
                    .setReviewNote(reason).setVersion(nextVersion(r.getVersion()));
            for (VoucherRefundItem item : refundItemMapper.findByRefundId(r.getId())) {
                int restored = voucherMapper.update(null, new UpdateWrapper<UserVoucher>().eq("id", item.getVoucherId())
                        .eq("status", UserVoucherStatus.REFUNDING.name())
                        .set("status", Boolean.TRUE.equals(item.getRedeemed())
                                ? UserVoucherStatus.USED.name() : UserVoucherStatus.UNUSED.name()));
                if (restored != 1) {
                    throw BusinessException.conflict("VOUCHER_REFUND_STATE_CONFLICT", "退款券状态已变化");
                }
                item.setStatus("FAILED");
                refundItemMapper.updateById(item);
            }
            long pending = refundMapper.selectCount(new QueryWrapper<VoucherRefund>().eq("order_id", r.getOrderId())
                    .ne("id", r.getId())
                    .in("status", VoucherRefundStatus.REQUESTED.name(), VoucherRefundStatus.PROCESSING.name()));
            if (pending == 0) {
                updateOrderAfterRejection(r.getOrderId());
            }
        }
        refundMapper.updateById(r);
        if (approve) queueExecution(r, key + ":execute");
        if (realtimeEvents != null) realtimeEvents.publish("REFUND_UPDATED", id.toString(), null);
        return toVO(r);
    }

    /**
     * 自动退款扫描：次日将已过期且仍未使用、商品允许过期退款的券转为自动退款单。
     * 幂等键固定为 AUTO-EXPIRED:{券ID}，重复扫描不会重复建单。
     */
    /** 由管理端为指定订单券发起退款，已核销券仍通过统一审核入口。 */
    @Override
    @Scheduled(cron = "${ray.refund.expired.scan-cron:0 10 0 * * *}", zone = "${ray.refund.expired.zone:Asia/Shanghai}")
    @Transactional
    public int scanExpiredVouchers() {
        LocalDateTime now = LocalDateTime.now();
        List<UserVoucher> expired = voucherMapper.selectList(new QueryWrapper<UserVoucher>()
                .in("status", UserVoucherStatus.UNUSED.name(), UserVoucherStatus.EXPIRED.name())
                .le("expire_time", now).isNotNull("expire_time")
                .last("LIMIT 200"));
        int created = 0;
        for (UserVoucher voucher : expired) {
            VoucherProduct product = productMapper.selectById(voucher.getProductId());
            if (product == null || !Boolean.TRUE.equals(product.getRefundExpired())) continue;
            VoucherOrder order = orderMapper.selectById(voucher.getOrderId());
            if (order == null || !VoucherOrderStatus.PAID.name().equals(order.getStatus())) continue;
            String key = "AUTO-EXPIRED:" + voucher.getId();
            if (refundMapper.selectOne(new QueryWrapper<VoucherRefund>().eq("idempotency_key", key)) != null) continue;
            long amount = refundAmount(order, voucher);
            VoucherRefund refund = new VoucherRefund().setId(idWorker.nextId("voucher-refund"))
                    .setVoucherId(voucher.getId()).setOrderId(order.getId())
                    .setUserId(order.getUserId()).setShopId(order.getShopId()).setSource("SYSTEM").setApplicantId(null)
                    .setAmount(amount).setApprovedAmount(amount).setStatus(VoucherRefundStatus.REQUESTED.name())
                    .setDecisionStatus(com.ray.enums.RefundDecisionStatus.AUTO_APPROVED.name())
                    .setExecutionStatus(com.ray.enums.RefundExecutionStatus.WAITING_EXECUTION.name()).setRetryCount(0)
                    .setReason("EXPIRED_AUTO").setDescription("有效期结束自动退款").setIdempotencyKey(key)
                    .setRequestedTime(now).setApprovedTime(now);
            try {
                refundMapper.insert(refund);
                createItems(refund, List.of(voucher.getId()));
                int locked = voucherMapper.update(null, new UpdateWrapper<UserVoucher>().eq("id", voucher.getId())
                        .in("status", UserVoucherStatus.UNUSED.name(), UserVoucherStatus.EXPIRED.name())
                        .set("status", UserVoucherStatus.REFUNDING.name()));
                if (locked != 1) {
                    refundItemMapper.delete(new QueryWrapper<VoucherRefundItem>().eq("refund_id", refund.getId()));
                    refundMapper.deleteById(refund.getId());
                    continue;
                }
                orderMapper.update(null, new UpdateWrapper<VoucherOrder>().eq("id", order.getId())
                        .set("after_sale_status", com.ray.enums.OrderAfterSaleStatus.REFUNDING.name()));
                queueExecution(refund, key + ":execute");
                created++;
            } catch (DuplicateKeyException ignored) {
                // 并发扫描时由唯一幂等键保证只保留一张自动退款单。
            }
        }
        return created;
    }

    /** 以字符串业务 ID 发起商户退款申请，并由服务端重新校验资格。 */
    /** 由消费者按券发起退款，所有券必须属于当前用户和同一订单。 */
    @Override
    @Transactional
    public VoucherRefundVO merchantRequest(MerchantRefundDTO request, String key) {
        merchantAuth.requirePermission(MerchantPermissionCatalog.AFTER_SALES_CREATE);
        MerchantAccount account = merchantAuth.requireCurrentAccount();
        if (account.getShopId() == null) throw BusinessException.forbidden("MERCHANT_ACTIVATION_REQUIRED", "商户账号尚未激活");
        if (request == null || request.voucherIds() == null || request.voucherIds().isEmpty())
            throw BusinessException.badRequest("REFUND_VOUCHERS_REQUIRED", "请选择要退款的券");
        Long orderId = IdUtils.parse(request.orderId(), "orderId");
        List<Long> voucherIds = request.voucherIds().stream()
                .map(id -> IdUtils.parse(id, "voucherId"))
                .toList();
        return createForVouchers(orderId, voucherIds, request.reasonCode(), request.description(),
                key, "MERCHANT", account.getId(), account.getShopId(), null);
    }

    @Override
    @Transactional
    public VoucherRefundVO adminRequest(AdminRefundDTO request, String key) {
        adminAuth.requirePermission(AdminPermissions.REFUND_MANAGE);
        Long adminId = adminAuth.currentAdminId();
        VoucherRefund replay = refundMapper.selectOne(new QueryWrapper<VoucherRefund>()
                .eq("idempotency_key", key).eq("source", "ADMIN").eq("applicant_id", adminId));
        if (replay != null) return toVO(replay);
        if (request == null || request.voucherIds() == null || request.voucherIds().isEmpty())
            throw BusinessException.badRequest("REFUND_VOUCHERS_REQUIRED", "请选择要退款的券");
        VoucherRefund created = createEntity(request.orderId(), request.voucherIds(), request.reasonCode(),
                request.description(), key, "ADMIN", adminId, null, null);
        refundMapper.insert(created);
        createItems(created, request.voucherIds());
        lockForRefund(created);
        // 管理端发起即进入统一渠道处理，仍复用审批状态机和账本逻辑。
        return decide(created.getId(), true, null, key + ":approve");
    }

    @Override
    @Transactional
    public VoucherRefundVO consumerRequest(ConsumerRefundDTO request, String key) {
        Long userId = userProvider.requireUserId();
        VoucherRefund replay = refundMapper.selectOne(new QueryWrapper<VoucherRefund>()
                .eq("idempotency_key", key).eq("source", "CONSUMER").eq("user_id", userId));
        if (replay != null) return toVO(replay);
        VoucherRefund created = createEntity(request.orderId(), request.voucherIds(), request.reasonCode(), request.description(),
                key, "CONSUMER", userId, null, userId);
        refundMapper.insert(created);
        createItems(created, request.voucherIds());
        lockForRefund(created);
        if (com.ray.enums.RefundDecisionStatus.AUTO_APPROVED.name().equals(created.getDecisionStatus())) {
            queueExecution(created, key + ":execute");
        }
        return toVO(created);
    }

    /** 按门店、聚合阶段与精确关键词分页查询售后记录。 */
    @Override
    public PageResult<VoucherRefundVO> merchantList(String status, MerchantAfterSaleStage stage, String keyword, int page, int size) {
        merchantAuth.requirePermission(MerchantPermissionCatalog.AFTER_SALES_READ);
        MerchantAccount account = merchantAuth.requireCurrentAccount();
        if (account.getShopId() == null) throw BusinessException.forbidden("MERCHANT_ACTIVATION_REQUIRED", "商户账号尚未激活");
        if (page < 1 || size < 1 || size > 100)
            throw BusinessException.badRequest("INVALID_PAGE", "page 必须大于等于1，size 必须在1到100之间");
        if (status != null && !status.isBlank() && stage != null)
            throw BusinessException.badRequest("INVALID_REFUND_FILTER", "status 与 stage 不能同时使用");
        QueryWrapper<VoucherRefund> q = new QueryWrapper<VoucherRefund>().eq("shop_id", account.getShopId());
        if (status != null && !status.isBlank()) {
            try { q.eq("status", VoucherRefundStatus.valueOf(status.toUpperCase(Locale.ROOT)).name()); }
            catch (IllegalArgumentException exception) { throw BusinessException.badRequest("INVALID_STATUS", "退款状态无效"); }
        } else if (stage != null) applyStage(q, stage);
        applyKeyword(q, keyword, account.getShopId());
        Page<VoucherRefund> result = refundMapper.selectPage(new Page<>(page, size), q.orderByDesc("created_time", "id"));
        return new PageResult<>(result.getRecords().stream().map(this::toVO).toList(), page, size, result.getTotal());
    }

    /** 按本店订单号优先、完整券码其次查询退款候选资格。 */
    @Override
    public MerchantRefundCandidateVO merchantCandidate(String keyword) {
        merchantAuth.requirePermission(MerchantPermissionCatalog.AFTER_SALES_CREATE);
        MerchantAccount account = merchantAuth.requireCurrentAccount();
        if (account.getShopId() == null) throw BusinessException.forbidden("MERCHANT_ACTIVATION_REQUIRED", "商户账号尚未激活");
        String normalized = requireKeyword(keyword);
        VoucherOrder order = null;
        UserVoucher matchedVoucher = null;
        Long possibleOrderId = tryParsePositiveId(normalized);
        if (possibleOrderId != null) {
            VoucherOrder candidate = orderMapper.selectById(possibleOrderId);
            if (candidate != null && account.getShopId().equals(candidate.getShopId())) order = candidate;
        }
        if (order == null) {
            UserVoucher byCode = voucherMapper.findByCodeHmac(DigestUtil.sha256Hex(normalized));
            if (byCode != null && account.getShopId().equals(byCode.getShopId())) {
                VoucherOrder candidate = orderMapper.selectById(byCode.getOrderId());
                if (candidate != null && account.getShopId().equals(candidate.getShopId())) { order = candidate; matchedVoucher = byCode; }
            }
        }
        if (order == null) throw BusinessException.notFound("REFUND_CANDIDATE_NOT_FOUND", "未找到本店订单或券码");
        List<UserVoucher> vouchers = voucherMapper.selectList(new QueryWrapper<UserVoucher>()
                .eq("order_id", order.getId()).eq("shop_id", account.getShopId()).orderByAsc("sequence_no", "id"));
        Set<Long> activeVoucherIds = activeRefundVoucherIds(order.getId());
        VoucherOrder locatedOrder = order;
        List<MerchantRefundCandidateVoucherVO> voucherViews = vouchers.stream()
                .map(voucher -> toCandidateVoucher(locatedOrder, voucher, activeVoucherIds)).toList();
        boolean refundable = voucherViews.stream().anyMatch(item -> Boolean.TRUE.equals(item.refundable()));
        String unavailableReason = refundable ? null : orderUnavailableReason(order, voucherViews);
        return new MerchantRefundCandidateVO(IdUtils.format(order.getId()), IdUtils.format(order.getId()), order.getProductTitle(),
                order.getStatus(), order.getQuantity() == null ? 1 : order.getQuantity(), order.getPayAmount(), refundable,
                unavailableReason, matchedVoucher == null ? null : IdUtils.format(matchedVoucher.getId()), voucherViews);
    }

    /** 查询当前门店可见的单条售后详情。 */
    @Override
    public VoucherRefundVO merchantGet(Long id) {
        merchantAuth.requirePermission(MerchantPermissionCatalog.AFTER_SALES_READ);
        MerchantAccount account = merchantAuth.requireCurrentAccount();
        VoucherRefund refund = refundMapper.selectById(id);
        if (refund == null || account.getShopId() == null || !account.getShopId().equals(refund.getShopId()))
            throw BusinessException.notFound("REFUND_NOT_FOUND", "售后记录不存在");
        return toVO(refund);
    }

    /** 查询当前门店可见的退款处理时间线。 */
    @Override
    public List<RefundTimelineEventVO> merchantTimeline(Long id) {
        merchantAuth.requirePermission(MerchantPermissionCatalog.AFTER_SALES_READ);
        MerchantAccount account = merchantAuth.requireCurrentAccount();
        VoucherRefund refund = refundMapper.selectById(id);
        if (refund == null || account.getShopId() == null || !account.getShopId().equals(refund.getShopId())) {
            throw BusinessException.notFound("REFUND_NOT_FOUND", "售后记录不存在");
        }
        return buildTimeline(refund);
    }

    private VoucherRefundVO createForVouchers(Long orderId, List<Long> voucherIds, String reason, String description,
            String key, String source, Long applicantId, Long shopId, Long userId) {
        VoucherRefund replay = refundMapper.selectOne(new QueryWrapper<VoucherRefund>()
                .eq("idempotency_key", key).eq("source", source)
                .eq(applicantId != null, "applicant_id", applicantId)
                .eq(userId != null, "user_id", userId));
        if (replay != null) return toVO(replay);
        VoucherRefund created = createEntity(orderId, voucherIds, reason, description, key, source, applicantId, shopId, userId);
        refundMapper.insert(created);
        createItems(created, voucherIds);
        lockForRefund(created);
        if (com.ray.enums.RefundDecisionStatus.AUTO_APPROVED.name().equals(created.getDecisionStatus())) {
            queueExecution(created, key + ":execute");
        }
        return toVO(created);
    }

    private void lockForRefund(VoucherRefund refund) {
        for (Long voucherId : refundVoucherIds(refund)) {
            int changed = voucherMapper.update(null, new UpdateWrapper<UserVoucher>().eq("id", voucherId)
                    .in("status", UserVoucherStatus.UNUSED.name(), UserVoucherStatus.USED.name())
                    .set("status", UserVoucherStatus.REFUNDING.name()));
            if (changed != 1) throw BusinessException.conflict("VOUCHER_REFUND_STATE_CONFLICT", "用户券状态已变化");
        }
        orderMapper.update(null, new UpdateWrapper<VoucherOrder>().eq("id", refund.getOrderId())
                .set("after_sale_status", com.ray.enums.RefundDecisionStatus.PENDING_REVIEW.name()
                        .equals(refund.getDecisionStatus())
                                ? com.ray.enums.OrderAfterSaleStatus.UNDER_REVIEW.name()
                                : com.ray.enums.OrderAfterSaleStatus.REFUNDING.name()));
    }

    private VoucherRefund createEntity(Long orderId, List<Long> voucherIds, String reason, String description,
            String key, String source, Long applicantId, Long shopId, Long userId) {
        if (key == null || key.isBlank()) throw BusinessException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key 不能为空");
        VoucherOrder order = orderMapper.selectById(orderId);
        if (order == null || !VoucherOrderStatus.PAID.name().equals(order.getStatus()))
            throw BusinessException.conflict("REFUND_ORDER_NOT_ELIGIBLE", "订单当前不可退款");
        if (shopId != null && !shopId.equals(order.getShopId())) throw BusinessException.notFound("REFUND_NOT_FOUND", "订单不属于当前门店");
        if (userId != null && !userId.equals(order.getUserId())) throw BusinessException.notFound("REFUND_NOT_FOUND", "订单不存在");
        List<Long> ids = voucherIds.stream().distinct().toList();
        if (ids.isEmpty()) throw BusinessException.badRequest("REFUND_VOUCHERS_REQUIRED", "请选择要退款的券");
        long total = order.getPayAmount() == null ? 0 : order.getPayAmount();
        int quantity = Math.max(1, order.getQuantity() == null ? 1 : order.getQuantity());
        long unit = total / quantity;
        Long firstVoucher = null;
        long amount = 0;
        boolean requiresReview = false;
        Set<Long> activeVoucherIds = activeRefundVoucherIds(orderId);
        for (int i = 0; i < ids.size(); i++) {
            UserVoucher voucher = voucherMapper.selectById(ids.get(i));
            if (voucher == null || !orderId.equals(voucher.getOrderId()))
                throw BusinessException.conflict("VOUCHER_REFUND_NOT_ALLOWED", "存在不可退款的券");
            VoucherProduct product = productMapper.selectById(voucher.getProductId());
            RefundEligibility eligibility = refundEligibility(order, voucher, product, activeVoucherIds, source);
            if (!eligibility.refundable())
                throw BusinessException.conflict("VOUCHER_REFUND_NOT_ALLOWED", eligibility.reason());
            if (firstVoucher == null) firstVoucher = voucher.getId();
            requiresReview |= UserVoucherStatus.USED.name().equals(voucher.getStatus());
            amount += unit;
            if (voucher.getSequenceNo() != null && voucher.getSequenceNo() == quantity) amount += total % quantity;
        }
        LocalDateTime now = LocalDateTime.now();
        VoucherRefund refund = new VoucherRefund().setId(idWorker.nextId("voucher-refund")).setVoucherId(firstVoucher)
                .setOrderId(orderId)
                .setUserId(userId == null ? order.getUserId() : userId).setShopId(order.getShopId())
                .setSource(source).setApplicantId(applicantId).setAmount(amount).setStatus(VoucherRefundStatus.REQUESTED.name())
                .setDecisionStatus(requiresReview
                        ? com.ray.enums.RefundDecisionStatus.PENDING_REVIEW.name()
                        : com.ray.enums.RefundDecisionStatus.AUTO_APPROVED.name())
                .setExecutionStatus(com.ray.enums.RefundExecutionStatus.WAITING_EXECUTION.name()).setRetryCount(0)
                .setReason(reason).setDescription(description).setVersion(0)
                .setIdempotencyKey(key).setRequestedTime(now);
        if (!requiresReview) {
            refund.setApprovedAmount(amount).setApprovedTime(now);
        }
        return refund;
    }

    private List<Long> refundVoucherIds(VoucherRefund refund) {
        List<VoucherRefundItem> items = refundItemMapper.findByRefundId(refund.getId());
        if (items != null && !items.isEmpty()) return items.stream().map(VoucherRefundItem::getVoucherId).toList();
        return refund.getVoucherId() == null ? List.of() : List.of(refund.getVoucherId());
    }

    private void applyStage(QueryWrapper<VoucherRefund> query, MerchantAfterSaleStage stage) {
        switch (stage) {
            case PENDING -> query.eq("status", VoucherRefundStatus.REQUESTED.name());
            case PROCESSING -> query.eq("status", VoucherRefundStatus.PROCESSING.name());
            case DECLINED -> query.in("status", VoucherRefundStatus.REJECTED.name(), VoucherRefundStatus.FAILED.name());
            case COMPLETED -> query.eq("status", VoucherRefundStatus.SUCCEEDED.name());
        }
    }

    private void applyKeyword(QueryWrapper<VoucherRefund> query, String keyword, Long shopId) {
        if (keyword == null || keyword.isBlank()) return;
        String normalized = requireKeyword(keyword);
        Long possibleId = tryParsePositiveId(normalized);
        UserVoucher byCode = voucherMapper.findByCodeHmac(DigestUtil.sha256Hex(normalized));
        Long codeVoucherId = byCode != null && shopId.equals(byCode.getShopId()) ? byCode.getId() : null;
        if (possibleId == null && codeVoucherId == null) { query.eq("id", -1L); return; }
        query.and(nested -> {
            if (possibleId != null) nested.eq("id", possibleId).or().eq("order_id", possibleId).or().eq("voucher_id", possibleId);
            if (codeVoucherId != null) {
                if (possibleId != null) nested.or();
                nested.eq("voucher_id", codeVoucherId).or().apply(
                        "EXISTS (SELECT 1 FROM voucher_refund_item ri WHERE ri.refund_id=voucher_refund.id AND ri.voucher_id={0})",
                        codeVoucherId);
            }
        });
    }

    private MerchantRefundCandidateVoucherVO toCandidateVoucher(VoucherOrder order, UserVoucher voucher, Set<Long> activeVoucherIds) {
        RefundEligibility eligibility = refundEligibility(order, voucher,
                productMapper.selectById(voucher.getProductId()), activeVoucherIds, "MERCHANT");
        return new MerchantRefundCandidateVoucherVO(IdUtils.format(voucher.getId()), voucher.getSequenceNo(), voucher.getVoucherCodeLast4(),
                voucher.getStatus(), refundAmount(order, voucher), eligibility.refundable(), eligibility.reason());
    }

    private RefundEligibility refundEligibility(VoucherOrder order, UserVoucher voucher, VoucherProduct product,
            Set<Long> activeVoucherIds, String source) {
        if (!VoucherOrderStatus.PAID.name().equals(order.getStatus())) return new RefundEligibility(false, "订单当前不可退款");
        boolean unused = UserVoucherStatus.UNUSED.name().equals(voucher.getStatus());
        boolean usedWithPlatformReview = UserVoucherStatus.USED.name().equals(voucher.getStatus())
                && !"CONSUMER".equals(source);
        if (!unused && !usedWithPlatformReview) return new RefundEligibility(false, "券当前状态不可退款");
        if (activeVoucherIds.contains(voucher.getId())) return new RefundEligibility(false, "该券已有退款记录");
        if (product == null) return new RefundEligibility(false, "退款商品不存在");
        boolean expired = voucher.getExpireTime() != null && !voucher.getExpireTime().isAfter(LocalDateTime.now());
        boolean allowed = expired ? Boolean.TRUE.equals(product.getRefundExpired()) : Boolean.TRUE.equals(product.getRefundAnytime());
        return allowed ? new RefundEligibility(true, null) : new RefundEligibility(false, "商品规则不支持退款");
    }

    private Set<Long> activeRefundVoucherIds(Long orderId) {
        return new HashSet<>(refundItemMapper.findActiveVoucherIds(orderId));
    }

    /** 为退款申请创建逐券金额快照，后续不再解析 CSV。 */
    private void createItems(VoucherRefund refund, List<Long> voucherIds) {
        for (Long voucherId : voucherIds.stream().distinct().toList()) {
            UserVoucher voucher = voucherMapper.selectById(voucherId);
            if (voucher == null) throw BusinessException.conflict("VOUCHER_REFUND_NOT_ALLOWED", "退款券不存在");
            long refundable = voucher.getCustomerPaidAmount() == null
                    ? refundAmount(orderMapper.selectById(refund.getOrderId()), voucher)
                    : voucher.getCustomerPaidAmount();
            VoucherRedemption redemption = redemptionMapper.selectOne(new QueryWrapper<VoucherRedemption>()
                    .eq("voucher_id", voucherId).eq("status", "SUCCEEDED")
                    .orderByDesc("redeemed_time", "id").last("LIMIT 1"));
            VoucherRefundItem item = new VoucherRefundItem()
                    .setId(idWorker.nextId("voucher-refund-item"))
                    .setRefundId(refund.getId()).setVoucherId(voucherId).setRedeemed(redemption != null)
                    .setSaleAmount(zero(voucher.getSaleAmount()))
                    .setCustomerPaidAmount(zero(voucher.getCustomerPaidAmount()))
                    .setPlatformSubsidyAmount(zero(voucher.getPlatformDiscountAmount()))
                    .setMerchantSubsidyAmount(zero(voucher.getMerchantSubsidyAmount()))
                    .setServiceFeeAmount(redemption == null ? 0L : zero(redemption.getServiceFeeAmount()))
                    .setRefundableAmount(refundable).setRefundAmount(0L).setStatus("PENDING")
                    .setReversedIncomeAmount(redemption == null ? 0L : zero(redemption.getEstimatedIncomeAmount()))
                    .setRefundedServiceFeeAmount(redemption == null ? 0L : zero(redemption.getServiceFeeAmount()));
            refundItemMapper.insert(item);
        }
    }

    /** 幂等创建可由可靠队列领取的退款执行尝试。 */
    private void queueExecution(VoucherRefund refund, String key) {
        if (refundAttemptMapper.findByIdempotencyKey(key) != null) return;
        VoucherRefundAttempt attempt = new VoucherRefundAttempt()
                .setId(idWorker.nextId("voucher-refund-attempt"))
                .setRefundId(refund.getId()).setIdempotencyKey(key).setStatus("WAITING")
                .setMockScenario(mockScenario)
                .setRequestAmount(refund.getApprovedAmount() == null ? refund.getAmount() : refund.getApprovedAmount())
                .setRetryCount(0);
        try {
            refundAttemptMapper.insert(attempt);
        } catch (DuplicateKeyException ignored) {
            // 同一幂等键并发入队时只保留数据库唯一约束选出的任务。
        }
    }

    private void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw BusinessException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key 不能为空");
        }
    }

    private long zero(Long value) {
        return value == null ? 0L : value;
    }

    private int nextVersion(Integer version) {
        return version == null ? 1 : version + 1;
    }

    private void updateOrderAfterRejection(Long orderId) {
        long total = voucherMapper.selectCount(new QueryWrapper<UserVoucher>().eq("order_id", orderId));
        long refunded = voucherMapper.selectCount(new QueryWrapper<UserVoucher>()
                .eq("order_id", orderId).eq("status", UserVoucherStatus.REFUNDED.name()));
        String afterSale = refunded == 0 ? com.ray.enums.OrderAfterSaleStatus.REJECTED.name()
                : refunded >= total ? com.ray.enums.OrderAfterSaleStatus.REFUNDED.name()
                : com.ray.enums.OrderAfterSaleStatus.PARTIALLY_REFUNDED.name();
        orderMapper.update(null, new UpdateWrapper<VoucherOrder>().eq("id", orderId)
                .set("after_sale_status", afterSale));
    }

    private long refundAmount(VoucherOrder order, UserVoucher voucher) {
        long total = order.getPayAmount() == null ? 0 : order.getPayAmount();
        int quantity = Math.max(1, order.getQuantity() == null ? 1 : order.getQuantity());
        return total / quantity + (voucher.getSequenceNo() != null && voucher.getSequenceNo() == quantity ? total % quantity : 0);
    }

    private String orderUnavailableReason(VoucherOrder order, List<MerchantRefundCandidateVoucherVO> vouchers) {
        if (!VoucherOrderStatus.PAID.name().equals(order.getStatus())) return "订单当前不可退款";
        if (vouchers.isEmpty()) return "订单暂无可退款券";
        return vouchers.getFirst().unavailableReason();
    }

    private String requireKeyword(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isEmpty() || normalized.length() > 64)
            throw BusinessException.badRequest("INVALID_REFUND_KEYWORD", "请输入不超过64个字符的订单号或券码");
        return normalized;
    }

    private Long tryParsePositiveId(String value) {
        try { long parsed = Long.parseLong(value); return parsed > 0 ? parsed : null; }
        catch (NumberFormatException ignored) { return null; }
    }

    private record RefundEligibility(boolean refundable, String reason) {}

    private VoucherRefundVO toVO(VoucherRefund r) {
        VoucherOrder order = orderMapper.selectById(r.getOrderId());
        VoucherProduct voucherProduct = order == null ? null : productMapper.selectById(order.getProductId());
        com.ray.entity.PaymentTransaction payment = paymentTransactionMapper.findLatestByOrder(r.getOrderId());
        String productTitle = voucherProduct == null ? null : voucherProduct.getTitle();
        String paymentChannel = payment == null ? null : payment.getProvider();
        List<VoucherRefundItem> refundItems = refundItemMapper.findByRefundId(r.getId());
        List<VoucherRefundItemVO> itemViews = refundItems == null
                ? List.of() : refundItems.stream().map(this::toItemVO).toList();
        return new VoucherRefundVO(IdUtils.format(r.getId()), IdUtils.format(r.getVoucherId()),
                IdUtils.format(r.getOrderId()), r.getAmount(), r.getStatus(), r.getReason(), reasonLabel(r.getReason()),
                r.getDescription(), productTitle, paymentChannel, IdUtils.format(r.getId()),
                IdUtils.format(r.getOrderId()), r.getRequestedTime(), r.getProcessedTime(), r.getSource(),
                r.getRejectReason(), r.getFailureCode(), r.getFailureMessage(), r.getProviderRefundNo(),
                r.getDecisionStatus(), r.getExecutionStatus(), IdUtils.format(r.getTicketId()),
                r.getExecutionStartedTime(), r.getLastFailureTime(), r.getRetryCount(),
                IdUtils.format(r.getCurrentHandlerId()), IdUtils.format(r.getReviewerAdminId()),
                r.getReviewNote(), r.getVersion(), itemViews);
    }

    private VoucherRefundItemVO toItemVO(VoucherRefundItem item) {
        return new VoucherRefundItemVO(IdUtils.format(item.getId()), IdUtils.format(item.getVoucherId()),
                Boolean.TRUE.equals(item.getRedeemed()), item.getSaleAmount(), item.getCustomerPaidAmount(),
                item.getPlatformSubsidyAmount(), item.getMerchantSubsidyAmount(), item.getServiceFeeAmount(),
                item.getRefundableAmount(), item.getRefundAmount(), item.getStatus(),
                item.getReversedIncomeAmount(), item.getRefundedServiceFeeAmount());
    }

    private List<RefundTimelineEventVO> buildTimeline(VoucherRefund refund) {
        List<RefundTimelineEventVO> timeline = new ArrayList<>();
        addTimeline(timeline, "APPLICATION_CREATED", "已提交退款申请", refund.getDecisionStatus(),
                refund.getDescription(), refund.getRequestedTime());
        if (refund.getApprovedTime() != null) {
            addTimeline(timeline, "REVIEW_COMPLETED", "退款审核完成", refund.getDecisionStatus(),
                    refund.getReviewNote(), refund.getApprovedTime());
        } else if (com.ray.enums.RefundDecisionStatus.REJECTED.name().equals(refund.getDecisionStatus())) {
            addTimeline(timeline, "REVIEW_REJECTED", "退款审核未通过", refund.getDecisionStatus(),
                    refund.getRejectReason(), refund.getProcessedTime());
        }
        List<VoucherRefundAttempt> attemptList = refundAttemptMapper.findByRefundId(refund.getId());
        if (attemptList != null) {
            for (VoucherRefundAttempt attempt : attemptList) {
                addTimeline(timeline, "EXECUTION_STARTED", "退款渠道开始处理", "PROCESSING",
                        "第 " + zeroAttempt(attempt.getRetryCount()) + " 次 Mock 执行", attempt.getStartedTime());
                addTimeline(timeline, "EXECUTION_RESULT", executionTitle(attempt.getStatus()), attempt.getStatus(),
                        attempt.getFailureMessage(), attempt.getFinishedTime());
            }
        }
        timeline.sort(Comparator.comparing(RefundTimelineEventVO::occurredAt));
        return List.copyOf(timeline);
    }

    private void addTimeline(List<RefundTimelineEventVO> timeline, String type, String title,
            String status, String description, LocalDateTime occurredAt) {
        if (occurredAt != null) {
            timeline.add(new RefundTimelineEventVO(type, title, status, description, occurredAt));
        }
    }

    private int zeroAttempt(Integer retryCount) {
        return retryCount == null || retryCount < 1 ? 1 : retryCount;
    }

    private String executionTitle(String status) {
        return switch (status == null ? "" : status) {
            case "SUCCESS" -> "退款执行成功";
            case "MANUAL_REQUIRED" -> "退款转人工处理";
            case "FAILED", "RETRY_WAITING" -> "退款执行失败";
            default -> "退款渠道状态更新";
        };
    }

    /** 将固定原因编码投影为消费者可读文案，编码仍通过 reasonCode 保留给客户端。 */
    private String reasonLabel(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) return reasonCode;
        return Map.ofEntries(
                Map.entry("PLAN_CHANGED", "计划有变没时间消费"), Map.entry("BOUGHT_WRONG", "买多了/买错了"),
                Map.entry("SAFETY_CONCERN", "担心安全问题"), Map.entry("REGRET", "后悔了，不想要了"),
                Map.entry("MISTOOK_DELIVERY", "误以为是外卖"), Map.entry("RULES_UNCLEAR", "没看清使用规则"),
                Map.entry("QUEUE_TOO_LONG", "预约不上/排队太久"), Map.entry("CANNOT_CONTACT_SHOP", "联系不上商家"),
                Map.entry("SHOP_NOT_SERVING", "商家营业但不接待"), Map.entry("SHOP_EXCEPTION", "商户发起退款"),
                Map.entry("OTHER", "其他")).getOrDefault(reasonCode, reasonCode);
    }
}
