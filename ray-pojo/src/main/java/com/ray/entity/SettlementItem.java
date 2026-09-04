package com.ray.entity;import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import lombok.experimental.Accessors;
@Data @Accessors(chain=true) @TableName("settlement_item") public class SettlementItem {@TableId(value="id",type=IdType.INPUT)private Long id;private Long batchId;private Long ledgerEntryId;private Long amount;}
