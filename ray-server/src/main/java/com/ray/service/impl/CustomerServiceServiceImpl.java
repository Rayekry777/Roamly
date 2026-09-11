package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.constant.AdminPermissions;
import com.ray.dto.CustomerServiceCreateDTO;
import com.ray.dto.CustomerServiceReplyDTO;
import com.ray.dto.CustomerServiceStatusDTO;
import com.ray.entity.CustomerServiceMessage;
import com.ray.entity.CustomerServiceTicket;
import com.ray.entity.MerchantAccount;
import com.ray.enums.CustomerServiceTicketStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.CustomerServiceMessageMapper;
import com.ray.mapper.CustomerServiceTicketMapper;
import com.ray.result.PageResult;
import com.ray.service.AdminAuthService;
import com.ray.service.CurrentUserProvider;
import com.ray.service.CustomerServiceService;
import com.ray.service.MerchantAuthService;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.CustomerServiceMessageVO;
import com.ray.vo.CustomerServiceTicketVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 客服工单统一状态机；消息可见性在服务端按角色隔离。 */
@Service
public class CustomerServiceServiceImpl implements CustomerServiceService {
    private final CustomerServiceTicketMapper ticketMapper;
    private final CustomerServiceMessageMapper messageMapper;
    private final CurrentUserProvider userProvider;
    private final MerchantAuthService merchantAuth;
    private final AdminAuthService adminAuth;
    private final RedisIdWorker idWorker;

    /** 注入三端身份上下文、工单持久化和业务 ID 生成器。 */
    public CustomerServiceServiceImpl(CustomerServiceTicketMapper ticketMapper,
            CustomerServiceMessageMapper messageMapper, CurrentUserProvider userProvider,
            MerchantAuthService merchantAuth, AdminAuthService adminAuth, RedisIdWorker idWorker) {
        this.ticketMapper = ticketMapper; this.messageMapper = messageMapper; this.userProvider = userProvider;
        this.merchantAuth = merchantAuth; this.adminAuth = adminAuth; this.idWorker = idWorker;
    }

    /** 创建消费者工单并保存首条公开描述。 */
    @Override @Transactional
    public CustomerServiceTicketVO createForConsumer(CustomerServiceCreateDTO request) {
        Long userId = userProvider.requireUserId();
        CustomerServiceTicket ticket = baseTicket(request, "CONSUMER", userId).setUserId(userId);
        ticketMapper.insert(ticket);
        if (request.description() != null && !request.description().isBlank()) addMessage(ticket, "CONSUMER", userId, "PUBLIC", request.description(), "TEXT");
        return toView(ticket, false);
    }

    /** 分页查询当前消费者自己的工单。 */
    @Override public PageResult<CustomerServiceTicketVO> listForConsumer(int page, int size) {
        Long userId = userProvider.requireUserId();
        Page<CustomerServiceTicket> result = ticketMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<CustomerServiceTicket>().eq("user_id", userId).orderByDesc("update_time", "id"));
        return page(result, page, size, false);
    }

    /** 查询当前消费者可见的工单详情。 */
    @Override public CustomerServiceTicketVO getForConsumer(Long id) {
        CustomerServiceTicket ticket = requireTicket(id);
        ensureUser(ticket);
        return toView(ticket, false);
    }

    /** 在可重开窗口内追加消费者公开回复。 */
    @Override @Transactional
    public CustomerServiceTicketVO replyForConsumer(Long id, CustomerServiceReplyDTO request) {
        CustomerServiceTicket ticket = requireTicket(id); ensureUser(ticket);
        if (CustomerServiceTicketStatus.CLOSED.name().equals(ticket.getStatus())) {
            LocalDateTime now = LocalDateTime.now();
            if (ticket.getReopenDeadline() == null || ticket.getReopenDeadline().isBefore(now))
                throw BusinessException.conflict("TICKET_CLOSED", "工单已关闭，无法继续回复");
            ticket.setStatus(CustomerServiceTicketStatus.OPEN.name()).setClosedTime(null);
        }
        addMessage(ticket, "CONSUMER", userProvider.requireUserId(), "PUBLIC", request.content(), request.messageType());
        ticket.setStatus(CustomerServiceTicketStatus.OPEN.name()).setLastMessageTime(LocalDateTime.now()).setUpdateTime(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        return toView(ticket, false);
    }

    /** 创建当前商户门店范围内的客服工单。 */
    @Override @Transactional
    public CustomerServiceTicketVO createForMerchant(CustomerServiceCreateDTO request) {
        MerchantAccount account = merchantAuth.requireCurrentAccount();
        if (account.getShopId() == null) throw BusinessException.forbidden("MERCHANT_ACTIVATION_REQUIRED", "商户账号尚未激活");
        CustomerServiceTicket ticket = baseTicket(request, "MERCHANT", account.getId()).setShopId(account.getShopId());
        ticketMapper.insert(ticket);
        if (request.description() != null && !request.description().isBlank()) addMessage(ticket, "MERCHANT", account.getId(), "PUBLIC", request.description(), "TEXT");
        return toView(ticket, false);
    }

    /** 分页查询当前商户门店范围内的工单。 */
    @Override public PageResult<CustomerServiceTicketVO> listForMerchant(int page, int size) {
        MerchantAccount account = merchantAuth.requireCurrentAccount();
        if (account.getShopId() == null) throw BusinessException.forbidden("MERCHANT_ACTIVATION_REQUIRED", "商户账号尚未激活");
        Page<CustomerServiceTicket> result = ticketMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<CustomerServiceTicket>().eq("shop_id", account.getShopId()).orderByDesc("update_time", "id"));
        return page(result, page, size, false);
    }

    /** 按客服权限和状态筛选平台工单队列。 */
    @Override public PageResult<CustomerServiceTicketVO> listForAdmin(String status, int page, int size) {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_READ);
        QueryWrapper<CustomerServiceTicket> q = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            try { q.eq("status", CustomerServiceTicketStatus.valueOf(status.toUpperCase(Locale.ROOT)).name()); }
            catch (IllegalArgumentException e) { throw BusinessException.badRequest("INVALID_TICKET_STATUS", "工单状态无效"); }
        }
        Page<CustomerServiceTicket> result = ticketMapper.selectPage(new Page<>(page, size), q.orderByAsc("priority", "create_time", "id"));
        return page(result, page, size, true);
    }

    /** 查询平台客服可见的工单详情及内部消息。 */
    @Override public CustomerServiceTicketVO getForAdmin(Long id) {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_READ);
        return toView(requireTicket(id), true);
    }

    /** 认领工单并推进新工单状态。 */
    @Override @Transactional
    public CustomerServiceTicketVO claim(Long id) {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_MANAGE);
        CustomerServiceTicket ticket = requireTicket(id);
        Long adminId = adminAuth.currentAdminId();
        ticket.setAssigneeAdminId(adminId);
        if (CustomerServiceTicketStatus.NEW.name().equals(ticket.getStatus())) ticket.setStatus(CustomerServiceTicketStatus.OPEN.name());
        ticketMapper.updateById(ticket);
        return toView(ticket, true);
    }

    /** 追加公开回复或内部备注。 */
    @Override @Transactional
    public CustomerServiceTicketVO replyForAdmin(Long id, CustomerServiceReplyDTO request, boolean internal) {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_MANAGE);
        CustomerServiceTicket ticket = requireTicket(id);
        Long adminId = adminAuth.currentAdminId(); LocalDateTime now = LocalDateTime.now();
        addMessage(ticket, "ADMIN", adminId, internal ? "INTERNAL" : "PUBLIC", request.content(), request.messageType());
        if (ticket.getFirstResponseTime() == null && !internal) ticket.setFirstResponseTime(now);
        ticket.setStatus((internal ? CustomerServiceTicketStatus.WAITING_INTERNAL : CustomerServiceTicketStatus.WAITING_CUSTOMER).name())
                .setLastMessageTime(now).setUpdateTime(now);
        ticketMapper.updateById(ticket);
        return toView(ticket, true);
    }

    /** 更新工单状态及对应时间事实。 */
    @Override @Transactional
    public CustomerServiceTicketVO updateStatus(Long id, CustomerServiceStatusDTO request) {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_MANAGE);
        CustomerServiceTicket ticket = requireTicket(id); LocalDateTime now = LocalDateTime.now();
        String status = request.status().name(); ticket.setStatus(status);
        if (CustomerServiceTicketStatus.RESOLVED.name().equals(status)) ticket.setResolvedTime(now);
        if (CustomerServiceTicketStatus.CLOSED.name().equals(status)) ticket.setClosedTime(now).setReopenDeadline(now.plusDays(7));
        if (CustomerServiceTicketStatus.OPEN.name().equals(status)) { ticket.setClosedTime(null); ticket.setReopenDeadline(now.plusDays(7)); }
        ticketMapper.updateById(ticket); return toView(ticket, true);
    }

    /** 关闭超过七天空闲且处于可关闭状态的工单。 */
    @Override @Scheduled(fixedDelayString = "${ray.customer-service.auto-close-scan-ms:3600000}") @Transactional
    public int autoCloseIdleTickets() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        List<CustomerServiceTicket> tickets = ticketMapper.selectList(new QueryWrapper<CustomerServiceTicket>()
                .in("status", CustomerServiceTicketStatus.RESOLVED.name(), CustomerServiceTicketStatus.WAITING_CUSTOMER.name())
                .and(w -> w.le("last_message_time", cutoff).or().le("update_time", cutoff)));
        int count = 0; for (CustomerServiceTicket ticket : tickets) {
            LocalDateTime now = LocalDateTime.now();
            ticket.setStatus(CustomerServiceTicketStatus.CLOSED.name()).setClosedTime(now).setReopenDeadline(now.plusDays(7));
            ticketMapper.updateById(ticket); count++;
        } return count;
    }

    private CustomerServiceTicket baseTicket(CustomerServiceCreateDTO request, String creatorType, Long creatorId) {
        long id = idWorker.nextId("customer-service-ticket");
        return new CustomerServiceTicket().setId(id).setTicketNo("CS" + String.format("%014d", id % 100000000000000L))
                .setType(request.type().trim().toUpperCase(Locale.ROOT)).setStatus(CustomerServiceTicketStatus.NEW.name())
                .setPriority("NORMAL").setOrderId(request.orderId()).setVoucherId(request.voucherId()).setRefundId(request.refundId())
                .setRedemptionId(request.redemptionId()).setSubject(request.subject().trim()).setDescription(request.description())
                .setCreatedByType(creatorType).setCreatedById(creatorId).setVersion(0).setLastMessageTime(LocalDateTime.now());
    }

    private void addMessage(CustomerServiceTicket ticket, String senderType, Long senderId, String visibility, String content, String messageType) {
        messageMapper.insert(new CustomerServiceMessage().setId(idWorker.nextId("customer-service-message")).setTicketId(ticket.getId())
                .setSenderType(senderType).setSenderId(senderId).setVisibility(visibility)
                .setMessageType(messageType == null || messageType.isBlank() ? "TEXT" : messageType.toUpperCase(Locale.ROOT)).setContent(content));
    }

    private CustomerServiceTicket requireTicket(Long id) { CustomerServiceTicket ticket = ticketMapper.selectById(id); if (ticket == null) throw BusinessException.notFound("TICKET_NOT_FOUND", "客服工单不存在"); return ticket; }
    private void ensureUser(CustomerServiceTicket ticket) { if (ticket.getUserId() == null || !ticket.getUserId().equals(userProvider.requireUserId())) throw BusinessException.notFound("TICKET_NOT_FOUND", "客服工单不存在"); }
    private PageResult<CustomerServiceTicketVO> page(Page<CustomerServiceTicket> p, int page, int size, boolean admin) { return new PageResult<>(p.getRecords().stream().map(t -> toView(t, admin)).toList(), page, size, p.getTotal()); }
    private CustomerServiceTicketVO toView(CustomerServiceTicket t, boolean admin) {
        List<CustomerServiceMessageVO> messages = messageMapper.selectList(new QueryWrapper<CustomerServiceMessage>().eq("ticket_id", t.getId()).orderByAsc("create_time", "id")).stream()
                .filter(m -> admin || "PUBLIC".equals(m.getVisibility())).map(m -> new CustomerServiceMessageVO(m.getId(), m.getTicketId(), m.getSenderType(), m.getSenderId(), m.getVisibility(), m.getMessageType(), m.getContent(), m.getCreateTime())).toList();
        return new CustomerServiceTicketVO(t.getId(), t.getTicketNo(), t.getType(), t.getStatus(), t.getPriority(), t.getUserId(), t.getShopId(), t.getOrderId(), t.getVoucherId(), t.getRefundId(), t.getRedemptionId(), t.getSubject(), t.getDescription(), t.getAssigneeAdminId(), t.getCreatedByType(), t.getFirstResponseTime(), t.getResolvedTime(), t.getClosedTime(), t.getLastMessageTime(), t.getCreateTime(), t.getUpdateTime(), messages);
    }
}
