package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.constant.AdminPermissions;
import com.ray.dto.CommissionRuleUpdateDTO;
import com.ray.entity.CommissionRule;
import com.ray.entity.FundLedgerEntry;
import com.ray.entity.UserVoucher;
import com.ray.entity.VoucherOrder;
import com.ray.entity.VoucherRedemption;
import com.ray.entity.VoucherRefund;
import com.ray.exception.BusinessException;
import com.ray.mapper.CommissionRuleMapper;
import com.ray.mapper.FundLedgerEntryMapper;
import com.ray.mapper.SettlementItemMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherRedemptionMapper;
import com.ray.mapper.VoucherRefundMapper;
import com.ray.result.PageResult;
import com.ray.service.AdminAuthService;
import com.ray.service.FinanceService;
import com.ray.service.MerchantAuthService;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.CommissionRuleVO;
import com.ray.vo.FundLedgerEntryVO;
import com.ray.vo.MerchantFinanceSummaryVO;
import com.ray.vo.MerchantTodayFinanceVO;
import com.ray.vo.ServiceFeePolicyVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 以核销快照确认收入和软件服务费，并维护只追加资金账本。 */
@Slf4j
@Service
public class FinanceServiceImpl implements FinanceService {
    private static final int DEFAULT_SERVICE_FEE_RATE_BPS = 500;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final CommissionRuleMapper ruleMapper;
    private final FundLedgerEntryMapper ledgerMapper;
    private final VoucherOrderMapper orderMapper;
    private final VoucherRedemptionMapper redemptionMapper;
    private final VoucherRefundMapper refundMapper;
    private final SettlementItemMapper settlementItemMapper;
    private final AdminAuthService admin;
    private final MerchantAuthService merchant;
    private final RedisIdWorker ids;

    public FinanceServiceImpl(
            CommissionRuleMapper ruleMapper,
            FundLedgerEntryMapper ledgerMapper,
            VoucherOrderMapper orderMapper,
            VoucherRedemptionMapper redemptionMapper,
            VoucherRefundMapper refundMapper,
            SettlementItemMapper settlementItemMapper,
            AdminAuthService admin,
            MerchantAuthService merchant,
            RedisIdWorker ids) {
        this.ruleMapper = ruleMapper;
        this.ledgerMapper = ledgerMapper;
        this.orderMapper = orderMapper;
        this.redemptionMapper = redemptionMapper;
        this.refundMapper = refundMapper;
        this.settlementItemMapper = settlementItemMapper;
        this.admin = admin;
        this.merchant = merchant;
        this.ids = ids;
    }

    /** 查询最新平台默认或门店覆盖规则。 */
    @Override
    public CommissionRuleVO rule(String shopId) {
        admin.requirePermission(AdminPermissions.COMMISSION_MANAGE);
        Long sid = shopId == null ? null : IdUtils.parse(shopId, "shopId");
        CommissionRule rule = ruleMapper.selectOne(new QueryWrapper<CommissionRule>()
                .isNull(sid == null, "shop_id")
                .eq(sid != null, "shop_id", sid)
                .orderByDesc("effective_from", "id")
                .last("LIMIT 1"));
        return rule == null
                ? new CommissionRuleVO(null, shopId, DEFAULT_SERVICE_FEE_RATE_BPS, LocalDateTime.now(), null, 0)
                : toRule(rule);
    }

    /** 创建立即生效的新规则；历史规则保留以支持核销时点追溯。 */
    @Override
    @Transactional
    public CommissionRuleVO update(String shopId, CommissionRuleUpdateDTO request, String key) {
        admin.requirePermission(AdminPermissions.COMMISSION_MANAGE);
        Long sid = shopId == null ? null : IdUtils.parse(shopId, "shopId");
        LocalDateTime now = LocalDateTime.now();
        CommissionRule previous = activeRule(sid, now);
        if (previous != null && previous.getEffectiveTo() == null && java.util.Objects.equals(previous.getShopId(), sid)) {
            previous.setEffectiveTo(now);
            ruleMapper.updateById(previous);
        }
        CommissionRule rule = new CommissionRule()
                .setId(ids.nextId("commission-rule"))
                .setShopId(sid)
                .setRateBps(request.rateBps())
                .setEffectiveFrom(now)
                .setVersion(request.version() + 1);
        ruleMapper.insert(rule);
        log.info("[服务费规则] 新规则生效，shopId={}，rateBps={}", sid, request.rateBps());
        return toRule(rule);
    }

    /** 分页查询不可变资金账本。 */
    @Override
    public PageResult<FundLedgerEntryVO> ledger(int page, int size) {
        admin.requirePermission(AdminPermissions.COMMISSION_MANAGE);
        Page<FundLedgerEntry> result = ledgerMapper.selectPage(
                new Page<>(page, size), new QueryWrapper<FundLedgerEntry>().orderByDesc("occurred_time", "id"));
        return new PageResult<>(result.getRecords().stream().map(this::toLedger).toList(), page, size, result.getTotal());
    }

    /** 查询当前门店累计资金摘要。 */
    @Override
    public MerchantFinanceSummaryVO merchantSummary() {
        var account = merchant.requireCurrentAccount();
        if (account.getShopId() == null) {
            throw BusinessException.forbidden("MERCHANT_ACTIVATION_REQUIRED", "商户账号尚未激活");
        }
        return summarize(ledgerMapper.selectList(
                new QueryWrapper<FundLedgerEntry>().eq("shop_id", account.getShopId())));
    }

    /** 查询平台累计资金摘要。 */
    @Override
    public MerchantFinanceSummaryVO adminSummary() {
        admin.requirePermission(AdminPermissions.COMMISSION_MANAGE);
        return summarize(ledgerMapper.selectList(new QueryWrapper<FundLedgerEntry>()));
    }

    /** 按北京时间自然日汇总当前门店团购核销与退款。 */
    @Override
    public MerchantTodayFinanceVO merchantToday(LocalDate date) {
        var account = merchant.requireCurrentAccount();
        if (account.getShopId() == null) {
            throw BusinessException.forbidden("MERCHANT_ACTIVATION_REQUIRED", "商户账号尚未激活");
        }
        LocalDate target = date == null ? LocalDate.now(BUSINESS_ZONE) : date;
        LocalDateTime from = target.atStartOfDay();
        LocalDateTime to = target.plusDays(1).atStartOfDay();
        List<VoucherRedemption> redemptions = redemptionMapper.selectList(new QueryWrapper<VoucherRedemption>()
                .eq("shop_id", account.getShopId())
                .eq("status", "SUCCEEDED")
                .ge("redeemed_time", from)
                .lt("redeemed_time", to));
        long redemptionAmount = redemptions.stream().mapToLong(item -> zero(item.getCustomerPaidAmount())).sum();
        long redemptionCount = redemptions.stream().mapToLong(item -> positive(item.getUseCount())).sum();
        long redeemedVoucherCount = redemptions.stream().map(VoucherRedemption::getVoucherId).distinct().count();

        List<VoucherRefund> refunds = refundMapper.selectList(new QueryWrapper<VoucherRefund>()
                .eq("shop_id", account.getShopId())
                .and(query -> query.eq("execution_status", "SUCCEEDED").or().eq("status", "SUCCEEDED"))
                .ge("processed_time", from)
                .lt("processed_time", to));
        long refundAmount = refunds.stream().mapToLong(item -> zero(item.getApprovedAmount() == null
                ? item.getAmount() : item.getApprovedAmount())).sum();
        Set<Long> refundedVoucherIds = new HashSet<>();
        refunds.forEach(refund -> refundedVoucherIds.addAll(refundVoucherIds(refund)));
        return new MerchantTodayFinanceVO(target, BUSINESS_ZONE.getId(), redeemedVoucherCount,
                redemptionCount, redemptionAmount, (long) refundedVoucherIds.size(), refundAmount,
                redemptionAmount - refundAmount);
    }

    /** 查询当前门店此刻生效的软件服务费规则。 */
    @Override
    public ServiceFeePolicyVO merchantServiceFeePolicy() {
        var account = merchant.requireCurrentAccount();
        if (account.getShopId() == null) {
            throw BusinessException.forbidden("MERCHANT_ACTIVATION_REQUIRED", "商户账号尚未激活");
        }
        CommissionRule rule = activeRule(account.getShopId(), LocalDateTime.now());
        int rate = rule == null ? DEFAULT_SERVICE_FEE_RATE_BPS : rule.getRateBps();
        return new ServiceFeePolicyVO(
                rule == null ? null : IdUtils.format(rule.getId()),
                IdUtils.format(account.getShopId()),
                rate,
                BigDecimal.valueOf(rate).movePointLeft(2).toPlainString() + "%",
                "（商品售价 - 商家营销补贴）× 服务费率",
                rule == null ? null : rule.getEffectiveFrom(),
                rule == null ? null : rule.getEffectiveTo());
    }

    /** 幂等追加一条不可变账本分录。 */
    @Override
    public void append(FundLedgerEntryVO entry) {
        FundLedgerEntry ledger = new FundLedgerEntry()
                .setId(ids.nextId("ledger-entry"))
                .setShopId(entry.shopId() == null ? null : Long.valueOf(entry.shopId()))
                .setOrderId(entry.orderId() == null ? null : Long.valueOf(entry.orderId()))
                .setVoucherId(entry.voucherId() == null ? null : Long.valueOf(entry.voucherId()))
                .setBusinessEventId(entry.businessEventId())
                .setEntryType(entry.entryType())
                .setAccountSide(entry.accountSide())
                .setAmount(entry.amount())
                .setCommissionRateBps(entry.commissionRateBps())
                .setServiceFeeBaseAmount(entry.serviceFeeBaseAmount())
                .setOccurredTime(entry.occurredTime() == null ? LocalDateTime.now() : entry.occurredTime());
        try {
            ledgerMapper.insert(ledger);
        } catch (DuplicateKeyException ignored) {
            log.debug("[资金账本] 重放命中既有分录，event={}，type={}", entry.businessEventId(), entry.entryType());
        }
    }

    /** 固化单次核销的收入与服务费快照，并追加两条账本分录。 */
    @Override
    @Transactional
    public void recognizeRedemption(Long redemptionId, UserVoucher voucher, LocalDateTime occurredAt) {
        String event = "REDEMPTION-" + redemptionId;
        if (ledgerMapper.findUnique(event, "REDEMPTION_RECOGNIZED", "CREDIT") != null) return;
        VoucherOrder order = orderMapper.selectById(voucher.getOrderId());
        VoucherRedemption redemption = redemptionMapper.selectById(redemptionId);
        if (order == null || redemption == null) {
            throw BusinessException.conflict("REDEMPTION_FINANCE_FACT_MISSING", "核销关联订单或核销记录不存在");
        }
        int uses = positive(voucher.getTotalUseCount());
        int usedIndex = (int) redemptionMapper.countSucceeded(voucher.getId());
        long voucherSale = voucherAmount(voucher.getSaleAmount(), order.getTotalAmount(), order, voucher);
        long voucherMerchantSubsidy = voucherAmount(voucher.getMerchantSubsidyAmount(),
                order.getMerchantSubsidyAmount(), order, voucher);
        long sale = splitPart(voucherSale, uses, usedIndex);
        long merchantSubsidy = splitPart(voucherMerchantSubsidy, uses, usedIndex);
        long platformDiscount = splitPart(voucherAmount(voucher.getPlatformDiscountAmount(),
                order.getPlatformDiscountAmount(), order, voucher), uses, usedIndex);
        long customerPaid = splitPart(voucherAmount(voucher.getCustomerPaidAmount(), order.getPayAmount(), order, voucher),
                uses, usedIndex);
        long feeBase = Math.max(0L, sale - merchantSubsidy);
        CommissionRule rule = activeRule(order.getShopId(), occurredAt);
        int rate = rule == null ? DEFAULT_SERVICE_FEE_RATE_BPS : rule.getRateBps();
        long voucherFeeBase = Math.max(0L, voucherSale - voucherMerchantSubsidy);
        long serviceFee = splitPart(voucherFeeBase * rate / 10_000, uses, usedIndex);
        long estimatedIncome = customerPaid - serviceFee;

        redemption.setSaleAmount(sale)
                .setMerchantSubsidyAmount(merchantSubsidy)
                .setPlatformDiscountAmount(platformDiscount)
                .setCustomerPaidAmount(customerPaid)
                .setServiceFeeBaseAmount(feeBase)
                .setServiceFeeRateBps(rate)
                .setServiceFeeAmount(serviceFee)
                .setEstimatedIncomeAmount(estimatedIncome);
        redemptionMapper.updateById(redemption);
        append(new FundLedgerEntryVO(null, IdUtils.format(order.getShopId()), IdUtils.format(order.getId()),
                IdUtils.format(voucher.getId()), event, "REDEMPTION_RECOGNIZED", "CREDIT", customerPaid,
                rate, feeBase, occurredAt));
        append(new FundLedgerEntryVO(null, IdUtils.format(order.getShopId()), IdUtils.format(order.getId()),
                IdUtils.format(voucher.getId()), event, "SERVICE_FEE_RECOGNIZED", "DEBIT", serviceFee,
                rate, feeBase, occurredAt));
    }

    /** 撤销核销时按原快照生成等额反向分录。 */
    @Override
    @Transactional
    public void reverseRedemption(Long redemptionId, UserVoucher voucher, LocalDateTime occurredAt) {
        VoucherOrder order = orderMapper.selectById(voucher.getOrderId());
        if (order == null) throw BusinessException.conflict("ORDER_NOT_FOUND", "核销关联订单不存在");
        String originalEvent = "REDEMPTION-" + redemptionId;
        FundLedgerEntry recognized = ledgerMapper.findUnique(originalEvent, "REDEMPTION_RECOGNIZED", "CREDIT");
        FundLedgerEntry fee = ledgerMapper.findUnique(originalEvent, "SERVICE_FEE_RECOGNIZED", "DEBIT");
        if (fee == null) fee = ledgerMapper.findUnique(originalEvent, "COMMISSION_RECOGNIZED", "DEBIT");
        String event = "REDEMPTION-REVERSAL-" + redemptionId;
        if (recognized != null) {
            append(new FundLedgerEntryVO(null, IdUtils.format(order.getShopId()), IdUtils.format(order.getId()),
                    IdUtils.format(voucher.getId()), event, "REDEMPTION_REVERSED", "DEBIT",
                    -Math.abs(recognized.getAmount()), recognized.getCommissionRateBps(),
                    recognized.getServiceFeeBaseAmount(), occurredAt));
        }
        if (fee != null) {
            append(new FundLedgerEntryVO(null, IdUtils.format(order.getShopId()), IdUtils.format(order.getId()),
                    IdUtils.format(voucher.getId()), event, "SERVICE_FEE_REVERSED", "CREDIT",
                    Math.abs(fee.getAmount()), fee.getCommissionRateBps(), fee.getServiceFeeBaseAmount(), occurredAt));
        }
    }

    /** 退款成功后冲回冻结款；已核销券额外冲回收入并返还服务费。 */
    @Override
    @Transactional
    public void recordRefundSuccess(VoucherRefund refund, LocalDateTime occurredAt) {
        append(new FundLedgerEntryVO(null, IdUtils.format(refund.getShopId()), IdUtils.format(refund.getOrderId()),
                IdUtils.format(refund.getVoucherId()), "REFUND-" + refund.getId(), "REFUND_REVERSED", "DEBIT",
                -Math.abs(refund.getApprovedAmount() == null ? refund.getAmount() : refund.getApprovedAmount()),
                null, null, occurredAt));
        for (Long voucherId : refundVoucherIds(refund)) {
            List<VoucherRedemption> fulfilled = redemptionMapper.selectList(new QueryWrapper<VoucherRedemption>()
                    .eq("voucher_id", voucherId).eq("status", "SUCCEEDED"));
            for (VoucherRedemption redemption : fulfilled) {
                String event = "REFUND-REDEMPTION-" + refund.getId() + "-" + redemption.getId();
                append(new FundLedgerEntryVO(null, IdUtils.format(refund.getShopId()), IdUtils.format(refund.getOrderId()),
                        IdUtils.format(voucherId), event, "REFUND_REVENUE_REVERSED", "DEBIT",
                        -Math.abs(zero(redemption.getCustomerPaidAmount())), redemption.getServiceFeeRateBps(),
                        redemption.getServiceFeeBaseAmount(), occurredAt));
                append(new FundLedgerEntryVO(null, IdUtils.format(refund.getShopId()), IdUtils.format(refund.getOrderId()),
                        IdUtils.format(voucherId), event, "SERVICE_FEE_REFUNDED", "CREDIT",
                        Math.abs(zero(redemption.getServiceFeeAmount())), redemption.getServiceFeeRateBps(),
                        redemption.getServiceFeeBaseAmount(), occurredAt));
            }
        }
    }

    /** 判断核销正向收入是否已进入结算。 */
    @Override
    public boolean isRedemptionSettled(Long redemptionId) {
        return settlementItemMapper.existsForLedgerEvent("REDEMPTION-" + redemptionId);
    }

    private MerchantFinanceSummaryVO summarize(List<FundLedgerEntry> entries) {
        long frozen = 0L;
        long recognized = 0L;
        long serviceFee = 0L;
        long refunded = 0L;
        for (FundLedgerEntry entry : entries) {
            long amount = zero(entry.getAmount());
            switch (entry.getEntryType()) {
                case "PAYMENT_FROZEN", "REFUND_REVERSED" -> frozen += amount;
                case "REDEMPTION_RECOGNIZED", "REDEMPTION_REVERSED", "REFUND_REVENUE_REVERSED" ->
                        recognized += amount;
                case "SERVICE_FEE_RECOGNIZED", "COMMISSION_RECOGNIZED" -> serviceFee += Math.abs(amount);
                case "SERVICE_FEE_REVERSED", "COMMISSION_REVERSED", "SERVICE_FEE_REFUNDED" ->
                        serviceFee -= Math.abs(amount);
                default -> { }
            }
            if ("REFUND_REVERSED".equals(entry.getEntryType())) refunded += Math.abs(amount);
        }
        long net = recognized - serviceFee;
        return new MerchantFinanceSummaryVO(frozen, recognized, serviceFee, net, refunded, net, 0L);
    }

    private CommissionRule activeRule(Long shopId, LocalDateTime occurredAt) {
        CommissionRule shopRule = shopId == null ? null : findRule(shopId, occurredAt);
        return shopRule == null ? findRule(null, occurredAt) : shopRule;
    }

    private CommissionRule findRule(Long shopId, LocalDateTime occurredAt) {
        return ruleMapper.selectOne(new QueryWrapper<CommissionRule>()
                .isNull(shopId == null, "shop_id")
                .eq(shopId != null, "shop_id", shopId)
                .le("effective_from", occurredAt)
                .and(query -> query.isNull("effective_to").or().gt("effective_to", occurredAt))
                .orderByDesc("effective_from", "id")
                .last("LIMIT 1"));
    }

    private long voucherAmount(Long voucherSnapshot, Long orderAmount, VoucherOrder order, UserVoucher voucher) {
        if (voucherSnapshot != null) return voucherSnapshot;
        return splitPart(zero(orderAmount), positive(order.getQuantity()), positive(voucher.getSequenceNo()));
    }

    private List<Long> refundVoucherIds(VoucherRefund refund) {
        if (refund.getVoucherIds() == null || refund.getVoucherIds().isBlank()) return List.of(refund.getVoucherId());
        return java.util.Arrays.stream(refund.getVoucherIds().split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).map(Long::valueOf).toList();
    }

    private int positive(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }

    private long zero(Long value) {
        return value == null ? 0L : value;
    }

    private long splitPart(long amount, int parts, int index) {
        int safeParts = Math.max(1, parts);
        int safeIndex = Math.min(Math.max(1, index), safeParts);
        long base = amount / safeParts;
        return safeIndex == safeParts ? base + amount % safeParts : base;
    }

    private CommissionRuleVO toRule(CommissionRule rule) {
        return new CommissionRuleVO(IdUtils.format(rule.getId()), IdUtils.format(rule.getShopId()),
                rule.getRateBps(), rule.getEffectiveFrom(), rule.getEffectiveTo(), rule.getVersion());
    }

    private FundLedgerEntryVO toLedger(FundLedgerEntry entry) {
        return new FundLedgerEntryVO(IdUtils.format(entry.getId()), IdUtils.format(entry.getShopId()),
                IdUtils.format(entry.getOrderId()), IdUtils.format(entry.getVoucherId()), entry.getBusinessEventId(),
                entry.getEntryType(), entry.getAccountSide(), entry.getAmount(), entry.getCommissionRateBps(),
                entry.getServiceFeeBaseAmount(), entry.getOccurredTime());
    }
}
