package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.dto.MerchantApplicationSaveDTO;
import com.ray.entity.MerchantApplication;
import com.ray.vo.MerchantApplicationVO;

/** 维护当前店主的入驻草稿、预览和幂等提交。 */
public interface MerchantApplicationService extends IService<MerchantApplication> {
    /** 查询当前店主的唯一申请，尚未创建时返回空。 */
    MerchantApplicationVO current();

    /** 以完整快照和乐观锁版本创建或更新草稿。 */
    MerchantApplicationVO saveDraft(MerchantApplicationSaveDTO request);

    /** 校验完整资料并将申请和账号原子迁移为审核中。 */
    MerchantApplicationVO submit(String idempotencyKey);
}
