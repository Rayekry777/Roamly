package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ray.entity.BusinessMediaAsset;
import com.ray.enums.BusinessMediaStatus;
import com.ray.mapper.BusinessMediaAssetMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 在独立事务中确认已删除经营媒体的物理对象清理完成。 */
@Service
public class BusinessMediaCleanupWriter {
    private final BusinessMediaAssetMapper mapper;

    public BusinessMediaCleanupWriter(BusinessMediaAssetMapper mapper) {
        this.mapper = mapper;
    }

    /** 清空重试标记，避免清理任务反复请求已删除对象。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markObjectDeleted(Long mediaId) {
        mapper.update(null, new UpdateWrapper<BusinessMediaAsset>()
                .eq("id", mediaId)
                .eq("status", BusinessMediaStatus.DELETED.name())
                .set("expires_at", null));
    }
}
