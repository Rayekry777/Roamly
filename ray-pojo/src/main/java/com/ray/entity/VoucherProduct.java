package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 四类团购券的商品、规则、审核与销售事实。金额统一以分保存。 */
@Data
@Accessors(chain = true)
@TableName("voucher_product")
public class VoucherProduct implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private String productType;
    private String title;
    private String subTitle;
    private Long coverMediaId;
    private String detailMediaIdsJson;
    private Long priceAmount;
    private Long marketAmount;
    private Long faceValueAmount;
    private Long minimumSpendAmount;
    private Integer totalUseCount;
    private Integer totalStock;
    private Integer availableStock;
    private Integer soldCount;
    private Integer purchaseLimit;
    private LocalDateTime saleBeginTime;
    private LocalDateTime saleEndTime;
    private String validityType;
    private LocalDateTime validBeginTime;
    private LocalDateTime validEndTime;
    private Integer validDays;
    private String usageRulesJson;
    private String excludedDatesJson;
    private Boolean reservationRequired;
    private String reservationNotice;
    private Boolean stackable;
    private Boolean refundAnytime;
    private Boolean refundExpired;
    private String reviewStatus;
    private String saleStatus;
    private String rejectionReason;
    private String submissionIdempotencyKey;
    private String submissionRequestFingerprint;
    private LocalDateTime submittedAt;
    private String reviewDecision;
    private String reviewIdempotencyKey;
    private String reviewRequestFingerprint;
    private LocalDateTime reviewedAt;
    private Long reviewerAdminId;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String shopName;
    @TableField(exist = false)
    private Long shopTypeId;
    @TableField(exist = false)
    private String shopCover;
    @TableField(exist = false)
    private String shopAddress;
    @TableField(exist = false)
    private Integer shopScore;
    @TableField(exist = false)
    private Double distance;
}
