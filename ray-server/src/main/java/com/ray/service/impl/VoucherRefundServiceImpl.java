package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.constant.AdminPermissions;
import com.ray.dto.VoucherRefundRequest;
import com.ray.entity.VoucherOrder;
import com.ray.entity.VoucherProduct;
import com.ray.entity.VoucherRefund;
import com.ray.entity.UserVoucher;
import com.ray.enums.UserVoucherStatus;
import com.ray.enums.VoucherOrderStatus;
import com.ray.enums.VoucherRefundStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.UserVoucherMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.mapper.VoucherRefundMapper;
import com.ray.realtime.RealtimeEventPublisher;
import com.ray.result.PageResult;
import com.ray.service.AdminAuthService;
import com.ray.service.CurrentUserProvider;
import com.ray.service.VoucherRefundService;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.VoucherRefundVO;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoucherRefundServiceImpl implements VoucherRefundService {
    private final VoucherRefundMapper refundMapper;
    private final UserVoucherMapper voucherMapper;
    private final VoucherOrderMapper orderMapper;
    private final VoucherProductMapper productMapper;
    private final CurrentUserProvider userProvider;
    private final AdminAuthService adminAuth;
    private final RedisIdWorker idWorker;
    private RealtimeEventPublisher realtimeEvents;
    public VoucherRefundServiceImpl(VoucherRefundMapper refundMapper, UserVoucherMapper voucherMapper,
            VoucherOrderMapper orderMapper, VoucherProductMapper productMapper, CurrentUserProvider userProvider,
            AdminAuthService adminAuth, RedisIdWorker idWorker) {
        this.refundMapper = refundMapper; this.voucherMapper = voucherMapper; this.orderMapper = orderMapper;
        this.productMapper = productMapper; this.userProvider = userProvider; this.adminAuth = adminAuth; this.idWorker = idWorker;
    }
    @Autowired(required = false)
    void setRealtimeEvents(RealtimeEventPublisher realtimeEvents) { this.realtimeEvents = realtimeEvents; }
    @Override @Transactional
    public VoucherRefundVO request(Long voucherId, VoucherRefundRequest request, String key) {
        Long userId = userProvider.requireUserId();
        if (key == null || key.isBlank()) throw BusinessException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key 不能为空");
        UserVoucher voucher = voucherMapper.selectOne(new QueryWrapper<UserVoucher>().eq("id", voucherId).eq("user_id", userId));
        if (voucher == null) throw BusinessException.notFound("USER_VOUCHER_NOT_FOUND", "用户券不存在");
        VoucherRefund old = refundMapper.selectOne(new QueryWrapper<VoucherRefund>().eq("voucher_id", voucherId).eq("idempotency_key", key));
        if (old != null) return toVO(old);
        if (!UserVoucherStatus.UNUSED.name().equals(voucher.getStatus())) throw BusinessException.conflict("VOUCHER_REFUND_NOT_ALLOWED", "当前券状态不可退款");
        VoucherOrder order = orderMapper.selectById(voucher.getOrderId());
        VoucherProduct product = order == null ? null : productMapper.selectById(voucher.getProductId());
        if (order == null || product == null) throw BusinessException.conflict("VOUCHER_REFUND_NOT_ALLOWED", "订单或商品不存在");
        boolean expired = voucher.getExpireTime() != null && !voucher.getExpireTime().isAfter(LocalDateTime.now());
        if (expired ? !Boolean.TRUE.equals(product.getRefundExpired()) : !Boolean.TRUE.equals(product.getRefundAnytime()))
            throw BusinessException.conflict("VOUCHER_REFUND_NOT_ALLOWED", "商品规则不支持退款");
        int quantity = order.getQuantity() == null || order.getQuantity() < 1 ? 1 : order.getQuantity();
        long amount = (order.getPayAmount() == null ? 0 : order.getPayAmount()) / quantity;
        VoucherRefund refund = new VoucherRefund().setId(idWorker.nextId("voucher-refund")).setVoucherId(voucherId)
                .setOrderId(order.getId()).setUserId(userId).setAmount(amount).setStatus(VoucherRefundStatus.SUCCEEDED.name())
                .setReason(request == null ? null : request.reason()).setIdempotencyKey(key)
                .setRequestedTime(LocalDateTime.now()).setProcessedTime(LocalDateTime.now());
        try { refundMapper.insert(refund); } catch (DuplicateKeyException ex) {
            VoucherRefund retry = refundMapper.selectOne(new QueryWrapper<VoucherRefund>().eq("voucher_id", voucherId).eq("idempotency_key", key));
            if (retry != null) return toVO(retry); throw ex;
        }
        voucherMapper.update(null, new UpdateWrapper<UserVoucher>().eq("id", voucherId).eq("status", UserVoucherStatus.UNUSED.name()).set("status", UserVoucherStatus.REFUNDED.name()).set("refund_time", LocalDateTime.now()));
        long refunded = refundMapper.selectCount(new QueryWrapper<VoucherRefund>().eq("order_id", order.getId()).eq("status", VoucherRefundStatus.SUCCEEDED.name()));
        if (refunded >= quantity) orderMapper.update(null, new UpdateWrapper<VoucherOrder>().eq("id", order.getId()).set("status", VoucherOrderStatus.REFUNDED.name()).set("refund_time", LocalDateTime.now()));
        if (realtimeEvents != null) realtimeEvents.publish("REFUND_UPDATED", voucherId.toString(), order.getShopId());
        return toVO(refund);
    }
    @Override public PageResult<VoucherRefundVO> list(String status, int page, int size, boolean admin) {
        if (admin) adminAuth.requirePermission(AdminPermissions.REFUND_MANAGE);
        else userProvider.requireUserId();
        QueryWrapper<VoucherRefund> q = new QueryWrapper<>();
        if (!admin) q.eq("user_id", userProvider.requireUserId());
        if (status != null && !status.isBlank()) { try { q.eq("status", VoucherRefundStatus.valueOf(status.toUpperCase(Locale.ROOT)).name()); } catch (IllegalArgumentException e) { throw BusinessException.badRequest("INVALID_STATUS", "退款状态无效"); } }
        Page<VoucherRefund> p = refundMapper.selectPage(new Page<>(page, size), q.orderByDesc("created_time", "id"));
        return new PageResult<>(p.getRecords().stream().map(this::toVO).toList(), page, size, p.getTotal());
    }
    @Override public VoucherRefundVO get(Long id, boolean admin) {
        if (admin) adminAuth.requirePermission(AdminPermissions.REFUND_MANAGE);
        VoucherRefund r = refundMapper.selectById(id);
        if (r == null || (!admin && !r.getUserId().equals(userProvider.requireUserId()))) throw BusinessException.notFound("REFUND_NOT_FOUND", "退款记录不存在");
        return toVO(r);
    }
    @Override @Transactional public VoucherRefundVO decide(Long id, boolean approve, String reason, String key) {
        adminAuth.requirePermission(AdminPermissions.REFUND_MANAGE);
        VoucherRefund r = refundMapper.selectById(id); if (r == null) throw BusinessException.notFound("REFUND_NOT_FOUND", "退款记录不存在");
        if (!VoucherRefundStatus.REQUESTED.name().equals(r.getStatus()) && !VoucherRefundStatus.FAILED.name().equals(r.getStatus())) return toVO(r);
        r.setStatus((approve ? VoucherRefundStatus.SUCCEEDED : VoucherRefundStatus.REJECTED).name()).setReason(reason).setProcessedTime(LocalDateTime.now());
        refundMapper.updateById(r);
        if (realtimeEvents != null) realtimeEvents.publish("REFUND_UPDATED", id.toString(), null);
        return toVO(r);
    }
    private VoucherRefundVO toVO(VoucherRefund r) { return new VoucherRefundVO(IdUtils.format(r.getId()), IdUtils.format(r.getVoucherId()), IdUtils.format(r.getOrderId()), r.getAmount(), r.getStatus(), r.getReason(), r.getRequestedTime(), r.getProcessedTime()); }
}
