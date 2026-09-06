package com.ray.service;
import com.ray.dto.CommissionRuleUpdateDTO;
import com.ray.entity.UserVoucher;
import com.ray.result.PageResult;
import com.ray.vo.*;
import java.time.LocalDateTime;

/** 订单资金、佣金和结算账本服务。 */
public interface FinanceService {
    CommissionRuleVO rule(String shopId);
    CommissionRuleVO update(String shopId, CommissionRuleUpdateDTO request, String key);
    PageResult<FundLedgerEntryVO> ledger(int page, int size);
    MerchantFinanceSummaryVO merchantSummary();
    MerchantFinanceSummaryVO adminSummary();
    void append(FundLedgerEntryVO entry);
    void recognizeRedemption(Long redemptionId, UserVoucher voucher, LocalDateTime occurredAt);
    void reverseRedemption(Long redemptionId, UserVoucher voucher, LocalDateTime occurredAt);
    boolean isRedemptionSettled(Long redemptionId);
}
