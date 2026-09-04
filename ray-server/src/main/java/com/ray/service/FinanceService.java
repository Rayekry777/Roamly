package com.ray.service;
import com.ray.dto.CommissionRuleUpdateDTO;import com.ray.result.PageResult;import com.ray.vo.*;
public interface FinanceService { CommissionRuleVO rule(String shopId); CommissionRuleVO update(String shopId,CommissionRuleUpdateDTO request,String key); PageResult<FundLedgerEntryVO> ledger(int page,int size); MerchantFinanceSummaryVO merchantSummary(); void append(FundLedgerEntryVO entry); }
