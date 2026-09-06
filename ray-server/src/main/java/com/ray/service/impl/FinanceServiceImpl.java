package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.constant.AdminPermissions;
import com.ray.dto.CommissionRuleUpdateDTO;
import com.ray.entity.CommissionRule;
import com.ray.entity.FundLedgerEntry;
import com.ray.entity.UserVoucher;
import com.ray.entity.VoucherOrder;
import com.ray.exception.BusinessException;
import com.ray.mapper.CommissionRuleMapper;
import com.ray.mapper.FundLedgerEntryMapper;
import com.ray.mapper.SettlementItemMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherRedemptionMapper;
import com.ray.result.PageResult;
import com.ray.service.AdminAuthService;
import com.ray.service.FinanceService;
import com.ray.service.MerchantAuthService;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.CommissionRuleVO;
import com.ray.vo.FundLedgerEntryVO;
import com.ray.vo.MerchantFinanceSummaryVO;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 以团购券线上订单实付金额为基础确认收入、佣金并提供财务摘要。 */
@Service
public class FinanceServiceImpl implements FinanceService {
    private static final int DEFAULT_COMMISSION_RATE_BPS = 500;

    private final CommissionRuleMapper ruleMapper;
    private final FundLedgerEntryMapper ledgerMapper;
    private final VoucherOrderMapper orderMapper;
    private final VoucherRedemptionMapper redemptionMapper;
    private final SettlementItemMapper settlementItemMapper;
    private final AdminAuthService admin;
    private final MerchantAuthService merchant;
    private final RedisIdWorker ids;

    public FinanceServiceImpl(CommissionRuleMapper ruleMapper, FundLedgerEntryMapper ledgerMapper,
            VoucherOrderMapper orderMapper, VoucherRedemptionMapper redemptionMapper,
            SettlementItemMapper settlementItemMapper, AdminAuthService admin,
            MerchantAuthService merchant, RedisIdWorker ids) {
        this.ruleMapper = ruleMapper;
        this.ledgerMapper = ledgerMapper;
        this.orderMapper = orderMapper;
        this.redemptionMapper = redemptionMapper;
        this.settlementItemMapper = settlementItemMapper;
        this.admin = admin;
        this.merchant = merchant;
        this.ids = ids;
    }

    @Override
    public CommissionRuleVO rule(String shopId) {
        admin.requirePermission(AdminPermissions.COMMISSION_MANAGE);
        Long sid = shopId == null ? null : IdUtils.parse(shopId, "shopId");
        CommissionRule rule = ruleMapper.selectOne(new QueryWrapper<CommissionRule>()
                .eq(sid != null, "shop_id", sid).orderByDesc("effective_from").last("LIMIT 1"));
        return rule == null ? new CommissionRuleVO(null, shopId, DEFAULT_COMMISSION_RATE_BPS,
                LocalDateTime.now(), null, 0) : toRule(rule);
    }

    @Override
    @Transactional
    public CommissionRuleVO update(String shopId, CommissionRuleUpdateDTO request, String key) {
        admin.requirePermission(AdminPermissions.COMMISSION_MANAGE);
        Long sid = shopId == null ? null : IdUtils.parse(shopId, "shopId");
        CommissionRule rule = new CommissionRule().setId(ids.nextId("commission-rule")).setShopId(sid)
                .setRateBps(request.rateBps()).setEffectiveFrom(LocalDateTime.now())
                .setVersion(request.version() + 1);
        ruleMapper.insert(rule);
        return toRule(rule);
    }

    @Override
    public PageResult<FundLedgerEntryVO> ledger(int page, int size) {
        admin.requirePermission(AdminPermissions.COMMISSION_MANAGE);
        Page<FundLedgerEntry> result = ledgerMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<FundLedgerEntry>().orderByDesc("occurred_time", "id"));
        return new PageResult<>(result.getRecords().stream().map(this::toLedger).toList(), page, size, result.getTotal());
    }

    @Override
    public MerchantFinanceSummaryVO merchantSummary() {
        var account = merchant.requireCurrentAccount();
        if (account.getShopId() == null) {
            throw BusinessException.forbidden("MERCHANT_ACTIVATION_REQUIRED", "商户账号尚未激活");
        }
        var entries = ledgerMapper.selectList(new QueryWrapper<FundLedgerEntry>().eq("shop_id", account.getShopId()));
        long frozen = 0;
        long recognized = 0;
        long commission = 0;
        long refunded = 0;
        for (FundLedgerEntry entry : entries) {
            if ("PAYMENT_FROZEN".equals(entry.getEntryType())) frozen += entry.getAmount();
            if ("REDEMPTION_RECOGNIZED".equals(entry.getEntryType())) recognized += entry.getAmount();
            if ("COMMISSION_RECOGNIZED".equals(entry.getEntryType())) commission += entry.getAmount();
            if ("REDEMPTION_REVERSED".equals(entry.getEntryType())) recognized += entry.getAmount();
            if ("COMMISSION_REVERSED".equals(entry.getEntryType())) commission += entry.getAmount();
            if ("REFUND_REVERSED".equals(entry.getEntryType())) { frozen += entry.getAmount(); refunded += Math.abs(entry.getAmount()); }
        }
        long net = frozen + recognized - commission;
        return new MerchantFinanceSummaryVO(frozen, recognized, commission, net, refunded, net, 0L);
    }

    @Override
    public MerchantFinanceSummaryVO adminSummary() {
        admin.requirePermission(AdminPermissions.COMMISSION_MANAGE);
        var entries = ledgerMapper.selectList(new QueryWrapper<FundLedgerEntry>());
        long frozen = 0, recognized = 0, commission = 0, refunded = 0;
        for (FundLedgerEntry entry : entries) {
            long amount = entry.getAmount() == null ? 0 : entry.getAmount();
            if ("PAYMENT_FROZEN".equals(entry.getEntryType())) frozen += amount;
            if ("REDEMPTION_RECOGNIZED".equals(entry.getEntryType()) || "REDEMPTION_REVERSED".equals(entry.getEntryType())) recognized += amount;
            if ("COMMISSION_RECOGNIZED".equals(entry.getEntryType()) || "COMMISSION_REVERSED".equals(entry.getEntryType())) commission += amount;
            if ("REFUND_REVERSED".equals(entry.getEntryType())) { frozen += amount; refunded += Math.abs(amount); }
        }
        long net = frozen + recognized - commission;
        return new MerchantFinanceSummaryVO(frozen, recognized, commission, net, refunded, net, 0L);
    }

    @Override
    public void append(FundLedgerEntryVO entry) {
        FundLedgerEntry ledger = new FundLedgerEntry().setId(ids.nextId("ledger-entry"))
                .setShopId(entry.shopId() == null ? null : Long.valueOf(entry.shopId()))
                .setOrderId(entry.orderId() == null ? null : Long.valueOf(entry.orderId()))
                .setVoucherId(entry.voucherId() == null ? null : Long.valueOf(entry.voucherId()))
                .setBusinessEventId(entry.businessEventId()).setEntryType(entry.entryType())
                .setAccountSide(entry.accountSide()).setAmount(entry.amount())
                .setCommissionRateBps(entry.commissionRateBps())
                .setOccurredTime(entry.occurredTime() == null ? LocalDateTime.now() : entry.occurredTime());
        try {
            ledgerMapper.insert(ledger);
        } catch (DuplicateKeyException ignored) {
            // 唯一事件键保证重放返回既有事实，不重复记账。
        }
    }

    @Override
    @Transactional
    public void recognizeRedemption(Long redemptionId, UserVoucher voucher, LocalDateTime occurredAt) {
        String event = "REDEMPTION-" + redemptionId;
        if (ledgerMapper.findUnique(event, "REDEMPTION_RECOGNIZED", "CREDIT") != null) return;
        VoucherOrder order = orderMapper.selectById(voucher.getOrderId());
        if (order == null) throw BusinessException.conflict("ORDER_NOT_FOUND", "核销关联订单不存在");
        int quantity = positive(order.getQuantity());
        int sequence = voucher.getSequenceNo() == null ? 1 : voucher.getSequenceNo();
        long voucherAmount = splitPart(order.getPayAmount() == null ? 0 : order.getPayAmount(), quantity, sequence);
        int uses = positive(voucher.getTotalUseCount());
        int usedIndex = (int) redemptionMapper.countSucceeded(voucher.getId());
        long recognized = splitPart(voucherAmount, uses, usedIndex);
        int rate = paymentRate(order);
        long commissionTotal = voucherAmount * rate / 10_000;
        long commission = splitPart(commissionTotal, uses, usedIndex);
        append(new FundLedgerEntryVO(null, order.getShopId().toString(), order.getId().toString(),
                voucher.getId().toString(), event, "REDEMPTION_RECOGNIZED", "CREDIT", recognized, rate, occurredAt));
        append(new FundLedgerEntryVO(null, order.getShopId().toString(), order.getId().toString(),
                voucher.getId().toString(), event, "COMMISSION_RECOGNIZED", "DEBIT", commission, rate, occurredAt));
    }

    @Override
    @Transactional
    public void reverseRedemption(Long redemptionId, UserVoucher voucher, LocalDateTime occurredAt) {
        VoucherOrder order = orderMapper.selectById(voucher.getOrderId());
        if (order == null) throw BusinessException.conflict("ORDER_NOT_FOUND", "核销关联订单不存在");
        String originalEvent = "REDEMPTION-" + redemptionId;
        FundLedgerEntry recognized = ledgerMapper.findUnique(originalEvent, "REDEMPTION_RECOGNIZED", "CREDIT");
        FundLedgerEntry commission = ledgerMapper.findUnique(originalEvent, "COMMISSION_RECOGNIZED", "DEBIT");
        String event = "REDEMPTION-REVERSAL-" + redemptionId;
        if (recognized != null) {
            append(new FundLedgerEntryVO(null, order.getShopId().toString(), order.getId().toString(),
                    voucher.getId().toString(), event, "REDEMPTION_REVERSED", "DEBIT", -recognized.getAmount(),
                    recognized.getCommissionRateBps(), occurredAt));
        }
        if (commission != null) {
            append(new FundLedgerEntryVO(null, order.getShopId().toString(), order.getId().toString(),
                    voucher.getId().toString(), event, "COMMISSION_REVERSED", "CREDIT", -commission.getAmount(),
                    commission.getCommissionRateBps(), occurredAt));
        }
    }

    @Override
    public boolean isRedemptionSettled(Long redemptionId) {
        return settlementItemMapper.existsForLedgerEvent("REDEMPTION-" + redemptionId);
    }

    private int paymentRate(VoucherOrder order) {
        FundLedgerEntry frozen = ledgerMapper.findUnique("ORDER-" + order.getId(), "PAYMENT_FROZEN", "CREDIT");
        return frozen == null || frozen.getCommissionRateBps() == null
                ? DEFAULT_COMMISSION_RATE_BPS : frozen.getCommissionRateBps();
    }

    private int positive(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }

    private long splitPart(long amount, int parts, int index) {
        int safeParts = Math.max(1, parts);
        int safeIndex = Math.min(Math.max(1, index), safeParts);
        long base = amount / safeParts;
        return safeIndex == safeParts ? base + amount % safeParts : base;
    }

    private CommissionRuleVO toRule(CommissionRule rule) {
        return new CommissionRuleVO(IdUtils.format(rule.getId()), rule.getShopId() == null ? null : IdUtils.format(rule.getShopId()),
                rule.getRateBps(), rule.getEffectiveFrom(), rule.getEffectiveTo(), rule.getVersion());
    }

    private FundLedgerEntryVO toLedger(FundLedgerEntry entry) {
        return new FundLedgerEntryVO(IdUtils.format(entry.getId()), IdUtils.format(entry.getShopId()),
                IdUtils.format(entry.getOrderId()), IdUtils.format(entry.getVoucherId()), entry.getBusinessEventId(),
                entry.getEntryType(), entry.getAccountSide(), entry.getAmount(), entry.getCommissionRateBps(),
                entry.getOccurredTime());
    }
}
