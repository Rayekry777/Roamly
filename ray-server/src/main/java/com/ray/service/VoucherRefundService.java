package com.ray.service;

import com.ray.dto.VoucherRefundDTO;
import com.ray.dto.MerchantRefundDTO;
import com.ray.dto.AdminRefundDTO;
import com.ray.dto.ConsumerRefundDTO;
import com.ray.result.PageResult;
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
    PageResult<VoucherRefundVO> merchantList(String status, int page, int size);
    VoucherRefundVO merchantGet(Long id);
    VoucherRefundVO merchantRequest(MerchantRefundDTO request, String idempotencyKey);
    VoucherRefundVO adminRequest(AdminRefundDTO request, String idempotencyKey);
    VoucherRefundVO consumerRequest(ConsumerRefundDTO request, String idempotencyKey);
}
