package com.ray.service;

import com.ray.result.PageResult;
import com.ray.vo.SettlementBatchVO;
import java.time.LocalDate;

/** 结算批次查询、重试和 T+1 批次生成。 */
public interface SettlementService {
    /** 按管理端或当前商户权限分页查询结算批次。 */
    PageResult<SettlementBatchVO> list(int page, int size, boolean admin);

    /** 查询单个结算批次并校验访问权限。 */
    SettlementBatchVO get(Long id, boolean admin);

    /** 使用幂等键创建一次新的 Mock 结算执行尝试。 */
    SettlementBatchVO retry(Long id, String key);

    /** 导出当前调用方有权访问的账本或结算批次。 */
    byte[] export(String resource, boolean admin);

    /** 为指定结算日生成前一日可结算账本；同一门店和日期重复执行返回既有批次。 */
    void generateForDate(LocalDate settlementDate);
}
