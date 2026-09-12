package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.constant.AdminPermissions;
import com.ray.dto.CustomerServiceCreateDTO;
import com.ray.dto.CustomerServiceQuickReplyDTO;
import com.ray.dto.CustomerServiceReplyDTO;
import com.ray.dto.CustomerServiceStatusDTO;
import com.ray.dto.CustomerServiceTagUpdateDTO;
import com.ray.dto.CustomerServiceTransferDTO;
import com.ray.entity.AdminUser;
import com.ray.entity.CustomerServiceAttachment;
import com.ray.entity.CustomerServiceMessage;
import com.ray.entity.CustomerServiceQuickReply;
import com.ray.entity.CustomerServiceReadCursor;
import com.ray.entity.CustomerServiceTag;
import com.ray.entity.CustomerServiceTicket;
import com.ray.entity.CustomerServiceTicketTag;
import com.ray.entity.CustomerServiceTransfer;
import com.ray.entity.MerchantAccount;
import com.ray.entity.UserVoucher;
import com.ray.entity.VoucherOrder;
import com.ray.entity.VoucherRedemption;
import com.ray.entity.VoucherRefund;
import com.ray.enums.AdminRole;
import com.ray.enums.AdminStatus;
import com.ray.enums.CustomerServiceTicketStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.AdminUserMapper;
import com.ray.mapper.CustomerServiceAttachmentMapper;
import com.ray.mapper.CustomerServiceMessageMapper;
import com.ray.mapper.CustomerServiceQuickReplyMapper;
import com.ray.mapper.CustomerServiceReadCursorMapper;
import com.ray.mapper.CustomerServiceTagMapper;
import com.ray.mapper.CustomerServiceTicketMapper;
import com.ray.mapper.CustomerServiceTicketTagMapper;
import com.ray.mapper.CustomerServiceTransferMapper;
import com.ray.mapper.UserVoucherMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherRedemptionMapper;
import com.ray.mapper.VoucherRefundMapper;
import com.ray.result.PageResult;
import com.ray.service.AdminAuthService;
import com.ray.service.AdminAuditService;
import com.ray.service.CurrentUserProvider;
import com.ray.service.CustomerServiceAttachmentService;
import com.ray.service.CustomerServiceAttachmentService.Actor;
import com.ray.service.CustomerServiceService;
import com.ray.service.MerchantAuthService;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.CustomerServiceAttachmentVO;
import com.ray.vo.CustomerServiceMessagePageVO;
import com.ray.vo.CustomerServiceMessageVO;
import com.ray.vo.CustomerServiceQuickReplyVO;
import com.ray.vo.CustomerServiceTagVO;
import com.ray.vo.CustomerServiceTicketVO;
import com.ray.vo.CustomerServiceTransferVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台统一客服实现，集中处理三端权限、状态迁移、消息游标、标签、转交和 SLA。 */
@Slf4j
@Service
public class CustomerServiceServiceImpl implements CustomerServiceService {
    private static final Set<String> TYPES = Set.of("REFUND", "REDEMPTION", "ORDER", "SETTLEMENT", "GENERAL");
    private static final Map<CustomerServiceTicketStatus, Set<CustomerServiceTicketStatus>> TRANSITIONS = transitions();

    private final CustomerServiceTicketMapper tickets;
    private final CustomerServiceMessageMapper messages;
    private final CustomerServiceAttachmentMapper attachments;
    private final CustomerServiceReadCursorMapper cursors;
    private final CustomerServiceTagMapper tags;
    private final CustomerServiceTicketTagMapper ticketTags;
    private final CustomerServiceTransferMapper transfers;
    private final CustomerServiceQuickReplyMapper quickReplies;
    private final VoucherOrderMapper orders;
    private final UserVoucherMapper vouchers;
    private final VoucherRefundMapper refunds;
    private final VoucherRedemptionMapper redemptions;
    private final AdminUserMapper admins;
    private final CurrentUserProvider userProvider;
    private final MerchantAuthService merchantAuth;
    private final AdminAuthService adminAuth;
    private final AdminAuditService audit;
    private final CustomerServiceAttachmentService attachmentService;
    private final RedisIdWorker idWorker;

    /** 注入客服聚合依赖、关联业务查询和三个登录域的身份服务。 */
    public CustomerServiceServiceImpl(CustomerServiceTicketMapper tickets,
            CustomerServiceMessageMapper messages, CustomerServiceAttachmentMapper attachments,
            CustomerServiceReadCursorMapper cursors, CustomerServiceTagMapper tags,
            CustomerServiceTicketTagMapper ticketTags, CustomerServiceTransferMapper transfers,
            CustomerServiceQuickReplyMapper quickReplies, VoucherOrderMapper orders,
            UserVoucherMapper vouchers, VoucherRefundMapper refunds, VoucherRedemptionMapper redemptions,
            AdminUserMapper admins, CurrentUserProvider userProvider, MerchantAuthService merchantAuth,
            AdminAuthService adminAuth, AdminAuditService audit,
            CustomerServiceAttachmentService attachmentService, RedisIdWorker idWorker) {
        this.tickets = tickets;
        this.messages = messages;
        this.attachments = attachments;
        this.cursors = cursors;
        this.tags = tags;
        this.ticketTags = ticketTags;
        this.transfers = transfers;
        this.quickReplies = quickReplies;
        this.orders = orders;
        this.vouchers = vouchers;
        this.refunds = refunds;
        this.redemptions = redemptions;
        this.admins = admins;
        this.userProvider = userProvider;
        this.merchantAuth = merchantAuth;
        this.adminAuth = adminAuth;
        this.audit = audit;
        this.attachmentService = attachmentService;
        this.idWorker = idWorker;
    }

    /** 创建仅归当前消费者所有的平台工单。 */
    @Override
    @Transactional
    public CustomerServiceTicketVO createForConsumer(CustomerServiceCreateDTO request) {
        Long userId = userProvider.requireUserId();
        RelatedIds related = parseRelated(request);
        validateConsumerLinks(userId, related);
        CustomerServiceTicket ticket = baseTicket(request, "CONSUMER", userId, related)
                .setRelatedUserId(userId).setUserId(userId);
        tickets.insert(ticket);
        createInitialMessage(ticket, request, Actor.CONSUMER, userId);
        log.info("[平台客服] 消费者创建工单，ticketId={}，userId={}", ticket.getId(), userId);
        return view(ticket, Audience.consumer(userId), true);
    }

    /** 只按申请人类型和申请人账号查询消费者自己的工单。 */
    @Override
    public PageResult<CustomerServiceTicketVO> listForConsumer(int page, int size) {
        Long userId = userProvider.requireUserId();
        Page<CustomerServiceTicket> result = tickets.selectPage(new Page<>(page, size),
                applicantQuery("CONSUMER", userId).orderByDesc("last_message_time", "id"));
        return page(result, page, size, Audience.consumer(userId));
    }

    /** 返回当前消费者的工单详情。 */
    @Override
    public CustomerServiceTicketVO getForConsumer(Long id) {
        Long userId = userProvider.requireUserId();
        return view(requireApplicantTicket(id, "CONSUMER", userId), Audience.consumer(userId), true);
    }

    /** 消费者回复只在允许的等待状态下唤醒已认领工单。 */
    @Override
    @Transactional
    public CustomerServiceTicketVO replyForConsumer(Long id, CustomerServiceReplyDTO request) {
        Long userId = userProvider.requireUserId();
        CustomerServiceTicket ticket = requireApplicantTicketForUpdate(id, "CONSUMER", userId);
        replyForApplicant(ticket, request, Actor.CONSUMER, userId);
        return view(ticket, Audience.consumer(userId), true);
    }

    /** 游标读取公开消息并推进消费者已读位置。 */
    @Override
    @Transactional
    public CustomerServiceMessagePageVO messagesForConsumer(Long id, Long beforeId, Long afterId, int limit) {
        Long userId = userProvider.requireUserId();
        CustomerServiceTicket ticket = requireApplicantTicket(id, "CONSUMER", userId);
        return messagePage(ticket, Audience.consumer(userId), beforeId, afterId, limit, true);
    }

    /** 消费者只能关闭本人且符合状态白名单的工单。 */
    @Override
    @Transactional
    public CustomerServiceTicketVO closeForConsumer(Long id) {
        Long userId = userProvider.requireUserId();
        CustomerServiceTicket ticket = requireApplicantTicketForUpdate(id, "CONSUMER", userId);
        CustomerServiceTicketStatus from = parseStatus(ticket.getStatus());
        ensureTransition(from, CustomerServiceTicketStatus.CLOSED);
        LocalDateTime now = LocalDateTime.now();
        applyStateFacts(ticket, CustomerServiceTicketStatus.CLOSED, now);
        ticket.setStatus(CustomerServiceTicketStatus.CLOSED.name()).setUpdateTime(now)
                .setVersion(ticket.getVersion() + 1);
        tickets.updateById(ticket);
        log.info("[平台客服] 消费者关闭工单，ticketId={}，userId={}", id, userId);
        return view(ticket, Audience.consumer(userId), true);
    }

    /** 已解决工单可直接重开；已关闭工单只在七天期限内允许重开。 */
    @Override
    @Transactional
    public CustomerServiceTicketVO reopenForConsumer(Long id) {
        Long userId = userProvider.requireUserId();
        CustomerServiceTicket ticket = requireApplicantTicketForUpdate(id, "CONSUMER", userId);
        CustomerServiceTicketStatus from = parseStatus(ticket.getStatus());
        if (from != CustomerServiceTicketStatus.RESOLVED && from != CustomerServiceTicketStatus.CLOSED) {
            throw BusinessException.conflict("TICKET_NOT_REOPENABLE", "当前工单状态不能重新打开");
        }
        LocalDateTime now = LocalDateTime.now();
        if (from == CustomerServiceTicketStatus.CLOSED && (ticket.getReopenDeadline() == null
                || ticket.getReopenDeadline().isBefore(now))) {
            throw BusinessException.conflict("TICKET_REOPEN_EXPIRED", "工单重新打开期限已过，请创建新工单");
        }
        CustomerServiceTicketStatus target = ticket.getAssigneeAdminId() == null
                ? CustomerServiceTicketStatus.OPEN : CustomerServiceTicketStatus.CLAIMED;
        ticket.setStatus(target.name()).setResolvedTime(null).setClosedTime(null).setReopenDeadline(null)
                .setWaitingCustomerSince(null).setWaitingMerchantSince(null).setSlaDeadline(now.plusHours(4))
                .setSlaBreached(false).setUpdateTime(now).setVersion(ticket.getVersion() + 1);
        tickets.updateById(ticket);
        log.info("[平台客服] 消费者重新打开工单，ticketId={}，from={}，to={}，userId={}",
                id, from, target, userId);
        return view(ticket, Audience.consumer(userId), true);
    }

    /** 创建由当前商户账号主动发起的平台工单。 */
    @Override
    @Transactional
    public CustomerServiceTicketVO createForMerchant(CustomerServiceCreateDTO request) {
        MerchantAccount account = activeMerchant();
        RelatedIds related = parseRelated(request);
        validateMerchantLinks(account, related);
        CustomerServiceTicket ticket = baseTicket(request, "MERCHANT", account.getId(), related)
                .setRelatedShopId(account.getShopId()).setShopId(account.getShopId());
        tickets.insert(ticket);
        createInitialMessage(ticket, request, Actor.MERCHANT, account.getId());
        log.info("[平台客服] 商户创建工单，ticketId={}，merchantAccountId={}", ticket.getId(), account.getId());
        return view(ticket, Audience.merchant(account.getId()), true);
    }

    /** 商户列表严格按申请账号查询，不以门店 ID 扩大可见范围。 */
    @Override
    public PageResult<CustomerServiceTicketVO> listForMerchant(int page, int size) {
        MerchantAccount account = activeMerchant();
        Page<CustomerServiceTicket> result = tickets.selectPage(new Page<>(page, size),
                applicantQuery("MERCHANT", account.getId()).orderByDesc("last_message_time", "id"));
        return page(result, page, size, Audience.merchant(account.getId()));
    }

    /** 返回当前商户账号主动创建的工单详情。 */
    @Override
    public CustomerServiceTicketVO getForMerchant(Long id) {
        MerchantAccount account = activeMerchant();
        return view(requireApplicantTicket(id, "MERCHANT", account.getId()), Audience.merchant(account.getId()), true);
    }

    /** 商户回复只唤醒等待商户的已认领工单。 */
    @Override
    @Transactional
    public CustomerServiceTicketVO replyForMerchant(Long id, CustomerServiceReplyDTO request) {
        MerchantAccount account = activeMerchant();
        CustomerServiceTicket ticket = requireApplicantTicketForUpdate(id, "MERCHANT", account.getId());
        replyForApplicant(ticket, request, Actor.MERCHANT, account.getId());
        return view(ticket, Audience.merchant(account.getId()), true);
    }

    /** 游标读取公开消息并推进商户账号独立的已读位置。 */
    @Override
    @Transactional
    public CustomerServiceMessagePageVO messagesForMerchant(Long id, Long beforeId, Long afterId, int limit) {
        MerchantAccount account = activeMerchant();
        CustomerServiceTicket ticket = requireApplicantTicket(id, "MERCHANT", account.getId());
        return messagePage(ticket, Audience.merchant(account.getId()), beforeId, afterId, limit, true);
    }

    /** 平台队列支持冻结状态、申请人类型和标签筛选，并优先返回超时、高优先级工单。 */
    @Override
    public PageResult<CustomerServiceTicketVO> listForAdmin(String queue, String status, String applicantType,
            Long tagId, int page, int size) {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_READ);
        Long adminId = adminAuth.currentAdminId();
        QueryWrapper<CustomerServiceTicket> query = new QueryWrapper<>();
        applyAdminQueue(query, queue, adminId);
        if (status != null && !status.isBlank()) query.eq("status", parseStatus(status).name());
        if (applicantType != null && !applicantType.isBlank()) {
            String type = applicantType.trim().toUpperCase(Locale.ROOT);
            if (!Set.of("CONSUMER", "MERCHANT").contains(type)) {
                throw BusinessException.badRequest("INVALID_APPLICANT_TYPE", "申请人类型无效");
            }
            query.eq("applicant_type", type);
        }
        if (tagId != null) {
            List<Long> ids = ticketTags.selectList(new QueryWrapper<CustomerServiceTicketTag>().eq("tag_id", tagId))
                    .stream().map(CustomerServiceTicketTag::getTicketId).toList();
            if (ids.isEmpty()) return new PageResult<>(List.of(), page, size, 0);
            query.in("id", ids);
        }
        query.orderByDesc("sla_breached")
                .orderByAsc("CASE priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'NORMAL' THEN 2 ELSE 3 END")
                .orderByAsc("sla_deadline").orderByDesc("last_message_time");
        Page<CustomerServiceTicket> result = tickets.selectPage(new Page<>(page, size), query);
        return page(result, page, size, Audience.admin(adminId));
    }

    private void applyAdminQueue(QueryWrapper<CustomerServiceTicket> query, String queue, Long adminId) {
        if (queue == null || queue.isBlank() || "ALL".equalsIgnoreCase(queue)) return;
        switch (queue.trim().toUpperCase(Locale.ROOT)) {
            case "UNCLAIMED" -> query.eq("status", CustomerServiceTicketStatus.OPEN.name())
                    .isNull("assignee_admin_id");
            case "MINE" -> query.eq("assignee_admin_id", adminId)
                    .notIn("status", CustomerServiceTicketStatus.CLOSED.name());
            case "SLA_BREACHED" -> query.eq("sla_breached", true)
                    .notIn("status", CustomerServiceTicketStatus.RESOLVED.name(),
                            CustomerServiceTicketStatus.CLOSED.name());
            case "HIGH_PRIORITY" -> query.in("priority", "URGENT", "HIGH")
                    .notIn("status", CustomerServiceTicketStatus.RESOLVED.name(),
                            CustomerServiceTicketStatus.CLOSED.name());
            case "REFUND" -> query.and(wrapper -> wrapper.eq("type", "REFUND").or().isNotNull("refund_id"));
            default -> throw BusinessException.badRequest("INVALID_CUSTOMER_SERVICE_QUEUE", "客服队列无效");
        }
    }

    /** 平台详情包含内部备注和当前客服的未读数量。 */
    @Override
    public CustomerServiceTicketVO getForAdmin(Long id) {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_READ);
        return view(requireTicket(id), Audience.admin(adminAuth.currentAdminId()), true);
    }

    /** 平台消息游标可见公开消息、内部备注和系统事件。 */
    @Override
    @Transactional
    public CustomerServiceMessagePageVO messagesForAdmin(Long id, Long beforeId, Long afterId, int limit) {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_READ);
        return messagePage(requireTicket(id), Audience.admin(adminAuth.currentAdminId()), beforeId, afterId, limit, true);
    }

    /** 单条条件更新保证并发认领时只有一个客服成功。 */
    @Override
    @Transactional
    public CustomerServiceTicketVO claim(Long id) {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_MANAGE);
        Long adminId = adminAuth.currentAdminId();
        if (tickets.claimOpenTicket(id, adminId) != 1) {
            if (tickets.selectById(id) == null) throw BusinessException.notFound("TICKET_NOT_FOUND", "客服工单不存在");
            throw BusinessException.conflict("TICKET_ALREADY_CLAIMED", "工单已被认领或当前状态不可认领");
        }
        addSystemMessage(id, "工单已被客服认领", adminId);
        audit.record(adminId, "CUSTOMER_SERVICE_TICKET_CLAIMED", "CUSTOMER_SERVICE_TICKET", id.toString(), "SUCCEEDED", null);
        log.info("[平台客服] 工单认领成功，ticketId={}，adminId={}", id, adminId);
        return view(requireTicket(id), Audience.admin(adminId), true);
    }

    /** 公开回复推进等待申请人状态；内部备注只记录标志，不改变主状态。 */
    @Override
    @Transactional
    public CustomerServiceTicketVO replyForAdmin(Long id, CustomerServiceReplyDTO request, boolean internal) {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_MANAGE);
        Long adminId = adminAuth.currentAdminId();
        CustomerServiceTicket ticket = requireTicketForUpdate(id);
        ensureAssignedToCurrent(ticket, adminId);
        if (CustomerServiceTicketStatus.CLOSED.name().equals(ticket.getStatus())) {
            throw BusinessException.conflict("TICKET_CLOSED", "已关闭工单不能继续修改");
        }
        LocalDateTime now = LocalDateTime.now();
        CustomerServiceMessage message = addMessage(ticket.getId(), "ADMIN", adminId,
                internal ? "INTERNAL" : "PUBLIC", request.content(), request.messageType());
        attachmentService.bindToMessage(ticket.getId(), message.getId(), request.attachmentIds(), Actor.ADMIN);
        if (internal) {
            ticket.setHasInternalNote(true).setLastMessageTime(now);
        } else {
            CustomerServiceTicketStatus target = "MERCHANT".equals(ticket.getApplicantType())
                    ? CustomerServiceTicketStatus.WAITING_MERCHANT : CustomerServiceTicketStatus.WAITING_CUSTOMER;
            ensureTransition(parseStatus(ticket.getStatus()), target);
            ticket.setStatus(target.name()).setLastResponseTime(now).setLastMessageTime(now)
                    .setWaitingCustomerSince(target == CustomerServiceTicketStatus.WAITING_CUSTOMER ? now : null)
                    .setWaitingMerchantSince(target == CustomerServiceTicketStatus.WAITING_MERCHANT ? now : null);
            if (ticket.getFirstResponseTime() == null) ticket.setFirstResponseTime(now);
        }
        ticket.setUpdateTime(now).setVersion(ticket.getVersion() + 1);
        tickets.updateById(ticket);
        audit.record(adminId, internal ? "CUSTOMER_SERVICE_INTERNAL_NOTE_CREATED" : "CUSTOMER_SERVICE_REPLY_CREATED",
                "CUSTOMER_SERVICE_TICKET", id.toString(), "SUCCEEDED", null);
        return view(ticket, Audience.admin(adminId), true);
    }

    /** 所有人工状态更新都经过集中白名单，并维护对应时间事实。 */
    @Override
    @Transactional
    public CustomerServiceTicketVO updateStatus(Long id, CustomerServiceStatusDTO request) {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_MANAGE);
        Long adminId = adminAuth.currentAdminId();
        CustomerServiceTicket ticket = requireTicketForUpdate(id);
        CustomerServiceTicketStatus from = parseStatus(ticket.getStatus());
        CustomerServiceTicketStatus to = request.status();
        ensureTransition(from, to);
        if (from != CustomerServiceTicketStatus.OPEN) ensureAssignedToCurrent(ticket, adminId);
        LocalDateTime now = LocalDateTime.now();
        applyStateFacts(ticket, to, now);
        ticket.setStatus(to.name()).setUpdateTime(now).setVersion(ticket.getVersion() + 1);
        tickets.updateById(ticket);
        addSystemMessage(id, "工单状态由 " + from.name() + " 变更为 " + to.name(), adminId);
        audit.record(adminId, "CUSTOMER_SERVICE_STATUS_CHANGED", "CUSTOMER_SERVICE_TICKET", id.toString(),
                "SUCCEEDED", from.name() + " -> " + to.name());
        log.info("[平台客服] 工单状态变更，ticketId={}，from={}，to={}，adminId={}", id, from, to, adminId);
        return view(ticket, Audience.admin(adminId), true);
    }

    /** 转交校验目标账号为启用客服，并保留不可覆盖的转交事实。 */
    @Override
    @Transactional
    public CustomerServiceTicketVO transfer(Long id, CustomerServiceTransferDTO request) {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_MANAGE);
        Long operatorId = adminAuth.currentAdminId();
        Long targetId = IdUtils.parse(request.assigneeAdminId(), "assigneeAdminId");
        AdminUser target = admins.selectById(targetId);
        if (target == null || !AdminStatus.ACTIVE.name().equals(target.getStatus())
                || !(AdminRole.CUSTOMER_SERVICE.name().equals(target.getRole()) || AdminRole.PLATFORM_ADMIN.name().equals(target.getRole()))) {
            throw BusinessException.badRequest("INVALID_TRANSFER_TARGET", "目标账号不是启用的平台客服");
        }
        CustomerServiceTicket ticket = requireTicketForUpdate(id);
        ensureAssignedToCurrent(ticket, operatorId);
        Long fromId = ticket.getAssigneeAdminId();
        if (targetId.equals(fromId)) throw BusinessException.conflict("SAME_ASSIGNEE", "目标客服与当前客服相同");
        CustomerServiceTicketStatus current = parseStatus(ticket.getStatus());
        if (current == CustomerServiceTicketStatus.CLOSED || current == CustomerServiceTicketStatus.RESOLVED) {
            throw BusinessException.conflict("TICKET_NOT_TRANSFERABLE", "当前状态不能转交");
        }
        if (current != CustomerServiceTicketStatus.CLAIMED) ensureTransition(current, CustomerServiceTicketStatus.CLAIMED);
        ticket.setAssigneeAdminId(targetId).setStatus(CustomerServiceTicketStatus.CLAIMED.name())
                .setWaitingCustomerSince(null).setWaitingMerchantSince(null)
                .setUpdateTime(LocalDateTime.now()).setVersion(ticket.getVersion() + 1);
        tickets.updateById(ticket);
        CustomerServiceTransfer transfer = new CustomerServiceTransfer()
                .setId(idWorker.nextId("customer-service-transfer")).setTicketId(id)
                .setFromAdminId(fromId).setToAdminId(targetId).setOperatorAdminId(operatorId)
                .setReason(request.reason().trim());
        transfers.insert(transfer);
        addSystemMessage(id, "工单已转交给其他客服", operatorId);
        audit.record(operatorId, "CUSTOMER_SERVICE_TICKET_TRANSFERRED", "CUSTOMER_SERVICE_TICKET", id.toString(),
                "SUCCEEDED", "toAdminId=" + targetId);
        return view(ticket, Audience.admin(operatorId), true);
    }

    /** 返回按时间正序排列的工单转交记录。 */
    @Override
    public List<CustomerServiceTransferVO> transfers(Long id) {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_READ);
        requireTicket(id);
        return transfers.selectList(new QueryWrapper<CustomerServiceTransfer>()
                        .eq("ticket_id", id).orderByAsc("create_time", "id"))
                .stream().map(this::transferView).toList();
    }

    /** 返回启用标签字典。 */
    @Override
    public List<CustomerServiceTagVO> tags() {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_READ);
        return tags.selectList(new QueryWrapper<CustomerServiceTag>().eq("enabled", true).orderByAsc("id"))
                .stream().map(this::tagView).toList();
    }

    /** 在工单锁内替换标签，重复标签或停用标签会被拒绝。 */
    @Override
    @Transactional
    public CustomerServiceTicketVO replaceTags(Long id, CustomerServiceTagUpdateDTO request) {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_MANAGE);
        Long adminId = adminAuth.currentAdminId();
        CustomerServiceTicket ticket = requireTicketForUpdate(id);
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String value : request.tagIds()) ids.add(IdUtils.parse(value, "tagIds"));
        if (ids.size() != request.tagIds().size()) throw BusinessException.badRequest("DUPLICATE_TAG", "工单标签不能重复");
        if (!ids.isEmpty() && tags.selectCount(new QueryWrapper<CustomerServiceTag>().in("id", ids).eq("enabled", true)) != ids.size()) {
            throw BusinessException.badRequest("INVALID_TAG", "客服标签不存在或已停用");
        }
        ticketTags.delete(new QueryWrapper<CustomerServiceTicketTag>().eq("ticket_id", id));
        for (Long tagId : ids) ticketTags.insert(new CustomerServiceTicketTag()
                .setId(idWorker.nextId("customer-service-ticket-tag")).setTicketId(id)
                .setTagId(tagId).setCreatedByAdminId(adminId));
        audit.record(adminId, "CUSTOMER_SERVICE_TAGS_REPLACED", "CUSTOMER_SERVICE_TICKET", id.toString(), "SUCCEEDED", null);
        return view(ticket, Audience.admin(adminId), true);
    }

    /** 返回当前客服个人模板与平台团队模板。 */
    @Override
    public List<CustomerServiceQuickReplyVO> quickReplies() {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_READ);
        Long adminId = adminAuth.currentAdminId();
        return quickReplies.selectList(new QueryWrapper<CustomerServiceQuickReply>()
                        .eq("enabled", true).and(q -> q.eq("scope", "TEAM")
                                .or(w -> w.eq("scope", "PERSONAL").eq("owner_admin_id", adminId)))
                        .orderByAsc("sort_order", "id"))
                .stream().map(this::quickReplyView).toList();
    }

    /** 普通客服可建个人模板，团队模板只允许平台管理员创建。 */
    @Override
    @Transactional
    public CustomerServiceQuickReplyVO createQuickReply(CustomerServiceQuickReplyDTO request) {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_MANAGE);
        Long adminId = adminAuth.currentAdminId();
        String scope = request.scope().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PERSONAL", "TEAM").contains(scope)) throw BusinessException.badRequest("INVALID_QUICK_REPLY_SCOPE", "快捷回复范围无效");
        if ("TEAM".equals(scope) && adminAuth.currentAdmin().role() != AdminRole.PLATFORM_ADMIN) {
            throw BusinessException.forbidden("TEAM_QUICK_REPLY_FORBIDDEN", "只有平台管理员可以维护团队快捷回复");
        }
        CustomerServiceQuickReply item = new CustomerServiceQuickReply()
                .setId(idWorker.nextId("customer-service-quick-reply"))
                .setTitle(request.title().trim()).setContent(request.content().trim())
                .setScope(scope).setOwnerAdminId("PERSONAL".equals(scope) ? adminId : null)
                .setEnabled(true).setSortOrder(request.sortOrder() == null ? 100 : request.sortOrder());
        quickReplies.insert(item);
        audit.record(adminId, "CUSTOMER_SERVICE_QUICK_REPLY_CREATED", "CUSTOMER_SERVICE_QUICK_REPLY",
                item.getId().toString(), "SUCCEEDED", scope);
        return quickReplyView(item);
    }

    /** 个人模板只可由所有者删除，团队模板只可由平台管理员删除。 */
    @Override
    @Transactional
    public void deleteQuickReply(Long id) {
        adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_MANAGE);
        Long adminId = adminAuth.currentAdminId();
        CustomerServiceQuickReply item = quickReplies.selectById(id);
        if (item == null || !Boolean.TRUE.equals(item.getEnabled())) throw BusinessException.notFound("QUICK_REPLY_NOT_FOUND", "快捷回复不存在");
        boolean teamAdmin = "TEAM".equals(item.getScope()) && adminAuth.currentAdmin().role() == AdminRole.PLATFORM_ADMIN;
        boolean owner = "PERSONAL".equals(item.getScope()) && adminId.equals(item.getOwnerAdminId());
        if (!teamAdmin && !owner) throw BusinessException.forbidden("QUICK_REPLY_FORBIDDEN", "无权删除该快捷回复");
        quickReplies.update(null, new UpdateWrapper<CustomerServiceQuickReply>().eq("id", id).set("enabled", false));
        audit.record(adminId, "CUSTOMER_SERVICE_QUICK_REPLY_DELETED", "CUSTOMER_SERVICE_QUICK_REPLY",
                id.toString(), "SUCCEEDED", item.getScope());
    }

    /** 自动关闭已解决或长期等待申请人的工单，关闭后普通消息接口不可再修改。 */
    @Override
    @Scheduled(fixedDelayString = "${ray.customer-service.auto-close-scan-ms:3600000}")
    @Transactional
    public int autoCloseIdleTickets() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        List<CustomerServiceTicket> idle = tickets.selectList(new QueryWrapper<CustomerServiceTicket>()
                .in("status", CustomerServiceTicketStatus.RESOLVED.name(),
                        CustomerServiceTicketStatus.WAITING_CUSTOMER.name(), CustomerServiceTicketStatus.WAITING_MERCHANT.name())
                .and(q -> q.le("last_message_time", cutoff).or().le("update_time", cutoff)));
        int count = 0;
        for (CustomerServiceTicket ticket : idle) {
            CustomerServiceTicketStatus from = parseStatus(ticket.getStatus());
            if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(CustomerServiceTicketStatus.CLOSED)) continue;
            LocalDateTime now = LocalDateTime.now();
            int changed = tickets.update(null, new UpdateWrapper<CustomerServiceTicket>()
                    .eq("id", ticket.getId()).eq("status", from.name())
                    .set("status", "CLOSED").set("closed_time", now).set("reopen_deadline", now.plusDays(7))
                    .setSql("version=version+1"));
            if (changed == 1) count++;
        }
        if (count > 0) log.info("[平台客服] 自动关闭空闲工单，count={}", count);
        return count;
    }

    /** 将未关闭且超过 SLA 截止时间的工单批量标记为超时。 */
    @Override
    @Scheduled(fixedDelayString = "${ray.customer-service.sla-scan-ms:60000}")
    @Transactional
    public int refreshSlaBreaches() {
        return tickets.update(null, new UpdateWrapper<CustomerServiceTicket>()
                .eq("sla_breached", false).lt("sla_deadline", LocalDateTime.now())
                .notIn("status", CustomerServiceTicketStatus.RESOLVED.name(), CustomerServiceTicketStatus.CLOSED.name())
                .set("sla_breached", true));
    }

    private void createInitialMessage(CustomerServiceTicket ticket, CustomerServiceCreateDTO request, Actor actor, Long actorId) {
        if (request.description() == null || request.description().isBlank()) {
            if (!request.attachmentIds().isEmpty()) throw BusinessException.badRequest("ATTACHMENT_REQUIRES_MESSAGE", "附件必须随问题描述一起提交");
            return;
        }
        CustomerServiceMessage message = addMessage(ticket.getId(), actor.name(), actorId, "PUBLIC", request.description(),
                request.attachmentIds().isEmpty() ? "TEXT" : "ATTACHMENT");
        attachmentService.bindToMessage(ticket.getId(), message.getId(), request.attachmentIds(), actor);
    }

    private void replyForApplicant(CustomerServiceTicket ticket, CustomerServiceReplyDTO request, Actor actor, Long actorId) {
        CustomerServiceTicketStatus current = parseStatus(ticket.getStatus());
        if (current == CustomerServiceTicketStatus.CLOSED) throw BusinessException.conflict("TICKET_CLOSED", "已关闭工单不能继续修改");
        CustomerServiceTicketStatus waiting = actor == Actor.CONSUMER
                ? CustomerServiceTicketStatus.WAITING_CUSTOMER : CustomerServiceTicketStatus.WAITING_MERCHANT;
        LocalDateTime now = LocalDateTime.now();
        if (current == waiting || current == CustomerServiceTicketStatus.RESOLVED) {
            ensureTransition(current, CustomerServiceTicketStatus.CLAIMED);
            ensureAssigned(ticket);
            ticket.setStatus(CustomerServiceTicketStatus.CLAIMED.name()).setResolvedTime(null);
        } else if (current != CustomerServiceTicketStatus.OPEN && current != CustomerServiceTicketStatus.CLAIMED) {
            throw BusinessException.conflict("TICKET_STATE_CONFLICT", "当前工单状态不能由申请人回复");
        }
        CustomerServiceMessage message = addMessage(ticket.getId(), actor.name(), actorId, "PUBLIC", request.content(), request.messageType());
        attachmentService.bindToMessage(ticket.getId(), message.getId(), request.attachmentIds(), actor);
        ticket.setLastMessageTime(now).setWaitingCustomerSince(null).setWaitingMerchantSince(null)
                .setUpdateTime(now).setVersion(ticket.getVersion() + 1);
        tickets.updateById(ticket);
    }

    private CustomerServiceTicket baseTicket(CustomerServiceCreateDTO request, String applicantType,
            Long applicantId, RelatedIds related) {
        String type = request.type().trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw BusinessException.badRequest("INVALID_TICKET_TYPE", "工单类型无效");
        long id = idWorker.nextId("customer-service-ticket");
        LocalDateTime now = LocalDateTime.now();
        return new CustomerServiceTicket().setId(id)
                .setTicketNo("CS" + String.format("%014d", id % 100_000_000_000_000L))
                .setType(type).setStatus(CustomerServiceTicketStatus.OPEN.name()).setPriority("NORMAL")
                .setApplicantType(applicantType).setApplicantId(applicantId)
                .setOrderId(related.orderId()).setVoucherId(related.voucherId())
                .setRefundId(related.refundId()).setRedemptionId(related.redemptionId())
                .setSubject(request.subject().trim()).setDescription(trimToNull(request.description()))
                .setCreatedByType(applicantType).setCreatedById(applicantId)
                .setLastMessageTime(now).setSlaDeadline(now.plusHours(4))
                .setSlaBreached(false).setHasInternalNote(false).setVersion(0);
    }

    private CustomerServiceMessage addMessage(Long ticketId, String senderType, Long senderId,
            String visibility, String content, String messageType) {
        String normalized = messageType == null || messageType.isBlank() ? "TEXT" : messageType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("TEXT", "IMAGE", "ATTACHMENT", "SYSTEM").contains(normalized)) {
            throw BusinessException.badRequest("INVALID_MESSAGE_TYPE", "消息类型无效");
        }
        CustomerServiceMessage message = new CustomerServiceMessage()
                .setId(idWorker.nextId("customer-service-message")).setTicketId(ticketId)
                .setSenderType(senderType).setSenderId(senderId).setVisibility(visibility)
                .setMessageType(normalized).setContent(content.trim());
        messages.insert(message);
        return message;
    }

    private void addSystemMessage(Long ticketId, String content, Long operatorId) {
        addMessage(ticketId, "SYSTEM", operatorId, "INTERNAL", content, "SYSTEM");
        tickets.update(null, new UpdateWrapper<CustomerServiceTicket>().eq("id", ticketId)
                .set("has_internal_note", true).set("last_message_time", LocalDateTime.now()));
    }

    private CustomerServiceMessagePageVO messagePage(CustomerServiceTicket ticket, Audience audience,
            Long beforeId, Long afterId, int limit, boolean markRead) {
        if (beforeId != null && afterId != null) throw BusinessException.badRequest("MESSAGE_CURSOR_CONFLICT", "before_message_id 与 after_message_id 不能同时使用");
        QueryWrapper<CustomerServiceMessage> query = new QueryWrapper<CustomerServiceMessage>().eq("ticket_id", ticket.getId());
        if (!audience.internal()) query.eq("visibility", "PUBLIC");
        boolean ascending = afterId != null;
        if (beforeId != null) query.lt("id", beforeId);
        if (afterId != null) query.gt("id", afterId);
        if (ascending) query.orderByAsc("id"); else query.orderByDesc("id");
        query.last("LIMIT " + (limit + 1));
        List<CustomerServiceMessage> fetched = new ArrayList<>(messages.selectList(query));
        boolean hasMore = fetched.size() > limit;
        if (hasMore) fetched.remove(fetched.size() - 1);
        if (!ascending) Collections.reverse(fetched);
        List<CustomerServiceMessageVO> items = fetched.stream().map(message -> messageView(message, audience)).toList();
        if (markRead && !fetched.isEmpty()) {
            long newest = fetched.get(fetched.size() - 1).getId();
            cursors.advance(idWorker.nextId("customer-service-read-cursor"), ticket.getId(),
                    audience.type(), audience.id(), newest);
        }
        return new CustomerServiceMessagePageVO(items,
                fetched.isEmpty() ? null : IdUtils.format(fetched.get(0).getId()),
                fetched.isEmpty() ? null : IdUtils.format(fetched.get(fetched.size() - 1).getId()), hasMore);
    }

    private CustomerServiceTicketVO view(CustomerServiceTicket ticket, Audience audience, boolean includeMessages) {
        List<CustomerServiceMessageVO> latest = includeMessages
                ? messagePage(ticket, audience, null, null, 20, false).items() : List.of();
        return new CustomerServiceTicketVO(IdUtils.format(ticket.getId()), ticket.getTicketNo(), ticket.getType(),
                ticket.getStatus(), ticket.getPriority(), ticket.getApplicantType(), IdUtils.format(ticket.getApplicantId()),
                IdUtils.format(ticket.getRelatedUserId()), IdUtils.format(ticket.getRelatedShopId()),
                IdUtils.format(ticket.getOrderId()), IdUtils.format(ticket.getVoucherId()), IdUtils.format(ticket.getRefundId()),
                IdUtils.format(ticket.getRedemptionId()), ticket.getSubject(), ticket.getDescription(),
                IdUtils.format(ticket.getAssigneeAdminId()), ticket.getFirstResponseTime(), ticket.getLastResponseTime(),
                ticket.getWaitingCustomerSince(), ticket.getWaitingMerchantSince(), ticket.getResolvedTime(),
                ticket.getClosedTime(), ticket.getSlaDeadline(), isSlaBreached(ticket),
                Boolean.TRUE.equals(ticket.getHasInternalNote()), unread(ticket, audience), ticket.getLastMessageTime(),
                ticket.getCreateTime(), ticket.getUpdateTime(), tagViews(ticket.getId()), latest);
    }

    private CustomerServiceMessageVO messageView(CustomerServiceMessage message, Audience audience) {
        List<CustomerServiceAttachmentVO> messageAttachments = attachments.selectList(
                        new QueryWrapper<CustomerServiceAttachment>().eq("message_id", message.getId()).eq("status", "BOUND").orderByAsc("id"))
                .stream().map(attachment -> attachmentView(attachment, audience)).toList();
        return new CustomerServiceMessageVO(IdUtils.format(message.getId()), IdUtils.format(message.getTicketId()),
                message.getSenderType(), IdUtils.format(message.getSenderId()), message.getVisibility(),
                message.getMessageType(), message.getContent(), message.getCreateTime(), messageAttachments);
    }

    private CustomerServiceAttachmentVO attachmentView(CustomerServiceAttachment a, Audience audience) {
        String prefix = switch (audience.type()) {
            case "CONSUMER" -> "/v1/users/me/customer-service/tickets/";
            case "MERCHANT" -> "/v1/merchant/customer-service/tickets/";
            default -> "/v1/admin/customer-service/tickets/";
        };
        return new CustomerServiceAttachmentVO(IdUtils.format(a.getId()), IdUtils.format(a.getTicketId()),
                IdUtils.format(a.getMessageId()), a.getStatus(), a.getOriginalFilename(), a.getMimeType(), a.getByteSize(),
                prefix + a.getTicketId() + "/attachments/" + a.getId() + "/content");
    }

    private int unread(CustomerServiceTicket ticket, Audience audience) {
        CustomerServiceReadCursor cursor = cursors.selectOne(new QueryWrapper<CustomerServiceReadCursor>()
                .eq("ticket_id", ticket.getId()).eq("reader_type", audience.type()).eq("reader_id", audience.id()).last("LIMIT 1"));
        QueryWrapper<CustomerServiceMessage> query = new QueryWrapper<CustomerServiceMessage>().eq("ticket_id", ticket.getId());
        if (cursor != null) query.gt("id", cursor.getLastReadMessageId());
        if (!audience.internal()) query.eq("visibility", "PUBLIC");
        query.and(q -> q.ne("sender_type", audience.type()).or().ne("sender_id", audience.id()));
        return Math.toIntExact(messages.selectCount(query));
    }

    private List<CustomerServiceTagVO> tagViews(Long ticketId) {
        List<Long> ids = ticketTags.selectList(new QueryWrapper<CustomerServiceTicketTag>().eq("ticket_id", ticketId).orderByAsc("id"))
                .stream().map(CustomerServiceTicketTag::getTagId).toList();
        if (ids.isEmpty()) return List.of();
        return tags.selectBatchIds(ids).stream().map(this::tagView).toList();
    }

    private PageResult<CustomerServiceTicketVO> page(Page<CustomerServiceTicket> result, int page, int size, Audience audience) {
        return new PageResult<>(result.getRecords().stream().map(t -> view(t, audience, false)).toList(), page, size, result.getTotal());
    }

    private QueryWrapper<CustomerServiceTicket> applicantQuery(String type, Long id) {
        return new QueryWrapper<CustomerServiceTicket>().eq("applicant_type", type).eq("applicant_id", id);
    }

    private CustomerServiceTicket requireTicket(Long id) {
        CustomerServiceTicket ticket = tickets.selectById(id);
        if (ticket == null) throw BusinessException.notFound("TICKET_NOT_FOUND", "客服工单不存在");
        return ticket;
    }

    private CustomerServiceTicket requireTicketForUpdate(Long id) {
        CustomerServiceTicket ticket = tickets.selectByIdForUpdate(id);
        if (ticket == null) throw BusinessException.notFound("TICKET_NOT_FOUND", "客服工单不存在");
        return ticket;
    }

    private CustomerServiceTicket requireApplicantTicket(Long id, String type, Long applicantId) {
        CustomerServiceTicket ticket = tickets.selectOne(applicantQuery(type, applicantId).eq("id", id).last("LIMIT 1"));
        if (ticket == null) throw BusinessException.notFound("TICKET_NOT_FOUND", "客服工单不存在");
        return ticket;
    }

    private CustomerServiceTicket requireApplicantTicketForUpdate(Long id, String type, Long applicantId) {
        CustomerServiceTicket ticket = requireTicketForUpdate(id);
        if (!type.equals(ticket.getApplicantType()) || !applicantId.equals(ticket.getApplicantId())) {
            throw BusinessException.notFound("TICKET_NOT_FOUND", "客服工单不存在");
        }
        return ticket;
    }

    private MerchantAccount activeMerchant() {
        MerchantAccount account = merchantAuth.requireCurrentAccount();
        if (account.getShopId() == null) throw BusinessException.forbidden("MERCHANT_ACTIVATION_REQUIRED", "商户账号尚未激活");
        return account;
    }

    private void validateConsumerLinks(Long userId, RelatedIds ids) {
        VoucherOrder order = ids.orderId() == null ? null : orders.selectById(ids.orderId());
        UserVoucher voucher = ids.voucherId() == null ? null : vouchers.selectById(ids.voucherId());
        VoucherRefund refund = ids.refundId() == null ? null : refunds.selectById(ids.refundId());
        VoucherRedemption redemption = ids.redemptionId() == null ? null : redemptions.selectById(ids.redemptionId());
        if ((order != null && !userId.equals(order.getUserId())) || (voucher != null && !userId.equals(voucher.getUserId()))
                || (refund != null && !userId.equals(refund.getUserId()))) throw invalidLink();
        if ((ids.orderId() != null && order == null) || (ids.voucherId() != null && voucher == null)
                || (ids.refundId() != null && refund == null) || (ids.redemptionId() != null && redemption == null)) throw invalidLink();
        if (redemption != null) {
            UserVoucher redeemedVoucher = vouchers.selectById(redemption.getVoucherId());
            if (redeemedVoucher == null || !userId.equals(redeemedVoucher.getUserId())) throw invalidLink();
        }
        ensureRelatedConsistency(ids, order, voucher, refund, redemption);
    }

    private void validateMerchantLinks(MerchantAccount account, RelatedIds ids) {
        Long shopId = account.getShopId();
        VoucherOrder order = ids.orderId() == null ? null : orders.selectById(ids.orderId());
        UserVoucher voucher = ids.voucherId() == null ? null : vouchers.selectById(ids.voucherId());
        VoucherRefund refund = ids.refundId() == null ? null : refunds.selectById(ids.refundId());
        VoucherRedemption redemption = ids.redemptionId() == null ? null : redemptions.selectById(ids.redemptionId());
        if ((ids.orderId() != null && (order == null || !shopId.equals(order.getShopId())))
                || (ids.voucherId() != null && (voucher == null || !shopId.equals(voucher.getShopId())))
                || (ids.refundId() != null && (refund == null || !shopId.equals(refund.getShopId())))
                || (ids.redemptionId() != null && (redemption == null || !shopId.equals(redemption.getShopId())))) throw invalidLink();
        ensureRelatedConsistency(ids, order, voucher, refund, redemption);
    }

    private void ensureRelatedConsistency(RelatedIds ids, VoucherOrder order, UserVoucher voucher,
            VoucherRefund refund, VoucherRedemption redemption) {
        Set<Long> orderIds = new HashSet<>();
        if (order != null) orderIds.add(order.getId());
        if (voucher != null) orderIds.add(voucher.getOrderId());
        if (refund != null) orderIds.add(refund.getOrderId());
        if (redemption != null) orderIds.add(redemption.getOrderId());
        if (orderIds.size() > 1) throw BusinessException.badRequest("RELATED_RESOURCE_MISMATCH", "关联订单、券、退款或核销记录不属于同一业务链路");
    }

    private BusinessException invalidLink() {
        return BusinessException.notFound("RELATED_RESOURCE_NOT_FOUND", "关联业务不存在或不属于当前账号");
    }

    private RelatedIds parseRelated(CustomerServiceCreateDTO request) {
        return new RelatedIds(parseNullable(request.orderId(), "orderId"), parseNullable(request.voucherId(), "voucherId"),
                parseNullable(request.refundId(), "refundId"), parseNullable(request.redemptionId(), "redemptionId"));
    }

    private Long parseNullable(String value, String field) {
        return value == null || value.isBlank() ? null : IdUtils.parse(value, field);
    }

    private void ensureAssigned(CustomerServiceTicket ticket) {
        if (ticket.getAssigneeAdminId() == null) throw BusinessException.conflict("TICKET_NOT_CLAIMED", "工单尚未认领");
    }

    private void ensureAssignedToCurrent(CustomerServiceTicket ticket, Long adminId) {
        ensureAssigned(ticket);
        if (!adminId.equals(ticket.getAssigneeAdminId()) && adminAuth.currentAdmin().role() != AdminRole.PLATFORM_ADMIN) {
            throw BusinessException.forbidden("TICKET_ASSIGNEE_REQUIRED", "只有当前处理人可以回复该工单");
        }
    }

    private void ensureTransition(CustomerServiceTicketStatus from, CustomerServiceTicketStatus to) {
        if (from == to || !TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw BusinessException.conflict("INVALID_TICKET_TRANSITION", "不允许从 " + from.name() + " 切换到 " + to.name());
        }
    }

    private void applyStateFacts(CustomerServiceTicket ticket, CustomerServiceTicketStatus status, LocalDateTime now) {
        ticket.setWaitingCustomerSince(status == CustomerServiceTicketStatus.WAITING_CUSTOMER ? now : null)
                .setWaitingMerchantSince(status == CustomerServiceTicketStatus.WAITING_MERCHANT ? now : null);
        if (status == CustomerServiceTicketStatus.RESOLVED) ticket.setResolvedTime(now);
        if (status == CustomerServiceTicketStatus.CLOSED) ticket.setClosedTime(now).setReopenDeadline(now.plusDays(7));
        if (status == CustomerServiceTicketStatus.CLAIMED) ticket.setResolvedTime(null).setClosedTime(null);
    }

    private boolean isSlaBreached(CustomerServiceTicket ticket) {
        return Boolean.TRUE.equals(ticket.getSlaBreached()) || (ticket.getSlaDeadline() != null
                && ticket.getSlaDeadline().isBefore(LocalDateTime.now())
                && !Set.of("RESOLVED", "CLOSED").contains(ticket.getStatus()));
    }

    private CustomerServiceTicketStatus parseStatus(String value) {
        try {
            return CustomerServiceTicketStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw BusinessException.badRequest("INVALID_TICKET_STATUS", "工单状态无效");
        }
    }

    private CustomerServiceTagVO tagView(CustomerServiceTag tag) {
        return new CustomerServiceTagVO(IdUtils.format(tag.getId()), tag.getCode(), tag.getName(), tag.getColor());
    }

    private CustomerServiceTransferVO transferView(CustomerServiceTransfer transfer) {
        return new CustomerServiceTransferVO(IdUtils.format(transfer.getId()), IdUtils.format(transfer.getTicketId()),
                IdUtils.format(transfer.getFromAdminId()), IdUtils.format(transfer.getToAdminId()),
                IdUtils.format(transfer.getOperatorAdminId()), transfer.getReason(), transfer.getCreateTime());
    }

    private CustomerServiceQuickReplyVO quickReplyView(CustomerServiceQuickReply item) {
        return new CustomerServiceQuickReplyVO(IdUtils.format(item.getId()), item.getTitle(), item.getContent(),
                item.getScope(), IdUtils.format(item.getOwnerAdminId()), item.getSortOrder());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static Map<CustomerServiceTicketStatus, Set<CustomerServiceTicketStatus>> transitions() {
        Map<CustomerServiceTicketStatus, Set<CustomerServiceTicketStatus>> result = new EnumMap<>(CustomerServiceTicketStatus.class);
        result.put(CustomerServiceTicketStatus.OPEN, Set.of(CustomerServiceTicketStatus.CLAIMED, CustomerServiceTicketStatus.CLOSED));
        result.put(CustomerServiceTicketStatus.CLAIMED, Set.of(CustomerServiceTicketStatus.WAITING_CUSTOMER,
                CustomerServiceTicketStatus.WAITING_MERCHANT, CustomerServiceTicketStatus.WAITING_INTERNAL,
                CustomerServiceTicketStatus.RESOLVED));
        result.put(CustomerServiceTicketStatus.WAITING_CUSTOMER, Set.of(CustomerServiceTicketStatus.CLAIMED, CustomerServiceTicketStatus.CLOSED));
        result.put(CustomerServiceTicketStatus.WAITING_MERCHANT, Set.of(CustomerServiceTicketStatus.CLAIMED, CustomerServiceTicketStatus.CLOSED));
        result.put(CustomerServiceTicketStatus.WAITING_INTERNAL, Set.of(CustomerServiceTicketStatus.CLAIMED, CustomerServiceTicketStatus.CLOSED));
        result.put(CustomerServiceTicketStatus.RESOLVED, Set.of(CustomerServiceTicketStatus.CLOSED, CustomerServiceTicketStatus.CLAIMED));
        result.put(CustomerServiceTicketStatus.CLOSED, Set.of());
        return Map.copyOf(result);
    }

    private record RelatedIds(Long orderId, Long voucherId, Long refundId, Long redemptionId) {}
    private record Audience(String type, Long id, boolean internal) {
        static Audience consumer(Long id) { return new Audience("CONSUMER", id, false); }
        static Audience merchant(Long id) { return new Audience("MERCHANT", id, false); }
        static Audience admin(Long id) { return new Audience("ADMIN", id, true); }
    }
}
