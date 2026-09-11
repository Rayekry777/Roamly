package com.ray.service;
import com.ray.dto.CommissionRuleUpdateDTO;
import com.ray.entity.UserVoucher;
import com.ray.entity.VoucherRefund;
import com.ray.result.PageResult;
import com.ray.vo.*;
import java.time.LocalDateTime;
import java.time.LocalDate;

/** 团购订单、软件服务费和不可变结算账本能力。 */
public interface FinanceService {
    /** 查询管理端平台默认或门店覆盖服务费规则。 */
    CommissionRuleVO rule(String shopId);
    /** 创建新的平台默认或门店覆盖服务费规则。 */
    CommissionRuleVO update(String shopId, CommissionRuleUpdateDTO request, String key);
    /** 查询管理端资金账本。 */
    PageResult<FundLedgerEntryVO> ledger(int page, int size);
    /** 查询当前商户累计资金摘要。 */
    MerchantFinanceSummaryVO merchantSummary();
    /** 查询平台累计资金摘要。 */
    MerchantFinanceSummaryVO adminSummary();
    /** 查询当前门店某个北京时间自然日的团购收银数据。 */
    MerchantTodayFinanceVO merchantToday(LocalDate date);
    /** 查询当前门店此刻生效的软件服务费规则。 */
    ServiceFeePolicyVO merchantServiceFeePolicy();
    /** 幂等追加一条不可变账本分录。 */
    void append(FundLedgerEntryVO entry);
    /** 核销成功后固化本次收入和服务费快照并记账。 */
    void recognizeRedemption(Long redemptionId, UserVoucher voucher, LocalDateTime occurredAt);
    /** 撤销核销时追加收入和服务费反向分录。 */
    void reverseRedemption(Long redemptionId, UserVoucher voucher, LocalDateTime occurredAt);
    /** 退款成功时冲回冻结款；已履约退款同时冲回收入并返还服务费。 */
    void recordRefundSuccess(VoucherRefund refund, LocalDateTime occurredAt);
    /** 判断核销正向分录是否已经进入结算批次。 */
    boolean isRedemptionSettled(Long redemptionId);
}
