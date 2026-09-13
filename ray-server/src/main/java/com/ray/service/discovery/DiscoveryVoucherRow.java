package com.ray.service.discovery;
import com.ray.entity.VoucherProduct;
import lombok.Getter;
import lombok.Setter;
/** 每店最多四条候选券，窗口计数保留完整可售数量。 */
@Getter @Setter
public class DiscoveryVoucherRow extends VoucherProduct {
    private Long availableCount;
    private Boolean keywordMatched;
}
