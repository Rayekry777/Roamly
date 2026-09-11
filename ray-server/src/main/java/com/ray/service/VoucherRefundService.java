package com.ray.service;

import com.ray.dto.VoucherRefundDTO;
import com.ray.dto.MerchantRefundDTO;
import com.ray.dto.AdminRefundDTO;
import com.ray.dto.ConsumerRefundDTO;
import com.ray.enums.MerchantAfterSaleStage;
import com.ray.result.PageResult;
import com.ray.vo.MerchantRefundCandidateVO;
import com.ray.vo.VoucherRefundVO;

/** 消费者单券退款申请及管理端处理能力。 */
public interface VoucherRefundService {
    /** 创建当前用户的单券退款申请并锁定券的可用状态。 */
    VoucherRefundVO request(Long voucherId, VoucherRefundDTO request, String idempotencyKey);
    /** 按用户或管理端权限分页查询退款记录。 */
    PageResult<VoucherRefundVO> list(String status, int page, int size, boolean admin);
    /** 查询当前用户或管理端可见的退款详情。 */
    VoucherRefundVO get(Long id, boolean admin);
    /** 由管理端审批、驳回或重试退款，并同步券与订单状态。 */
    VoucherRefundVO decide(Long id, boolean approve, String reason, String idempotencyKey);
    /** 按当前门店、聚合阶段和精确关键词分页查询售后。 */
    PageResult<VoucherRefundVO> merchantList(
            String status, MerchantAfterSaleStage stage, String keyword, int page, int size);
    /** 按本店订单号或券码查询退款候选和服务端资格。 */
    MerchantRefundCandidateVO merchantCandidate(String keyword);
    /** 查询当前门店可见的售后详情。 */
    VoucherRefundVO merchantGet(Long id);
    /** 以字符串业务 ID 发起本店退款申请。 */
    VoucherRefundVO merchantRequest(MerchantRefundDTO request, String idempotencyKey);
    VoucherRefundVO adminRequest(AdminRefundDTO request, String idempotencyKey);
    VoucherRefundVO consumerRequest(ConsumerRefundDTO request, String idempotencyKey);
    /** 扫描已过期且支持自动退款的未使用券，生成幂等的自动退款单。 */
    int scanExpiredVouchers();
}
