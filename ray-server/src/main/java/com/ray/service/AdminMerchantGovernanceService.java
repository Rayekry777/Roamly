package com.ray.service;

import com.ray.dto.MerchantApplicationApprovalDTO;
import com.ray.dto.MerchantApplicationRejectionDTO;
import com.ray.dto.ShopGovernanceDTO;
import com.ray.enums.MerchantApplicationStatus;
import com.ray.enums.ShopStatus;
import com.ray.result.PageResult;
import com.ray.vo.AdminShopDetailVO;
import com.ray.vo.AdminShopGovernanceResultVO;
import com.ray.vo.AdminShopListItemVO;
import com.ray.vo.MerchantApplicationReviewDetailVO;
import com.ray.vo.MerchantApplicationReviewListItemVO;
import com.ray.vo.MerchantApplicationReviewResultVO;
import java.time.LocalDateTime;

/** 管理端商户申请审核、敏感资料读取与门店治理服务。 */
public interface AdminMerchantGovernanceService {
    /** 按审核条件分页查询脱敏商户申请。 */
    PageResult<MerchantApplicationReviewListItemVO> listApplications(
            MerchantApplicationStatus status,
            String cityCode,
            Long shopTypeId,
            String phone,
            LocalDateTime submittedFrom,
            LocalDateTime submittedTo,
            int page,
            int size);

    /** 查询审核白名单详情并记录敏感查看审计。 */
    MerchantApplicationReviewDetailVO getApplication(String applicationId);

    /** 读取指定申请拥有的已绑定私有媒体。 */
    AdminMediaContent readApplicationMedia(String applicationId, String mediaId);

    /** 幂等审核通过申请并在同一事务中创建活动门店和激活租户。 */
    MerchantApplicationReviewResultVO approve(
            String applicationId, String idempotencyKey, MerchantApplicationApprovalDTO request);

    /** 幂等驳回申请并在同一事务中迁移游客状态。 */
    MerchantApplicationReviewResultVO reject(
            String applicationId, String idempotencyKey, MerchantApplicationRejectionDTO request);

    /** 按经营条件分页查询门店治理摘要。 */
    PageResult<AdminShopListItemVO> listShops(
            ShopStatus status, String cityCode, Long shopTypeId, String keyword, int page, int size);

    /** 查询门店来源、租户、账号和最近治理详情。 */
    AdminShopDetailVO getShop(String shopId);

    /** 幂等停用门店并只联动当前活动账号。 */
    AdminShopGovernanceResultVO suspendShop(
            String shopId, String idempotencyKey, ShopGovernanceDTO request);

    /** 幂等恢复门店并只恢复因门店停用联动的账号。 */
    AdminShopGovernanceResultVO activateShop(
            String shopId, String idempotencyKey, ShopGovernanceDTO request);

    /** 管理端私有媒体内容。 */
    record AdminMediaContent(byte[] content, String mimeType, String filename) {
        public AdminMediaContent {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
