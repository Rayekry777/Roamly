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
import com.ray.realtime.RealtimeEventPublisher;
import com.ray.result.PageResult;
import com.ray.service.AdminAuthService;
import com.ray.service.CurrentUserProvider;
import com.ray.service.VoucherRefundService;
import com.ray.service.FinanceService;
import com.ray.service.MerchantAuthService;
import com.ray.entity.MerchantAccount;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.MerchantRefundCandidateVO;
import com.ray.vo.MerchantRefundCandidateVoucherVO;
import com.ray.vo.VoucherRefundVO;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 消费者单券退款申请与管理端审核状态流转实现。 */
@Service
public class VoucherRefundServiceImpl implements VoucherRefundService {
    private static final List<String> ACTIVE_REFUND_STATUSES = List.of(
            VoucherRefundStatus.REQUESTED.name(), VoucherRefundStatus.PROCESSING.name(), VoucherRefundStatus.SUCCEEDED.name());
    private final VoucherRefundMapper refundMapper;
    private final UserVoucherMapper voucherMapper;
    private final VoucherOrderMapper orderMapper;
    private final VoucherProductMapper productMapper;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final MerchantAuthService merchantAuth;
    private final FinanceService finance;
    private final CurrentUserProvider userProvider;
    private final AdminAuthService adminAuth;
    private final RedisIdWorker idWorker;
    private RealtimeEventPublisher realtimeEvents;
    public VoucherRefundServiceImpl(VoucherRefundMapper refundMapper, UserVoucherMapper voucherMapper,
            VoucherOrderMapper orderMapper, VoucherProductMapper productMapper, CurrentUserProvider userProvider,
            AdminAuthService adminAuth, RedisIdWorker idWorker, PaymentTransactionMapper paymentTransactionMapper,
            MerchantAuthService merchantAuth, FinanceService finance) {
        this.refundMapper = refundMapper; this.voucherMapper = voucherMapper; this.orderMapper = orderMapper;
        this.productMapper = productMapper; this.userProvider = userProvider; this.adminAuth = adminAuth; this.idWorker = idWorker;
        this.paymentTransactionMapper = paymentTransactionMapper;
        this.merchantAuth = merchantAuth;
        this.finance = finance;
    }
    @Autowired(required = false)
    void setRealtimeEvents(RealtimeEventPublisher realtimeEvents) { this.realtimeEvents = realtimeEvents; }
    /** 创建当前用户的单券退款申请并将券置为退款中。 */
    @Override @Transactional
    public VoucherRefundVO request(Long voucherId, VoucherRefundDTO request, String key) {
        Long userId = userProvider.requireUserId();
        if (key == null || key.isBlank()) throw BusinessException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key 不能为空");
        UserVoucher voucher = voucherMapper.selectOne(new QueryWrapper<UserVoucher>().eq("id", voucherId).eq("user_id", userId));
        if (voucher == null) throw BusinessException.notFound("USER_VOUCHER_NOT_FOUND", "用户券不存在");
        VoucherRefund old = refundMapper.selectOne(new QueryWrapper<VoucherRefund>().eq("voucher_id", voucherId).eq("idempotency_key", key));
        if (old != null) return toVO(old);
        if (!UserVoucherStatus.UNUSED.name().equals(voucher.getStatus())) throw BusinessException.conflict("VOUCHER_REFUND_NOT_ALLOWED", "当前券状态不可退款");
        if (request == null || request.quantity() == null || request.quantity() != 1)
            throw BusinessException.badRequest("REFUND_QUANTITY_INVALID", "单券退款数量只能为 1");
        VoucherRefund active = refundMapper.selectOne(new QueryWrapper<VoucherRefund>().eq("voucher_id", voucherId)
                .in("status", VoucherRefundStatus.REQUESTED.name(), VoucherRefundStatus.PROCESSING.name(), VoucherRefundStatus.SUCCEEDED.name()));
        if (active != null) throw BusinessException.conflict("VOUCHER_REFUND_ALREADY_EXISTS", "该券已有退款记录");
        VoucherOrder order = orderMapper.selectById(voucher.getOrderId());
        VoucherProduct product = order == null ? null : productMapper.selectById(voucher.getProductId());
        if (order == null || product == null) throw BusinessException.conflict("VOUCHER_REFUND_NOT_ALLOWED", "订单或商品不存在");
        boolean expired = voucher.getExpireTime() != null && !voucher.getExpireTime().isAfter(LocalDateTime.now());
        if (expired ? !Boolean.TRUE.equals(product.getRefundExpired()) : !Boolean.TRUE.equals(product.getRefundAnytime()))
            throw BusinessException.conflict("VOUCHER_REFUND_NOT_ALLOWED", "商品规则不支持退款");
        int quantity = order.getQuantity() == null || order.getQuantity() < 1 ? 1 : order.getQuantity();
        long amount = (order.getPayAmount() == null ? 0 : order.getPayAmount()) / quantity;
        VoucherRefund refund = new VoucherRefund().setId(idWorker.nextId("voucher-refund")).setVoucherId(voucherId)
                .setOrderId(order.getId()).setVoucherIds(String.valueOf(voucherId)).setUserId(userId).setShopId(order.getShopId()).setSource("CONSUMER").setApplicantId(userId)
                .setAmount(amount).setStatus(VoucherRefundStatus.REQUESTED.name())
                .setReason(request.reasonCode()).setDescription(request.description()).setIdempotencyKey(key)
                .setRequestedTime(LocalDateTime.now());
        try { refundMapper.insert(refund); } catch (DuplicateKeyException ex) {
            VoucherRefund retry = refundMapper.selectOne(new QueryWrapper<VoucherRefund>().eq("voucher_id", voucherId).eq("idempotency_key", key));
            if (retry != null) return toVO(retry); throw ex;
        }
        int markedRefunding = voucherMapper.update(null, new UpdateWrapper<UserVoucher>().eq("id", voucherId)
                .eq("status", UserVoucherStatus.UNUSED.name()).set("status", UserVoucherStatus.REFUNDING.name()));
        if (markedRefunding != 1) throw BusinessException.conflict("VOUCHER_REFUND_STATE_CONFLICT", "用户券状态已变化");
        orderMapper.update(null, new UpdateWrapper<VoucherOrder>().eq("id", order.getId())
                .eq("status", VoucherOrderStatus.PAID.name()).set("status", VoucherOrderStatus.REFUNDING.name()));
        if (realtimeEvents != null) realtimeEvents.publish("REFUND_UPDATED", voucherId.toString(), order.getShopId());
        return toVO(refund);
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
    /** 处理退款审批、驳回和失败重试状态迁移。 */
    @Override @Transactional public VoucherRefundVO decide(Long id, boolean approve, String reason, String key) {
        adminAuth.requirePermission(AdminPermissions.REFUND_MANAGE);
        VoucherRefund r = refundMapper.selectById(id); if (r == null) throw BusinessException.notFound("REFUND_NOT_FOUND", "退款记录不存在");
        if (!VoucherRefundStatus.REQUESTED.name().equals(r.getStatus()) && !VoucherRefundStatus.FAILED.name().equals(r.getStatus())) return toVO(r);
        if (approve) {
            r.setStatus(VoucherRefundStatus.PROCESSING.name());
            r.setApprovedAmount(r.getAmount()).setApprovedTime(LocalDateTime.now());
            refundMapper.updateById(r);
            int updated = 0;
            for (Long voucherId : refundVoucherIds(r)) updated += voucherMapper.update(null, new UpdateWrapper<UserVoucher>().eq("id", voucherId)
                    .eq("status", UserVoucherStatus.REFUNDING.name()).set("status", UserVoucherStatus.REFUNDED.name())
                    .set("refund_time", LocalDateTime.now()));
            if (updated != refundVoucherIds(r).size()) throw BusinessException.conflict("VOUCHER_REFUND_STATE_CONFLICT", "用户券状态已变化");
            r.setStatus(VoucherRefundStatus.SUCCEEDED.name()).setProcessedTime(LocalDateTime.now());
            r.setPaymentProvider(paymentTransactionMapper.findLatestByOrder(r.getOrderId()) == null ? null
                    : paymentTransactionMapper.findLatestByOrder(r.getOrderId()).getProvider());
            finance.append(new com.ray.vo.FundLedgerEntryVO(null,
                    r.getShopId() == null ? null : r.getShopId().toString(), r.getOrderId().toString(),
                    r.getVoucherId().toString(), "REFUND-" + r.getId(), "REFUND_REVERSED", "DEBIT",
                    -Math.abs(r.getAmount()), null, LocalDateTime.now()));
            long refunded = refundMapper.selectCount(new QueryWrapper<VoucherRefund>().eq("order_id", r.getOrderId())
                    .eq("status", VoucherRefundStatus.SUCCEEDED.name()));
            VoucherOrder order = orderMapper.selectById(r.getOrderId());
            int quantity = order == null || order.getQuantity() == null ? 1 : order.getQuantity();
            if (refunded >= quantity) {
                orderMapper.update(null, new UpdateWrapper<VoucherOrder>().eq("id", r.getOrderId())
                        .set("status", VoucherOrderStatus.REFUNDED.name()).set("refund_time", LocalDateTime.now()));
                paymentTransactionMapper.update(null, new UpdateWrapper<com.ray.entity.PaymentTransaction>()
                        .eq("order_id", r.getOrderId()).eq("status", "SUCCEEDED").set("status", "REFUNDED"));
            } else {
                paymentTransactionMapper.update(null, new UpdateWrapper<com.ray.entity.PaymentTransaction>()
                        .eq("order_id", r.getOrderId()).eq("status", "SUCCEEDED").set("status", "PARTIALLY_REFUNDED"));
            }
        } else {
            // Keep the consumer's reason code in the public contract; the approval note is
            // intentionally not modeled as a consumer-editable refund reason.
            r.setStatus(VoucherRefundStatus.REJECTED.name()).setRejectReason(reason).setProcessedTime(LocalDateTime.now());
            for (Long voucherId : refundVoucherIds(r)) {
                voucherMapper.update(null, new UpdateWrapper<UserVoucher>().eq("id", voucherId)
                        .eq("status", UserVoucherStatus.REFUNDING.name()).set("status", UserVoucherStatus.UNUSED.name()));
            }
            long pending = refundMapper.selectCount(new QueryWrapper<VoucherRefund>().eq("order_id", r.getOrderId())
                    .in("status", VoucherRefundStatus.REQUESTED.name(), VoucherRefundStatus.PROCESSING.name()));
            if (pending == 0) {
                orderMapper.update(null, new UpdateWrapper<VoucherOrder>().eq("id", r.getOrderId())
                        .eq("status", VoucherOrderStatus.REFUNDING.name()).set("status", VoucherOrderStatus.PAID.name()));
            }
        }
        refundMapper.updateById(r);
        if (realtimeEvents != null) realtimeEvents.publish("REFUND_UPDATED", id.toString(), null);
        return toVO(r);
    }

    /** 以字符串业务 ID 发起商户退款申请，并由服务端重新校验资格。 */
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
        VoucherRefund replay = refundMapper.selectOne(new QueryWrapper<VoucherRefund>().eq("idempotency_key", key));
        if (replay != null) return toVO(replay);
        if (request == null || request.voucherIds() == null || request.voucherIds().isEmpty())
            throw BusinessException.badRequest("REFUND_VOUCHERS_REQUIRED", "请选择要退款的券");
        VoucherRefund created = createEntity(request.orderId(), request.voucherIds(), request.reasonCode(),
                request.description(), key, "ADMIN", null, null, null);
        refundMapper.insert(created);
        lockForRefund(created);
        // 管理端发起即进入统一渠道处理，仍复用审批状态机和账本逻辑。
        return decide(created.getId(), true, null, key + ":approve");
    }

    @Override
    @Transactional
    public VoucherRefundVO consumerRequest(ConsumerRefundDTO request, String key) {
        Long userId = userProvider.requireUserId();
        VoucherRefund replay = refundMapper.selectOne(new QueryWrapper<VoucherRefund>().eq("idempotency_key", key));
        if (replay != null) return toVO(replay);
        VoucherRefund created = createEntity(request.orderId(), request.voucherIds(), request.reasonCode(), request.description(),
                key, "CONSUMER", userId, null, userId);
        refundMapper.insert(created);
        lockForRefund(created);
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

    private VoucherRefundVO createForVouchers(Long orderId, List<Long> voucherIds, String reason, String description,
            String key, String source, Long applicantId, Long shopId, Long userId) {
        VoucherRefund replay = refundMapper.selectOne(new QueryWrapper<VoucherRefund>().eq("idempotency_key", key));
        if (replay != null) return toVO(replay);
        VoucherRefund created = createEntity(orderId, voucherIds, reason, description, key, source, applicantId, shopId, userId);
        refundMapper.insert(created);
        lockForRefund(created);
        return toVO(created);
    }

    private void lockForRefund(VoucherRefund refund) {
        for (Long voucherId : refundVoucherIds(refund)) {
            int changed = voucherMapper.update(null, new UpdateWrapper<UserVoucher>().eq("id", voucherId)
                    .eq("status", UserVoucherStatus.UNUSED.name()).set("status", UserVoucherStatus.REFUNDING.name()));
            if (changed != 1) throw BusinessException.conflict("VOUCHER_REFUND_STATE_CONFLICT", "用户券状态已变化");
        }
        orderMapper.update(null, new UpdateWrapper<VoucherOrder>().eq("id", refund.getOrderId())
                .eq("status", VoucherOrderStatus.PAID.name()).set("status", VoucherOrderStatus.REFUNDING.name()));
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
        Set<Long> activeVoucherIds = activeRefundVoucherIds(orderId);
        for (int i = 0; i < ids.size(); i++) {
            UserVoucher voucher = voucherMapper.selectById(ids.get(i));
            if (voucher == null || !orderId.equals(voucher.getOrderId()))
                throw BusinessException.conflict("VOUCHER_REFUND_NOT_ALLOWED", "存在不可退款的券");
            VoucherProduct product = productMapper.selectById(voucher.getProductId());
            RefundEligibility eligibility = refundEligibility(order, voucher, product, activeVoucherIds);
            if (!eligibility.refundable())
                throw BusinessException.conflict("VOUCHER_REFUND_NOT_ALLOWED", eligibility.reason());
            if (firstVoucher == null) firstVoucher = voucher.getId();
            amount += unit;
            if (voucher.getSequenceNo() != null && voucher.getSequenceNo() == quantity) amount += total % quantity;
        }
        return new VoucherRefund().setId(idWorker.nextId("voucher-refund")).setVoucherId(firstVoucher)
                .setOrderId(orderId).setVoucherIds(ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")))
                .setUserId(userId == null ? order.getUserId() : userId).setShopId(order.getShopId())
                .setSource(source).setApplicantId(applicantId).setAmount(amount).setStatus(VoucherRefundStatus.REQUESTED.name())
                .setReason(reason).setDescription(description).setIdempotencyKey(key).setRequestedTime(LocalDateTime.now());
    }

    private List<Long> refundVoucherIds(VoucherRefund refund) {
        if (refund.getVoucherIds() == null || refund.getVoucherIds().isBlank()) return List.of(refund.getVoucherId());
        return java.util.Arrays.stream(refund.getVoucherIds().split(",")).map(String::trim).filter(v -> !v.isEmpty()).map(Long::valueOf).toList();
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
                nested.eq("voucher_id", codeVoucherId).or().apply("FIND_IN_SET({0}, voucher_ids)", codeVoucherId);
            }
        });
    }

    private MerchantRefundCandidateVoucherVO toCandidateVoucher(VoucherOrder order, UserVoucher voucher, Set<Long> activeVoucherIds) {
        RefundEligibility eligibility = refundEligibility(order, voucher, productMapper.selectById(voucher.getProductId()), activeVoucherIds);
        return new MerchantRefundCandidateVoucherVO(IdUtils.format(voucher.getId()), voucher.getSequenceNo(), voucher.getVoucherCodeLast4(),
                voucher.getStatus(), refundAmount(order, voucher), eligibility.refundable(), eligibility.reason());
    }

    private RefundEligibility refundEligibility(VoucherOrder order, UserVoucher voucher, VoucherProduct product, Set<Long> activeVoucherIds) {
        if (!VoucherOrderStatus.PAID.name().equals(order.getStatus())) return new RefundEligibility(false, "订单当前不可退款");
        if (!UserVoucherStatus.UNUSED.name().equals(voucher.getStatus())) return new RefundEligibility(false, "券当前状态不可退款");
        if (activeVoucherIds.contains(voucher.getId())) return new RefundEligibility(false, "该券已有退款记录");
        if (product == null) return new RefundEligibility(false, "退款商品不存在");
        boolean expired = voucher.getExpireTime() != null && !voucher.getExpireTime().isAfter(LocalDateTime.now());
        boolean allowed = expired ? Boolean.TRUE.equals(product.getRefundExpired()) : Boolean.TRUE.equals(product.getRefundAnytime());
        return allowed ? new RefundEligibility(true, null) : new RefundEligibility(false, "商品规则不支持退款");
    }

    private Set<Long> activeRefundVoucherIds(Long orderId) {
        Set<Long> result = new HashSet<>();
        List<VoucherRefund> refunds = refundMapper.selectList(new QueryWrapper<VoucherRefund>().eq("order_id", orderId).in("status", ACTIVE_REFUND_STATUSES));
        for (VoucherRefund refund : refunds) result.addAll(refundVoucherIds(refund));
        return result;
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
        return new VoucherRefundVO(IdUtils.format(r.getId()), IdUtils.format(r.getVoucherId()), IdUtils.format(r.getOrderId()),
                r.getAmount(), r.getStatus(), r.getReason(), reasonLabel(r.getReason()), r.getDescription(), productTitle, paymentChannel,
                IdUtils.format(r.getId()), IdUtils.format(r.getOrderId()), r.getRequestedTime(), r.getProcessedTime(),
                r.getSource(), r.getRejectReason(), r.getFailureCode(), r.getFailureMessage(), r.getProviderRefundNo());
    }

    /** 将固定原因编码投影为消费者可读文案，编码仍通过 reasonCode 保留给客户端。 */
    private String reasonLabel(String reasonCode) {
        return Map.ofEntries(
                Map.entry("PLAN_CHANGED", "计划有变没时间消费"), Map.entry("BOUGHT_WRONG", "买多了/买错了"),
                Map.entry("SAFETY_CONCERN", "担心安全问题"), Map.entry("REGRET", "后悔了，不想要了"),
                Map.entry("MISTOOK_DELIVERY", "误以为是外卖"), Map.entry("RULES_UNCLEAR", "没看清使用规则"),
                Map.entry("QUEUE_TOO_LONG", "预约不上/排队太久"), Map.entry("CANNOT_CONTACT_SHOP", "联系不上商家"),
                Map.entry("SHOP_NOT_SERVING", "商家营业但不接待"), Map.entry("SHOP_EXCEPTION", "商户发起退款"),
                Map.entry("OTHER", "其他")).getOrDefault(reasonCode, reasonCode);
    }
}
