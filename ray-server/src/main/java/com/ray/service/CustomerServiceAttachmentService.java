package com.ray.service;

import com.ray.vo.CustomerServiceAttachmentVO;
import org.springframework.web.multipart.MultipartFile;

/** 客服附件的临时上传、工单内绑定、私有读取和清理能力。 */
public interface CustomerServiceAttachmentService {
    /** 为指定角色可访问的工单上传临时图片。 */
    CustomerServiceAttachmentVO upload(Long ticketId, MultipartFile file, Actor actor);
    /** 读取工单内私有附件并再次校验工单归属和消息可见性。 */
    AttachmentContent read(Long ticketId, Long attachmentId, Actor actor);
    /** 删除本人上传且尚未绑定消息的临时附件。 */
    void deleteTemporary(Long ticketId, Long attachmentId, Actor actor);
    /** 将一组本角色上传的临时附件绑定到新消息。 */
    void bindToMessage(Long ticketId, Long messageId, java.util.List<String> attachmentIds, Actor actor);
    /** 清理已经超过临时上传有效期的未绑定附件。 */
    int cleanupExpiredTemporary();

    enum Actor { CONSUMER, MERCHANT, ADMIN }
    record AttachmentContent(String filename, String mimeType, byte[] content) {
        public AttachmentContent { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }
}
