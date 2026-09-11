package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ray.config.PaymentProperties;
import com.ray.dto.VoucherPaymentDTO;
import com.ray.entity.PaymentTransaction;
import com.ray.entity.VoucherOrder;
import com.ray.enums.VoucherOrderStatus;
import com.ray.enums.VoucherPaymentStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.PaymentTransactionMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.service.CurrentUserProvider;
import com.ray.service.VoucherPaymentService;
import com.ray.service.VoucherSettlementService;
import com.ray.realtime.RealtimeEventPublisher;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.VoucherPaymentVO;
import com.ray.vo.FundLedgerEntryVO;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mock 支付实现；真实微信支付仅保留适配边界。 */
@Service
public class VoucherPaymentServiceImpl implements VoucherPaymentService {
    private final VoucherOrderMapper orderMapper;
    private final PaymentTransactionMapper transactionMapper;
    private final VoucherProductMapper productMapper;
    private final VoucherSettlementService settlementService;
    private final CurrentUserProvider currentUserProvider;
    private final RedisIdWorker idWorker;
    private final PaymentProperties properties;
    private final com.ray.service.FinanceService financeService;
    private RealtimeEventPublisher realtimeEvents;

    public VoucherPaymentServiceImpl(VoucherOrderMapper orderMapper, PaymentTransactionMapper transactionMapper,
            VoucherProductMapper productMapper, VoucherSettlementService settlementService,
            CurrentUserProvider currentUserProvider, RedisIdWorker idWorker, PaymentProperties properties,
            com.ray.service.FinanceService financeService) {
        this.orderMapper = orderMapper;
        this.transactionMapper = transactionMapper;
        this.productMapper = productMapper;
        this.settlementService = settlementService;
        this.currentUserProvider = currentUserProvider;
        this.idWorker = idWorker;
        this.properties = properties;
        this.financeService = financeService;
    }

    @Autowired(required = false)
    void setRealtimeEvents(RealtimeEventPublisher realtimeEvents) {
        this.realtimeEvents = realtimeEvents;
    }

    /** 按服务端支付模式执行订单支付，并在 Mock 成功时完成发券与账本冻结。 */
    @Transactional
    @Override
    public VoucherPaymentVO pay(Long orderId, VoucherPaymentDTO request, String idempotencyKey) {
        Long userId = currentUserProvider.requireUserId();
        if (idempotencyKey == null || idempotencyKey.isBlank())
            throw BusinessException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key 不能为空");
        VoucherOrder order = orderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<VoucherOrder>()
                .eq("id", orderId).eq("user_id", userId));
        if (order == null) throw BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在");
        PaymentTransaction existing = transactionMapper.findByOrderAndKey(orderId, idempotencyKey);
        if (existing != null) return toVO(existing, order);
        PaymentTransaction succeeded = transactionMapper.findSucceeded(orderId);
        if (succeeded != null) return toVO(succeeded, order);
        LocalDateTime now = LocalDateTime.now();
        if (!VoucherOrderStatus.PENDING_PAYMENT.name().equals(order.getStatus()))
            throw BusinessException.conflict("ORDER_STATUS_CONFLICT", "订单当前不可支付");
        if (order.getPaymentExpireTime() != null && !order.getPaymentExpireTime().isAfter(now)) {
            if (orderMapper.closeIfExpired(orderId, now) == 1 && order.getProductId() != null)
                productMapper.restoreStock(order.getProductId(), order.getQuantity() == null ? 1 : order.getQuantity());
            throw BusinessException.conflict("ORDER_PAYMENT_EXPIRED", "订单支付已超时");
        }
        String mode = properties.getMode();
        if ("DISABLED".equals(mode)) {
            return unavailable(order, mode, "支付渠道暂未启用，订单已保存，可稍后支付");
        }
        if ("WECHAT".equals(mode)) {
            return unavailable(order, mode, "微信支付尚未配置，订单已保存，可稍后支付");
        }
        String scenario = request == null || request.scenario() == null ? "MOCK_SUCCESS" : request.scenario();
        PaymentTransaction tx = new PaymentTransaction().setId(idWorker.nextId("payment-transaction"))
                .setOrderId(orderId).setUserId(userId).setIdempotencyKey(idempotencyKey).setProvider("MOCK")
                .setAmount(order.getPayAmount()).setCreatedTime(now).setUpdatedTime(now);
        if ("MOCK_FAILURE".equals(scenario)) {
            tx.setStatus(VoucherPaymentStatus.FAILED.name()).setFailureReason("模拟支付失败");
            try { transactionMapper.insert(tx); } catch (DuplicateKeyException ignored) {
                PaymentTransaction retry = transactionMapper.findByOrderAndKey(orderId, idempotencyKey);
                if (retry != null) return toVO(retry, order);
            }
            return toVO(tx, order);
        }
        tx.setStatus(VoucherPaymentStatus.SUCCEEDED.name());
        try { transactionMapper.insert(tx); } catch (DuplicateKeyException ignored) {
            PaymentTransaction retry = transactionMapper.findByOrderAndKey(orderId, idempotencyKey);
            if (retry != null) return toVO(retry, order);
            throw BusinessException.conflict("PAYMENT_IDEMPOTENCY_CONFLICT", "支付请求正在处理中，请稍后重试");
        }
        settlementService.confirmPaid(orderId, now);
        financeService.append(new FundLedgerEntryVO(null, order.getShopId().toString(), order.getId().toString(), null,
                "ORDER-" + order.getId(), "PAYMENT_FROZEN", "CREDIT", order.getPayAmount(), null, null, now));
        if (realtimeEvents != null) realtimeEvents.publish("PAYMENT_UPDATED", orderId.toString(), order.getShopId());
        return toVO(tx, orderMapper.selectById(orderId));
    }

    /** 返回当前订单可用的支付渠道和支付有效期，不改变订单状态。 */
    @Override
    public VoucherPaymentVO prepare(Long orderId) {
        Long userId = currentUserProvider.requireUserId();
        VoucherOrder order = orderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<VoucherOrder>()
                .eq("id", orderId).eq("user_id", userId));
        if (order == null) throw BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在");
        if (!VoucherOrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            throw BusinessException.conflict("ORDER_STATUS_CONFLICT", "订单当前不可支付");
        }
        if (order.getPaymentExpireTime() != null && !order.getPaymentExpireTime().isAfter(LocalDateTime.now())) {
            throw BusinessException.conflict("ORDER_PAYMENT_EXPIRED", "订单支付已超时");
        }
        String mode = properties.getMode();
        if ("DISABLED".equals(mode)) return unavailable(order, mode, "支付渠道暂未启用，订单已保存，可稍后支付");
        if ("WECHAT".equals(mode)) return unavailable(order, mode, "微信支付尚未配置，订单已保存，可稍后支付");
        return new VoucherPaymentVO("MOCK", true, "", null, null, IdUtils.format(order.getId()),
                "PENDING", order.getPayAmount(), null, order.getPaymentExpireTime());
    }

    private VoucherPaymentVO toVO(PaymentTransaction tx, VoucherOrder order) {
        return new VoucherPaymentVO(tx.getProvider() == null ? "MOCK" : tx.getProvider(), true, "", null,
                IdUtils.format(tx.getId()), IdUtils.format(tx.getOrderId()), tx.getStatus(),
                tx.getAmount(), VoucherPaymentStatus.SUCCEEDED.name().equals(tx.getStatus()) ? order.getPayTime() : null,
                order.getPaymentExpireTime());
    }

    private VoucherPaymentVO unavailable(VoucherOrder order, String mode, String message) {
        return new VoucherPaymentVO(mode, false, message, null, null,
                IdUtils.format(order.getId()), "PENDING", order.getPayAmount(), null,
                order.getPaymentExpireTime());
    }
}
