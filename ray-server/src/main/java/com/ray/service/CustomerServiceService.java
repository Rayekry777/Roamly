package com.ray.service;

import com.ray.dto.CustomerServiceCreateDTO;
import com.ray.dto.CustomerServiceReplyDTO;
import com.ray.dto.CustomerServiceStatusDTO;
import com.ray.result.PageResult;
import com.ray.vo.CustomerServiceTicketVO;

/** 消费者、商户和平台客服共用的工单与消息能力。 */
public interface CustomerServiceService {
    /** 创建消费者工单并写入首条公开描述。 */
    CustomerServiceTicketVO createForConsumer(CustomerServiceCreateDTO request);
    /** 分页查询当前消费者自己的工单。 */
    PageResult<CustomerServiceTicketVO> listForConsumer(int page, int size);
    /** 查询当前消费者可见的工单详情。 */
    CustomerServiceTicketVO getForConsumer(Long id);
    /** 在可重开窗口内追加消费者公开回复。 */
    CustomerServiceTicketVO replyForConsumer(Long id, CustomerServiceReplyDTO request);

    /** 创建当前商户门店范围内的客服工单。 */
    CustomerServiceTicketVO createForMerchant(CustomerServiceCreateDTO request);
    /** 分页查询当前商户门店范围内的工单。 */
    PageResult<CustomerServiceTicketVO> listForMerchant(int page, int size);

    /** 按客服权限和状态筛选平台工单队列。 */
    PageResult<CustomerServiceTicketVO> listForAdmin(String status, int page, int size);
    /** 查询平台客服可见的工单详情及内部消息。 */
    CustomerServiceTicketVO getForAdmin(Long id);
    /** 认领工单并推进新工单状态。 */
    CustomerServiceTicketVO claim(Long id);
    /** 追加公开回复或内部备注。 */
    CustomerServiceTicketVO replyForAdmin(Long id, CustomerServiceReplyDTO request, boolean internal);
    /** 更新工单状态及对应时间事实。 */
    CustomerServiceTicketVO updateStatus(Long id, CustomerServiceStatusDTO request);
    /** 关闭超过空闲期限且处于可关闭状态的工单。 */
    int autoCloseIdleTickets();
}
