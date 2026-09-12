package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 客服消息图片附件元数据。 */
@Data
@Accessors(chain = true)
@TableName("customer_service_attachment")
public class CustomerServiceAttachment {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long ticketId;
    private Long messageId;
    private String status;
    private String uploaderType;
    private Long uploaderId;
    private String objectKey;
    private String bucketName;
    private String originalFilename;
    private String mimeType;
    private Long byteSize;
    private LocalDateTime expiresAt;
    private LocalDateTime boundAt;
    private LocalDateTime deletedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
