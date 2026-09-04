package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("shop")
public class Shop implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商铺名称
     */
    private String name;

    /**
     * 商铺类型的id
     */
    private Long typeId;

    /**
     * 城市编码
     */
    private String cityCode;

    /**
     * 商铺图片，多个图片以','隔开
     */
    private String images;

    /**
     * 商圈，例如陆家嘴
     */
    private String area;

    /**
     * 地址
     */
    private String address;

    /**
     * 经度
     */
    private Double x;

    /**
     * 维度
     */
    private Double y;

    /**
     * 均价，取整数
     */
    private Long avgPrice;

    /**
     * 销量
     */
    private Integer sold;

    /**
     * 评论数量
     */
    private Integer comments;

    /**
     * 评分，1~5分，乘10保存，避免小数
     */
    private Integer score;

    /**
     * 营业时间，例如 10:00-22:00
     */
    private String openHours;

    /**
     * 经营状态：PENDING 待激活、ACTIVE 营业中、SUSPENDED 已停用、CLOSED 已关闭
     */
    private String status;

    /** 审核通过来源申请 ID。 */
    private Long sourceApplicationId;

    /** 七日结构化营业时间。 */
    private String businessHoursJson;

    /** 首次激活时间。 */
    private LocalDateTime activatedAt;

    /** 最近一次停用时间。 */
    private LocalDateTime suspendedAt;

    /** 最近一次停用原因。 */
    private String suspensionReason;

    /** 最近一次治理管理员 ID。 */
    private Long statusChangedByAdminId;

    /** 最近一次治理命令。 */
    private String statusCommandType;

    /** 最近一次治理命令幂等键。 */
    private String statusIdempotencyKey;

    /** 最近一次治理命令请求指纹。 */
    private String statusRequestFingerprint;

    /** 乐观锁版本。 */
    private Integer version;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private Double distance;
}
