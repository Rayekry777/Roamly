package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ray.constant.AdminPermissions;
import com.ray.entity.CustomerServiceAttachment;
import com.ray.entity.CustomerServiceMessage;
import com.ray.entity.CustomerServiceTicket;
import com.ray.entity.MerchantAccount;
import com.ray.exception.BusinessException;
import com.ray.mapper.CustomerServiceAttachmentMapper;
import com.ray.mapper.CustomerServiceMessageMapper;
import com.ray.mapper.CustomerServiceTicketMapper;
import com.ray.service.AdminAuthService;
import com.ray.service.CurrentUserProvider;
import com.ray.service.CustomerServiceAttachmentService;
import com.ray.service.MerchantAuthService;
import com.ray.storage.ObjectStoragePort;
import com.ray.storage.ObjectStorageException;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.CustomerServiceAttachmentVO;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** 使用私有对象存储实现客服附件生命周期，并在每次读取时复核工单权限。 */
@Slf4j
@Service
public class CustomerServiceAttachmentServiceImpl implements CustomerServiceAttachmentService {
    private final CustomerServiceAttachmentMapper attachments;
    private final CustomerServiceMessageMapper messages;
    private final CustomerServiceTicketMapper tickets;
    private final CurrentUserProvider userProvider;
    private final MerchantAuthService merchantAuth;
    private final AdminAuthService adminAuth;
    private final ObjectStoragePort storage;
    private final BusinessImageInspector imageInspector;
    private final RedisIdWorker idWorker;

    /** 注入客服数据、三端身份上下文和私有对象存储。 */
    public CustomerServiceAttachmentServiceImpl(CustomerServiceAttachmentMapper attachments,
            CustomerServiceMessageMapper messages, CustomerServiceTicketMapper tickets,
            CurrentUserProvider userProvider, MerchantAuthService merchantAuth, AdminAuthService adminAuth,
            ObjectStoragePort storage, BusinessImageInspector imageInspector, RedisIdWorker idWorker) {
        this.attachments = attachments;
        this.messages = messages;
        this.tickets = tickets;
        this.userProvider = userProvider;
        this.merchantAuth = merchantAuth;
        this.adminAuth = adminAuth;
        this.storage = storage;
        this.imageInspector = imageInspector;
        this.idWorker = idWorker;
    }

    /** 上传仅归属于当前工单和上传者的临时图片，24 小时后可清理。 */
    @Override
    @Transactional
    public CustomerServiceAttachmentVO upload(Long ticketId, MultipartFile file, Actor actor) {
        CustomerServiceTicket ticket = requireAccessibleTicket(ticketId, actor);
        ActorIdentity identity = identity(actor);
        BusinessImageInspector.ImageMetadata image = imageInspector.inspect(file);
        long id = idWorker.nextId("customer-service-attachment");
        String key = "customer-service/" + ticket.getId() + "/" + UUID.randomUUID() + "." + image.extension();
        try {
            storage.put(key, image.mimeType(), image.content());
        } catch (ObjectStorageException exception) {
            throw unavailable(exception);
        }
        registerRollbackDelete(key);
        CustomerServiceAttachment attachment = new CustomerServiceAttachment()
                .setId(id).setTicketId(ticket.getId()).setStatus("TEMPORARY")
                .setUploaderType(actor.name()).setUploaderId(identity.id())
                .setObjectKey(key).setBucketName(storage.bucketName())
                .setOriginalFilename(image.originalFilename()).setMimeType(image.mimeType())
                .setByteSize((long) image.content().length).setExpiresAt(LocalDateTime.now().plusHours(24));
        attachments.insert(attachment);
        log.info("[客服附件] 已上传临时附件，ticketId={}，attachmentId={}，actor={}", ticketId, id, actor);
        return view(attachment, actor);
    }

    /** 读取前校验附件确属路径中的工单，并阻止外部申请人读取内部备注附件。 */
    @Override
    public AttachmentContent read(Long ticketId, Long attachmentId, Actor actor) {
        requireAccessibleTicket(ticketId, actor);
        CustomerServiceAttachment attachment = requireAttachment(ticketId, attachmentId);
        if ("DELETED".equals(attachment.getStatus())) throw notFound();
        ActorIdentity identity = identity(actor);
        if ("TEMPORARY".equals(attachment.getStatus())
                && (!actor.name().equals(attachment.getUploaderType()) || !identity.id().equals(attachment.getUploaderId()))) {
            throw notFound();
        }
        if (attachment.getMessageId() != null && actor != Actor.ADMIN) {
            CustomerServiceMessage message = messages.selectById(attachment.getMessageId());
            if (message == null || !"PUBLIC".equals(message.getVisibility())) throw notFound();
        }
        ObjectStoragePort.StoredObject object;
        try {
            object = storage.get(attachment.getObjectKey());
        } catch (ObjectStorageException exception) {
            throw unavailable(exception);
        }
        return new AttachmentContent(attachment.getOriginalFilename(), attachment.getMimeType(), object.content());
    }

    /** 只允许上传者删除尚未绑定的临时附件。 */
    @Override
    @Transactional
    public void deleteTemporary(Long ticketId, Long attachmentId, Actor actor) {
        requireAccessibleTicket(ticketId, actor);
        ActorIdentity identity = identity(actor);
        CustomerServiceAttachment attachment = requireAttachment(ticketId, attachmentId);
        int changed = attachments.update(null, new UpdateWrapper<CustomerServiceAttachment>()
                .eq("id", attachmentId).eq("ticket_id", ticketId).eq("status", "TEMPORARY")
                .eq("uploader_type", actor.name()).eq("uploader_id", identity.id())
                .set("status", "DELETED").set("deleted_at", LocalDateTime.now()));
        if (changed != 1) throw BusinessException.conflict("ATTACHMENT_NOT_TEMPORARY", "附件已绑定或无权删除");
        afterCommitDelete(attachment.getObjectKey());
    }

    /** 使用带工单、上传者和临时状态条件的更新绑定附件，避免跨工单抢绑。 */
    @Override
    public void bindToMessage(Long ticketId, Long messageId, List<String> attachmentIds, Actor actor) {
        if (attachmentIds == null || attachmentIds.isEmpty()) return;
        if (attachmentIds.size() > 9) throw BusinessException.badRequest("TOO_MANY_ATTACHMENTS", "每条消息最多绑定9个附件");
        ActorIdentity identity = identity(actor);
        Set<Long> unique = new HashSet<>();
        for (String value : attachmentIds) unique.add(IdUtils.parse(value, "attachmentIds"));
        if (unique.size() != attachmentIds.size()) throw BusinessException.badRequest("DUPLICATE_ATTACHMENT", "附件不能重复绑定");
        for (Long attachmentId : unique) {
            int changed = attachments.update(null, new UpdateWrapper<CustomerServiceAttachment>()
                    .eq("id", attachmentId).eq("ticket_id", ticketId).eq("status", "TEMPORARY")
                    .eq("uploader_type", actor.name()).eq("uploader_id", identity.id())
                    .gt("expires_at", LocalDateTime.now())
                    .set("message_id", messageId).set("status", "BOUND")
                    .set("bound_at", LocalDateTime.now()).set("expires_at", null));
            if (changed != 1) throw BusinessException.conflict("ATTACHMENT_BIND_CONFLICT", "附件不存在、已过期或已绑定");
        }
    }

    /** 定时把过期临时附件标记删除，并在事务提交后清理私有对象。 */
    @Override
    @Scheduled(fixedDelayString = "${ray.customer-service.attachment-cleanup-ms:3600000}")
    @Transactional
    public int cleanupExpiredTemporary() {
        List<CustomerServiceAttachment> expired = attachments.selectList(new QueryWrapper<CustomerServiceAttachment>()
                .eq("status", "TEMPORARY").le("expires_at", LocalDateTime.now()).last("LIMIT 100"));
        int count = 0;
        for (CustomerServiceAttachment attachment : expired) {
            int changed = attachments.update(null, new UpdateWrapper<CustomerServiceAttachment>()
                    .eq("id", attachment.getId()).eq("status", "TEMPORARY")
                    .set("status", "DELETED").set("deleted_at", LocalDateTime.now()));
            if (changed == 1) {
                afterCommitDelete(attachment.getObjectKey());
                count++;
            }
        }
        if (count > 0) log.info("[客服附件] 已清理过期临时附件，count={}", count);
        return count;
    }

    private CustomerServiceTicket requireAccessibleTicket(Long ticketId, Actor actor) {
        CustomerServiceTicket ticket = tickets.selectById(ticketId);
        if (ticket == null) throw BusinessException.notFound("TICKET_NOT_FOUND", "客服工单不存在");
        ActorIdentity identity = identity(actor);
        if (actor == Actor.ADMIN) {
            adminAuth.requirePermission(AdminPermissions.CUSTOMER_SERVICE_READ);
        } else if (!actor.name().equals(ticket.getApplicantType()) || !identity.id().equals(ticket.getApplicantId())) {
            throw BusinessException.notFound("TICKET_NOT_FOUND", "客服工单不存在");
        }
        return ticket;
    }

    private ActorIdentity identity(Actor actor) {
        return switch (actor) {
            case CONSUMER -> new ActorIdentity(userProvider.requireUserId());
            case MERCHANT -> {
                MerchantAccount account = merchantAuth.requireCurrentAccount();
                yield new ActorIdentity(account.getId());
            }
            case ADMIN -> new ActorIdentity(adminAuth.currentAdminId());
        };
    }

    private CustomerServiceAttachment requireAttachment(Long ticketId, Long attachmentId) {
        CustomerServiceAttachment attachment = attachments.selectOne(new QueryWrapper<CustomerServiceAttachment>()
                .eq("id", attachmentId).eq("ticket_id", ticketId).last("LIMIT 1"));
        if (attachment == null) throw notFound();
        return attachment;
    }

    private CustomerServiceAttachmentVO view(CustomerServiceAttachment a, Actor actor) {
        String prefix = switch (actor) {
            case CONSUMER -> "/v1/users/me/customer-service/tickets/";
            case MERCHANT -> "/v1/merchant/customer-service/tickets/";
            case ADMIN -> "/v1/admin/customer-service/tickets/";
        };
        return new CustomerServiceAttachmentVO(IdUtils.format(a.getId()), IdUtils.format(a.getTicketId()),
                IdUtils.format(a.getMessageId()), a.getStatus(), a.getOriginalFilename(), a.getMimeType(),
                a.getByteSize(), prefix + a.getTicketId() + "/attachments/" + a.getId() + "/content");
    }

    private BusinessException notFound() {
        return BusinessException.notFound("ATTACHMENT_NOT_FOUND", "客服附件不存在");
    }

    private BusinessException unavailable(ObjectStorageException cause) {
        return new BusinessException(503, "OBJECT_STORAGE_UNAVAILABLE", "对象存储暂不可用", cause);
    }

    private void registerRollbackDelete(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) deleteQuietly(objectKey);
            }
        });
    }

    private void afterCommitDelete(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(objectKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { deleteQuietly(objectKey); }
        });
    }

    private void deleteQuietly(String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException exception) {
            log.warn("[客服附件] 私有对象删除失败，将由清理任务重试，objectKey={}", objectKey, exception);
        }
    }

    private record ActorIdentity(Long id) {}
}
