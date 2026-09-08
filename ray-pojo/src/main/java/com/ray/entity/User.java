package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
@TableName("user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 手机号码
     */
    private String phone;

    /** BCrypt 密码摘要。 */
    private String passwordHash;

    /**
     * 昵称，默认是随机字符
     */
    private String nickName;

    /** 用户头像相对路径。 */
    private String icon = "";

    /** 当前头像媒体资产 ID。 */
    private Long avatarMediaId;

    /** 最近一次用户主动修改昵称的时间。 */
    private LocalDateTime nicknameUpdatedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
