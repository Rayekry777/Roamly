package com.ray.service;

import com.ray.dto.CustomerServiceCreateDTO;
import com.ray.dto.CustomerServiceQuickReplyDTO;
import com.ray.dto.CustomerServiceReplyDTO;
import com.ray.dto.CustomerServiceStatusDTO;
import com.ray.dto.CustomerServiceTagUpdateDTO;
import com.ray.dto.CustomerServiceTransferDTO;
import com.ray.result.PageResult;
import com.ray.vo.CustomerServiceMessagePageVO;
import com.ray.vo.CustomerServiceQuickReplyVO;
import com.ray.vo.CustomerServiceTagVO;
import com.ray.vo.CustomerServiceTicketVO;
import com.ray.vo.CustomerServiceTransferVO;
import java.util.List;

/** 消费者、商户和平台客服共用的工单、消息、标签与队列能力。 */
public interface CustomerServiceService {
    /** 创建消费者工单并校验所有关联业务归属。 */
    CustomerServiceTicketVO createForConsumer(CustomerServiceCreateDTO request);
    /** 分页查询当前消费者作为申请人的工单。 */
    PageResult<CustomerServiceTicketVO> listForConsumer(int page, int size);
    /** 查询当前消费者可见的工单详情。 */
    CustomerServiceTicketVO getForConsumer(Long id);
    /** 追加消费者公开回复，已关闭工单禁止修改。 */
    CustomerServiceTicketVO replyForConsumer(Long id, CustomerServiceReplyDTO request);
    /** 游标查询当前消费者可见消息并推进独立已读游标。 */
    CustomerServiceMessagePageVO messagesForConsumer(Long id, Long beforeId, Long afterId, int limit);

    /** 创建当前商户账号作为申请人的平台客服工单。 */
    CustomerServiceTicketVO createForMerchant(CustomerServiceCreateDTO request);
    /** 分页查询当前商户账号主动创建的工单。 */
    PageResult<CustomerServiceTicketVO> listForMerchant(int page, int size);
    /** 查询当前商户账号主动创建的工单详情。 */
    CustomerServiceTicketVO getForMerchant(Long id);
    /** 追加商户公开回复。 */
    CustomerServiceTicketVO replyForMerchant(Long id, CustomerServiceReplyDTO request);
    /** 游标查询当前商户可见消息并推进独立已读游标。 */
    CustomerServiceMessagePageVO messagesForMerchant(Long id, Long beforeId, Long afterId, int limit);

    /** 按权限、状态、申请人类型和标签筛选平台工单队列。 */
    PageResult<CustomerServiceTicketVO> listForAdmin(String status, String applicantType, Long tagId, int page, int size);
    /** 查询平台客服可见的工单详情。 */
    CustomerServiceTicketVO getForAdmin(Long id);
    /** 游标查询平台工单全部消息并推进当前客服的已读游标。 */
    CustomerServiceMessagePageVO messagesForAdmin(Long id, Long beforeId, Long afterId, int limit);
    /** 使用带状态条件的单语句更新原子认领开放工单。 */
    CustomerServiceTicketVO claim(Long id);
    /** 追加公开回复或内部备注；内部备注不改变主状态。 */
    CustomerServiceTicketVO replyForAdmin(Long id, CustomerServiceReplyDTO request, boolean internal);
    /** 按冻结白名单更新工单主状态。 */
    CustomerServiceTicketVO updateStatus(Long id, CustomerServiceStatusDTO request);
    /** 把已认领工单转交给启用的平台客服并保留记录。 */
    CustomerServiceTicketVO transfer(Long id, CustomerServiceTransferDTO request);
    /** 返回指定工单的全部转交记录。 */
    List<CustomerServiceTransferVO> transfers(Long id);
    /** 返回启用的客服标签字典。 */
    List<CustomerServiceTagVO> tags();
    /** 原子替换工单标签集合。 */
    CustomerServiceTicketVO replaceTags(Long id, CustomerServiceTagUpdateDTO request);
    /** 返回当前客服可使用的个人及团队快捷回复。 */
    List<CustomerServiceQuickReplyVO> quickReplies();
    /** 新建个人或团队快捷回复。 */
    CustomerServiceQuickReplyVO createQuickReply(CustomerServiceQuickReplyDTO request);
    /** 删除有权维护的快捷回复。 */
    void deleteQuickReply(Long id);
    /** 关闭超过空闲期限且处于可关闭状态的工单。 */
    int autoCloseIdleTickets();
    /** 刷新已超过截止时间的 SLA 标志。 */
    int refreshSlaBreaches();
}
