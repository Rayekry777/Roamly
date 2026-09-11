package com.ray.entity;
import com.baomidou.mybatisplus.annotation.*;import java.time.*;import lombok.Data;import lombok.experimental.Accessors;
@Data @Accessors(chain=true) @TableName("fund_ledger_entry") public class FundLedgerEntry {@TableId(value="id",type=IdType.INPUT)private Long id;private Long shopId;private Long orderId;private Long voucherId;private String businessEventId;private String entryType;private String accountSide;private Long amount;private Integer commissionRateBps;private Long serviceFeeBaseAmount;private LocalDateTime occurredTime;private LocalDateTime createTime;}
