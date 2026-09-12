package com.ray.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.dto.CustomerServiceReplyDTO;
import com.ray.dto.CustomerServiceStatusDTO;
import com.ray.entity.CustomerServiceMessage;
import com.ray.entity.CustomerServiceTicket;
import com.ray.entity.MerchantAccount;
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
import com.ray.service.AdminAuthService;
import com.ray.service.AdminAuditService;
import com.ray.service.CurrentUserProvider;
import com.ray.service.CustomerServiceAttachmentService;
import com.ray.service.MerchantAuthService;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.CurrentAdminVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomerServiceServiceImplTest {
    private CustomerServiceTicketMapper tickets;
    private CustomerServiceMessageMapper messages;
    private CustomerServiceAttachmentMapper attachments;
    private CustomerServiceTicketTagMapper ticketTags;
    private CustomerServiceReadCursorMapper cursors;
    private AdminAuthService adminAuth;
    private CustomerServiceAttachmentService attachmentService;
    private CustomerServiceServiceImpl service;

    @BeforeEach
    void setUp() {
        tickets = mock(CustomerServiceTicketMapper.class);
        messages = mock(CustomerServiceMessageMapper.class);
        attachments = mock(CustomerServiceAttachmentMapper.class);
        cursors = mock(CustomerServiceReadCursorMapper.class);
        CustomerServiceTagMapper tags = mock(CustomerServiceTagMapper.class);
        ticketTags = mock(CustomerServiceTicketTagMapper.class);
        CustomerServiceTransferMapper transfers = mock(CustomerServiceTransferMapper.class);
        CustomerServiceQuickReplyMapper quickReplies = mock(CustomerServiceQuickReplyMapper.class);
        adminAuth = mock(AdminAuthService.class);
        attachmentService = mock(CustomerServiceAttachmentService.class);
        RedisIdWorker idWorker = mock(RedisIdWorker.class);
        AtomicLong ids = new AtomicLong(1000);
        when(idWorker.nextId(anyString())).thenAnswer(invocation -> ids.incrementAndGet());
        when(messages.selectList(org.mockito.ArgumentMatchers.<Wrapper<CustomerServiceMessage>>any())).thenReturn(List.of());
        when(attachments.selectList(org.mockito.ArgumentMatchers.<Wrapper<com.ray.entity.CustomerServiceAttachment>>any())).thenReturn(List.of());
        when(ticketTags.selectList(org.mockito.ArgumentMatchers.<Wrapper<com.ray.entity.CustomerServiceTicketTag>>any())).thenReturn(List.of());
        when(messages.selectCount(org.mockito.ArgumentMatchers.<Wrapper<CustomerServiceMessage>>any())).thenReturn(0L);
        when(adminAuth.currentAdminId()).thenReturn(5L);
        when(adminAuth.currentAdmin()).thenReturn(new CurrentAdminVO("5", "service.demo", "客服",
                AdminRole.CUSTOMER_SERVICE, "客服", AdminStatus.ACTIVE, "已启用", List.of(), false));
        service = new CustomerServiceServiceImpl(tickets, messages, attachments, cursors, tags, ticketTags,
                transfers, quickReplies, mock(VoucherOrderMapper.class), mock(UserVoucherMapper.class),
                mock(VoucherRefundMapper.class), mock(VoucherRedemptionMapper.class), mock(AdminUserMapper.class),
                mock(CurrentUserProvider.class), mock(MerchantAuthService.class), adminAuth, mock(AdminAuditService.class),
                attachmentService, idWorker);
    }

    @Test
    void twoClaimAttemptsOnlyOneCanWinAtomicCondition() {
        CustomerServiceTicket claimed = ticket(CustomerServiceTicketStatus.CLAIMED).setAssigneeAdminId(5L);
        when(tickets.claimOpenTicket(99L, 5L)).thenReturn(1, 0);
        when(tickets.selectById(99L)).thenReturn(claimed);

        assertEquals("CLAIMED", service.claim(99L).status());
        BusinessException conflict = assertThrows(BusinessException.class, () -> service.claim(99L));

        assertEquals("TICKET_ALREADY_CLAIMED", conflict.code());
    }

    @Test
    void internalNoteDoesNotChangeMainStatus() {
        CustomerServiceTicket ticket = ticket(CustomerServiceTicketStatus.CLAIMED).setAssigneeAdminId(5L);
        when(tickets.selectByIdForUpdate(99L)).thenReturn(ticket);

        service.replyForAdmin(99L, new CustomerServiceReplyDTO("仅客服可见", "TEXT", List.of()), true);

        assertEquals("CLAIMED", ticket.getStatus());
        assertEquals(true, ticket.getHasInternalNote());
        verify(tickets).updateById(ticket);
    }

    @Test
    void illegalStatusTransitionIsRejectedBeforePersistence() {
        CustomerServiceTicket ticket = ticket(CustomerServiceTicketStatus.OPEN);
        when(tickets.selectByIdForUpdate(99L)).thenReturn(ticket);

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> service.updateStatus(99L, new CustomerServiceStatusDTO(CustomerServiceTicketStatus.RESOLVED)));

        assertEquals("INVALID_TICKET_TRANSITION", conflict.code());
        verify(tickets, never()).updateById(any(CustomerServiceTicket.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void consumerListUsesApplicantIdentityInsteadOfLegacyUserField() {
        CurrentUserProvider users = mock(CurrentUserProvider.class);
        when(users.requireUserId()).thenReturn(7L);
        Page<CustomerServiceTicket> empty = new Page<>(1, 20);
        empty.setRecords(List.of());
        when(tickets.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(empty);
        service = new CustomerServiceServiceImpl(tickets, messages, attachments, cursors,
                mock(CustomerServiceTagMapper.class), ticketTags, mock(CustomerServiceTransferMapper.class),
                mock(CustomerServiceQuickReplyMapper.class), mock(VoucherOrderMapper.class), mock(UserVoucherMapper.class),
                mock(VoucherRefundMapper.class), mock(VoucherRedemptionMapper.class), mock(AdminUserMapper.class), users,
                mock(MerchantAuthService.class), adminAuth, mock(AdminAuditService.class), attachmentService,
                mock(RedisIdWorker.class));
        ArgumentCaptor<Wrapper<CustomerServiceTicket>> wrapper = ArgumentCaptor.forClass(Wrapper.class);

        service.listForConsumer(1, 20);
        verify(tickets).selectPage(any(Page.class), wrapper.capture());

        String sql = wrapper.getValue().getCustomSqlSegment();
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("applicant_type"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("applicant_id"));
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("user_id"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void merchantListUsesCurrentAccountInsteadOfSharedShop() {
        MerchantAuthService merchant = mock(MerchantAuthService.class);
        when(merchant.requireCurrentAccount()).thenReturn(new MerchantAccount().setId(31L).setShopId(1L));
        Page<CustomerServiceTicket> empty = new Page<>(1, 20);
        empty.setRecords(List.of());
        when(tickets.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(empty);
        service = new CustomerServiceServiceImpl(tickets, messages, attachments, cursors,
                mock(CustomerServiceTagMapper.class), ticketTags, mock(CustomerServiceTransferMapper.class),
                mock(CustomerServiceQuickReplyMapper.class), mock(VoucherOrderMapper.class), mock(UserVoucherMapper.class),
                mock(VoucherRefundMapper.class), mock(VoucherRedemptionMapper.class), mock(AdminUserMapper.class),
                mock(CurrentUserProvider.class), merchant, adminAuth, mock(AdminAuditService.class), attachmentService,
                mock(RedisIdWorker.class));
        ArgumentCaptor<Wrapper<CustomerServiceTicket>> wrapper = ArgumentCaptor.forClass(Wrapper.class);

        service.listForMerchant(1, 20);
        verify(tickets).selectPage(any(Page.class), wrapper.capture());

        String sql = wrapper.getValue().getCustomSqlSegment();
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("applicant_type"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("applicant_id"));
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("shop_id"));
    }

    @Test
    void beforeAndAfterMessagePagesKeepStableAscendingOrder() {
        CustomerServiceTicket ticket = ticket(CustomerServiceTicketStatus.CLAIMED).setAssigneeAdminId(5L);
        when(tickets.selectById(99L)).thenReturn(ticket);
        CustomerServiceMessage m11 = message(11L);
        CustomerServiceMessage m12 = message(12L);
        CustomerServiceMessage m13 = message(13L);
        CustomerServiceMessage m14 = message(14L);
        when(messages.selectList(org.mockito.ArgumentMatchers.<Wrapper<CustomerServiceMessage>>any()))
                .thenReturn(List.of(m12, m11))
                .thenReturn(List.of(m13, m14));

        var before = service.messagesForAdmin(99L, 13L, null, 2);
        var after = service.messagesForAdmin(99L, null, 12L, 2);

        assertEquals(List.of("11", "12"), before.items().stream().map(item -> item.id()).toList());
        assertEquals(List.of("13", "14"), after.items().stream().map(item -> item.id()).toList());
        verify(cursors).advance(anyLong(), org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.eq("ADMIN"), org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(12L));
        verify(cursors).advance(anyLong(), org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.eq("ADMIN"), org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(14L));
    }

    private CustomerServiceTicket ticket(CustomerServiceTicketStatus status) {
        return new CustomerServiceTicket().setId(99L).setTicketNo("CS99").setType("GENERAL")
                .setStatus(status.name()).setPriority("NORMAL").setApplicantType("CONSUMER").setApplicantId(7L)
                .setSlaDeadline(LocalDateTime.now().plusHours(1)).setSlaBreached(false).setHasInternalNote(false)
                .setVersion(0).setLastMessageTime(LocalDateTime.now());
    }

    private CustomerServiceMessage message(Long id) {
        return new CustomerServiceMessage().setId(id).setTicketId(99L).setSenderType("CONSUMER")
                .setSenderId(7L).setVisibility("PUBLIC").setMessageType("TEXT").setContent("message-" + id)
                .setCreateTime(LocalDateTime.now());
    }
}
