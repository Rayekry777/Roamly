package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 临时或已绑定的媒体资产实体。 */
@Data
@Accessors(chain = true)
@TableName("tb_media_asset")
public class MediaAsset implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long ownerUserId;
    private String storagePath;
    private String mimeType;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private Integer status;
    private Integer boundType;
    private Long boundId;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
