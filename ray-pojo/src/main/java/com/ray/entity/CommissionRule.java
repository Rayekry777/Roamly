package com.ray.entity;
import com.baomidou.mybatisplus.annotation.*;import java.time.*;import lombok.Data;import lombok.experimental.Accessors;
@Data @Accessors(chain=true) @TableName("commission_rule") public class CommissionRule {@TableId(value="id",type=IdType.INPUT)private Long id;private Long shopId;private Integer rateBps;private LocalDateTime effectiveFrom;private LocalDateTime effectiveTo;private Integer version;private LocalDateTime createTime;private LocalDateTime updateTime;}
