package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 商户入驻草稿、提交和审核事实。 */
@Data
@Accessors(chain = true)
@TableName("merchant_application")
public class MerchantApplication implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long merchantAccountId;
    private String status;
    private String shopName;
    private String licenseNumber;
    private String legalRepresentative;
    private String contactName;
    private String contactPhone;
    private Long shopTypeId;
    private String cityCode;
    private String district;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String businessHoursJson;
    private Long licenseMediaId;
    private String galleryMediaIdsJson;
    private String settlementAccountName;
    private String settlementBankName;
    private String settlementAccountSuffix;
    private String rejectionReason;
    private String submissionIdempotencyKey;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private Long reviewerAdminId;
    private Long approvedShopId;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
