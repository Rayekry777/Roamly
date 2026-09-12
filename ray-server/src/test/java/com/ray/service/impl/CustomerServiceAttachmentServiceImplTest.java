package com.ray.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ray.entity.CustomerServiceAttachment;
import com.ray.entity.CustomerServiceMessage;
import com.ray.entity.CustomerServiceTicket;
import com.ray.exception.BusinessException;
import com.ray.mapper.CustomerServiceAttachmentMapper;
import com.ray.mapper.CustomerServiceMessageMapper;
import com.ray.mapper.CustomerServiceTicketMapper;
import com.ray.service.AdminAuthService;
import com.ray.service.CurrentUserProvider;
import com.ray.service.CustomerServiceAttachmentService.Actor;
import com.ray.service.MerchantAuthService;
import com.ray.storage.ObjectStoragePort;
import com.ray.utils.generator.RedisIdWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerServiceAttachmentServiceImplTest {
    private CustomerServiceAttachmentMapper attachments;
    private CustomerServiceMessageMapper messages;
    private CustomerServiceTicketMapper tickets;
    private CurrentUserProvider users;
    private ObjectStoragePort storage;
    private CustomerServiceAttachmentServiceImpl service;

    @BeforeEach
    void setUp() {
        attachments = mock(CustomerServiceAttachmentMapper.class);
        messages = mock(CustomerServiceMessageMapper.class);
        tickets = mock(CustomerServiceTicketMapper.class);
        users = mock(CurrentUserProvider.class);
        storage = mock(ObjectStoragePort.class);
        service = new CustomerServiceAttachmentServiceImpl(attachments, messages, tickets, users,
                mock(MerchantAuthService.class), mock(AdminAuthService.class), storage,
                mock(BusinessImageInspector.class), mock(RedisIdWorker.class));
    }

    @Test
    void consumerCannotReadAttachmentFromAnotherApplicantsTicket() {
        when(users.requireUserId()).thenReturn(7L);
        when(tickets.selectById(99L)).thenReturn(new CustomerServiceTicket()
                .setId(99L).setApplicantType("CONSUMER").setApplicantId(8L));

        BusinessException notFound = assertThrows(BusinessException.class,
                () -> service.read(99L, 100L, Actor.CONSUMER));

        assertEquals("TICKET_NOT_FOUND", notFound.code());
        verify(attachments, never()).selectOne(org.mockito.ArgumentMatchers.<Wrapper<CustomerServiceAttachment>>any());
    }

    @Test
    void consumerCannotReadAttachmentBoundToInternalNote() {
        when(users.requireUserId()).thenReturn(7L);
        when(tickets.selectById(99L)).thenReturn(new CustomerServiceTicket()
                .setId(99L).setApplicantType("CONSUMER").setApplicantId(7L));
        when(attachments.selectOne(org.mockito.ArgumentMatchers.<Wrapper<CustomerServiceAttachment>>any())).thenReturn(new CustomerServiceAttachment()
                .setId(100L).setTicketId(99L).setMessageId(101L).setStatus("BOUND")
                .setUploaderType("ADMIN").setUploaderId(5L).setObjectKey("private/internal.png"));
        when(messages.selectById(101L)).thenReturn(new CustomerServiceMessage()
                .setId(101L).setTicketId(99L).setVisibility("INTERNAL"));

        BusinessException notFound = assertThrows(BusinessException.class,
                () -> service.read(99L, 100L, Actor.CONSUMER));

        assertEquals("ATTACHMENT_NOT_FOUND", notFound.code());
        verify(storage, never()).get(any());
    }
}
