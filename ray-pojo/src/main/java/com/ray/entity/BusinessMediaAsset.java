package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 商户上传的私有经营媒体及业务绑定事实。 */
@Data
@Accessors(chain = true)
@TableName("business_media_asset")
public class BusinessMediaAsset implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long uploaderMerchantAccountId;
    private String purpose;
    private String status;
    private String bucketName;
    private String objectKey;
    private String originalFilename;
    private String mimeType;
    private Long byteSize;
    private Integer width;
    private Integer height;
    private String ownerType;
    private Long ownerId;
    private Integer sortOrder;
    private LocalDateTime boundAt;
    private LocalDateTime expiresAt;
    private LocalDateTime deletedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
