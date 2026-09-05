package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.SettlementItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SettlementItemMapper extends BaseMapper<SettlementItem> {
    @Select("SELECT COUNT(*) > 0 FROM settlement_item i JOIN fund_ledger_entry l ON l.id=i.ledger_entry_id "
            + "WHERE l.business_event_id=#{event}")
    boolean existsForLedgerEvent(@Param("event") String event);
}
