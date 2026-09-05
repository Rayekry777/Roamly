package com.ray.service;

import com.ray.result.PageResult;
import com.ray.vo.SettlementBatchVO;
import java.time.LocalDate;

/** 结算批次查询、重试和 T+1 批次生成。 */
public interface SettlementService {
    PageResult<SettlementBatchVO> list(int page, int size, boolean admin);
    SettlementBatchVO get(Long id, boolean admin);
    SettlementBatchVO retry(Long id, String key);
    byte[] export(String resource, boolean admin);

    /** 为指定结算日生成前一日可结算账本；同一门店和日期重复执行返回既有批次。 */
    void generateForDate(LocalDate settlementDate);
}
