package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ray.config.PaymentProperties;
import com.ray.dto.VoucherPaymentRequest;
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
import com.ray.utils.converter.IdUtils;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.VoucherPaymentVO;
import com.ray.vo.FundLedgerEntryVO;
import java.time.LocalDateTime;
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

    @Transactional
    @Override
    public VoucherPaymentVO pay(Long orderId, VoucherPaymentRequest request, String idempotencyKey) {
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
        if ("DISABLED".equals(mode) || "WECHAT".equals(mode))
            throw new BusinessException(503, "PAYMENT_SERVICE_UNAVAILABLE", "支付服务暂不可用");
        String scenario = request == null ? "MOCK_SUCCESS" : request.scenario();
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
        }
        settlementService.confirmPaid(orderId, now);
        financeService.append(new FundLedgerEntryVO(null, order.getShopId().toString(), order.getId().toString(), null,
                "ORDER-" + order.getId(), "PAYMENT_FROZEN", "CREDIT", order.getPayAmount(), 500, now));
        return toVO(tx, orderMapper.selectById(orderId));
    }

    private VoucherPaymentVO toVO(PaymentTransaction tx, VoucherOrder order) {
        return new VoucherPaymentVO(IdUtils.format(tx.getId()), IdUtils.format(tx.getOrderId()), tx.getStatus(),
                tx.getAmount(), VoucherPaymentStatus.SUCCEEDED.name().equals(tx.getStatus()) ? order.getPayTime() : null,
                order.getPaymentExpireTime());
    }
}
