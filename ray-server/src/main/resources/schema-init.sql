SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `operation_audit_log`;
DROP TABLE IF EXISTS `settlement_item`;
DROP TABLE IF EXISTS `settlement_batch`;
DROP TABLE IF EXISTS `fund_ledger_entry`;
DROP TABLE IF EXISTS `commission_rule`;
DROP TABLE IF EXISTS `voucher_redemption`;
DROP TABLE IF EXISTS `user_voucher_qr_code`;
DROP TABLE IF EXISTS `user_voucher`;
DROP TABLE IF EXISTS `voucher_refund`;
DROP TABLE IF EXISTS `payment_transaction`;
DROP TABLE IF EXISTS `voucher_order`;
DROP TABLE IF EXISTS `voucher_package_item`;
DROP TABLE IF EXISTS `voucher_product_multi_use_rule`;
DROP TABLE IF EXISTS `voucher_product_discount_rule`;
DROP TABLE IF EXISTS `voucher_product_cash_rule`;
DROP TABLE IF EXISTS `voucher_product_tag`;
DROP TABLE IF EXISTS `voucher_product_detail`;
DROP TABLE IF EXISTS `voucher_product`;
DROP TABLE IF EXISTS `merchant_staff_invitation`;
DROP TABLE IF EXISTS `business_media_asset`;
DROP TABLE IF EXISTS `merchant_application`;
DROP TABLE IF EXISTS `merchant_account`;
DROP TABLE IF EXISTS `shop_review_media`;
DROP TABLE IF EXISTS `shop_review`;
DROP TABLE IF EXISTS `user_info`;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS `shop_type`;
DROP TABLE IF EXISTS `shop`;
DROP TABLE IF EXISTS `follow`;
DROP TABLE IF EXISTS `post_comment_like`;
DROP TABLE IF EXISTS `post_comment`;
DROP TABLE IF EXISTS `post_like`;
DROP TABLE IF EXISTS `post_media`;
DROP TABLE IF EXISTS `post`;
DROP TABLE IF EXISTS `media_asset`;
DROP TABLE IF EXISTS `section_follow`;
DROP TABLE IF EXISTS `content_section`;
DROP TABLE IF EXISTS `city`;
DROP TABLE IF EXISTS `admin_user`;

CREATE TABLE `admin_user` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '管理员ID',
  `username` varchar(32) NOT NULL COMMENT '不可变登录名，统一小写',
  `password_hash` varchar(100) NOT NULL COMMENT 'BCrypt密码摘要',
  `display_name` varchar(64) NOT NULL COMMENT '管理员显示名',
  `role` varchar(32) NOT NULL COMMENT '固定角色：PLATFORM_ADMIN平台超级管理员、MERCHANT_REVIEWER商户审核员、FINANCE财务管理员',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态：ACTIVE已启用、DISABLED已停用',
  `force_password_change` tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否必须修改初始或重置密码：0否、1是',
  `last_login_time` timestamp NULL DEFAULT NULL COMMENT '最近登录时间',
  `created_by` bigint UNSIGNED NULL DEFAULT NULL COMMENT '创建管理员ID，逻辑关联admin_user.id',
  `version` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_admin_user_username` (`username`),
  INDEX `idx_admin_user_role_status` (`role`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台管理员账号';

CREATE TABLE `operation_audit_log` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审计记录ID',
  `actor_type` varchar(16) NOT NULL COMMENT '操作者类型：ADMIN管理员、MERCHANT商户、SYSTEM系统',
  `actor_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '操作者ID',
  `action` varchar(64) NOT NULL COMMENT '稳定操作编码',
  `object_type` varchar(32) NOT NULL COMMENT '操作对象类型',
  `object_id` varchar(64) NULL DEFAULT NULL COMMENT '操作对象业务ID或脱敏摘要',
  `result` varchar(16) NOT NULL COMMENT '结果：SUCCEEDED成功、FAILED失败',
  `reason` varchar(500) NULL DEFAULT NULL COMMENT '安全的结果说明，不包含密码或Token',
  `trace_id` varchar(64) NULL DEFAULT NULL COMMENT '请求追踪ID',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_audit_actor_time` (`actor_type`, `actor_id`, `create_time`, `id`),
  INDEX `idx_audit_object_time` (`object_type`, `object_id`, `create_time`, `id`),
  INDEX `idx_audit_action_time` (`action`, `create_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='敏感操作审计日志';

CREATE TABLE `city` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` varchar(16) NOT NULL COMMENT '稳定城市编码',
  `name` varchar(64) NOT NULL COMMENT '城市名称',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
  `sort` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_city_code` (`code`) USING BTREE,
  INDEX `idx_city_status_sort` (`status`, `sort`, `id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '城市字典' ROW_FORMAT = Dynamic;


CREATE TABLE `content_section` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` varchar(32) NOT NULL COMMENT '稳定分区编码',
  `name` varchar(32) NOT NULL COMMENT '分区名称',
  `description` varchar(255) NULL DEFAULT NULL COMMENT '分区说明',
  `icon` varchar(255) NULL DEFAULT NULL COMMENT '图标相对路径',
  `cover` varchar(255) NULL DEFAULT NULL COMMENT '封面相对路径',
  `allow_shop_visit` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否允许探店：0否，1是',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
  `sort` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_section_code` (`code`) USING BTREE,
  INDEX `idx_section_status_sort` (`status`, `sort`, `id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '官方内容分区' ROW_FORMAT = Dynamic;


CREATE TABLE `section_follow` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID，逻辑关联user.id',
  `section_id` bigint UNSIGNED NOT NULL COMMENT '分区ID，逻辑关联content_section.id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_section_follow_user_section` (`user_id`, `section_id`) USING BTREE,
  INDEX `idx_section_follow_section_time` (`section_id`, `create_time`, `id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户关注分区关系' ROW_FORMAT = Dynamic;


CREATE TABLE `media_asset` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `owner_user_id` bigint UNSIGNED NOT NULL COMMENT '上传用户ID，逻辑关联user.id',
  `storage_path` varchar(512) NOT NULL COMMENT '唯一相对存储路径',
  `mime_type` varchar(64) NOT NULL COMMENT '实际图片MIME类型',
  `file_size` bigint UNSIGNED NOT NULL COMMENT '文件字节数',
  `width` int UNSIGNED NOT NULL COMMENT '图片像素宽度',
  `height` int UNSIGNED NOT NULL COMMENT '图片像素高度',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0临时，1已绑定，2已删除',
  `bound_type` tinyint UNSIGNED NULL DEFAULT NULL COMMENT '绑定类型：1动态，2商户点评',
  `bound_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '绑定业务ID',
  `expire_time` timestamp NULL DEFAULT NULL COMMENT '临时资产过期时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_media_storage_path` (`storage_path`) USING BTREE,
  INDEX `idx_media_status_expire` (`status`, `expire_time`, `id`) USING BTREE,
  INDEX `idx_media_owner_status` (`owner_user_id`, `status`, `id`) USING BTREE,
  INDEX `idx_media_bound` (`bound_type`, `bound_id`, `id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '媒体资产' ROW_FORMAT = Dynamic;


CREATE TABLE `post` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '发布用户ID，逻辑关联user.id',
  `section_id` bigint UNSIGNED NOT NULL COMMENT '分区ID，逻辑关联content_section.id',
  `shop_visit` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否探店：0否，1是',
  `shop_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '商户ID，逻辑关联shop.id；普通动态为空',
  `city_code` varchar(16) NOT NULL COMMENT '城市编码，逻辑关联city.code',
  `title` varchar(120) NULL DEFAULT NULL COMMENT '可选标题',
  `content` varchar(5000) NOT NULL COMMENT '动态正文',
  `liked_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞数量冗余值',
  `comment_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '评论数量冗余值',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0正常，1隐藏，2已删除',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_post_section_status_time` (`section_id`, `status`, `create_time`, `id`) USING BTREE,
  INDEX `idx_post_user_status_time` (`user_id`, `status`, `create_time`, `id`) USING BTREE,
  INDEX `idx_post_shop_status_time` (`shop_id`, `status`, `create_time`, `id`) USING BTREE,
  INDEX `idx_post_city_status_time` (`city_code`, `status`, `create_time`, `id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '统一社区动态' ROW_FORMAT = Dynamic;


CREATE TABLE `post_media` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `post_id` bigint UNSIGNED NOT NULL COMMENT '动态ID，逻辑关联post.id',
  `media_asset_id` bigint UNSIGNED NOT NULL COMMENT '媒体资产ID，逻辑关联media_asset.id',
  `sort` tinyint UNSIGNED NOT NULL COMMENT '动态内展示顺序，从0开始',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_post_media_sort` (`post_id`, `sort`) USING BTREE,
  UNIQUE INDEX `uk_post_media_asset` (`media_asset_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '动态媒体关系' ROW_FORMAT = Dynamic;


CREATE TABLE `post_like` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `post_id` bigint UNSIGNED NOT NULL COMMENT '动态ID，逻辑关联post.id',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '点赞用户ID，逻辑关联user.id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_post_like_post_user` (`post_id`, `user_id`) USING BTREE,
  INDEX `idx_post_like_user_time` (`user_id`, `create_time`, `id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '动态点赞事实' ROW_FORMAT = Dynamic;


CREATE TABLE `post_comment` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `post_id` bigint UNSIGNED NOT NULL COMMENT '动态ID，逻辑关联post.id',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '评论用户ID，逻辑关联user.id',
  `root_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '所属根评论ID；根评论为空',
  `parent_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '直接回复目标评论ID；根评论为空',
  `reply_to_user_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '被回复用户ID；根评论为空',
  `content` varchar(1000) NULL DEFAULT NULL COMMENT '评论正文；删除后清空',
  `liked_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞数量冗余值',
  `reply_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '有效回复数量；仅根评论使用',
  `author_replied` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '动态作者是否存在有效回复；仅根评论使用',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0正常，1已删除，2审核隐藏',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_comment_post_root_time` (`post_id`, `root_id`, `status`, `create_time`, `id`) USING BTREE,
  INDEX `idx_comment_root_status_time` (`root_id`, `status`, `create_time`, `id`) USING BTREE,
  INDEX `idx_comment_parent_status` (`parent_id`, `status`, `id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '动态评论与追加回复' ROW_FORMAT = Dynamic;


CREATE TABLE `post_comment_like` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `comment_id` bigint UNSIGNED NOT NULL COMMENT '评论ID，逻辑关联post_comment.id',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '点赞用户ID，逻辑关联user.id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_comment_like_comment_user` (`comment_id`, `user_id`) USING BTREE,
  INDEX `idx_comment_like_user_time` (`user_id`, `create_time`, `id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '动态评论点赞事实' ROW_FORMAT = Dynamic;


CREATE TABLE `follow`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `follow_user_id` bigint(20) UNSIGNED NOT NULL COMMENT '关联的用户id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_follow_user_target` (`user_id`, `follow_user_id`) USING BTREE,
  INDEX `idx_follow_target_user` (`follow_user_id`, `user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;


CREATE TABLE `shop`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商铺名称',
  `type_id` bigint(20) UNSIGNED NOT NULL COMMENT '商铺类型的id',
  `city_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '330100' COMMENT '城市编码',
  `images` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '消费者摘要图片，多个地址以\',\'隔开',
  `area` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商圈，例如陆家嘴',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '地址',
  `x` double UNSIGNED NOT NULL COMMENT '经度',
  `y` double UNSIGNED NOT NULL COMMENT '维度',
  `avg_price` bigint(10) UNSIGNED NULL DEFAULT NULL COMMENT '均价，取整数',
  `sold` int(10) UNSIGNED NOT NULL COMMENT '销量',
  `comments` int(10) UNSIGNED NOT NULL COMMENT '评论数量',
  `score` int(2) UNSIGNED NOT NULL COMMENT '评分，1~5分，乘10保存，避免小数',
  `open_hours` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '营业时间，例如 10:00-22:00',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '经营状态：PENDING待激活、ACTIVE营业中、SUSPENDED已停用、CLOSED已关闭',
  `source_application_id` bigint UNSIGNED NOT NULL COMMENT '审核通过来源申请ID，逻辑关联merchant_application.id',
  `business_hours_json` json NOT NULL COMMENT '星期一至星期日结构化营业时段',
  `activated_at` timestamp NULL DEFAULT NULL COMMENT '首次激活时间',
  `suspended_at` timestamp NULL DEFAULT NULL COMMENT '最近一次停用时间',
  `suspension_reason` varchar(500) NULL DEFAULT NULL COMMENT '最近一次停用原因',
  `status_changed_by_admin_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '最近一次治理管理员ID',
  `status_command_type` varchar(16) NULL DEFAULT NULL COMMENT '最近治理命令：SUSPENSION停用、ACTIVATION恢复',
  `status_idempotency_key` varchar(128) NULL DEFAULT NULL COMMENT '最近治理命令幂等键',
  `status_request_fingerprint` char(64) NULL DEFAULT NULL COMMENT '最近治理请求SHA-256指纹',
  `version` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_shop_source_application` (`source_application_id`),
  INDEX `foreign_key_type`(`type_id`) USING BTREE,
  INDEX `idx_shop_status_city_type`(`status`, `city_code`, `type_id`, `id`) USING BTREE,
  CONSTRAINT `chk_shop_status` CHECK (`status` IN ('PENDING','ACTIVE','SUSPENDED','CLOSED')),
  CONSTRAINT `chk_shop_activation` CHECK (`status` <> 'ACTIVE' OR `activated_at` IS NOT NULL),
  CONSTRAINT `chk_shop_suspension` CHECK ((`status`='SUSPENDED' AND `suspended_at` IS NOT NULL AND `suspension_reason` IS NOT NULL) OR (`status`<>'SUSPENDED' AND `suspended_at` IS NULL AND `suspension_reason` IS NULL)),
  CONSTRAINT `chk_shop_status_command` CHECK ((`status_command_type` IS NULL AND `status_changed_by_admin_id` IS NULL AND `status_idempotency_key` IS NULL AND `status_request_fingerprint` IS NULL) OR (`status_command_type` IN ('SUSPENSION','ACTIVATION') AND `status_changed_by_admin_id` IS NOT NULL AND `status_idempotency_key` IS NOT NULL AND CHAR_LENGTH(`status_request_fingerprint`)=64))
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;

CREATE TABLE `merchant_account` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商户账号ID',
  `phone` varchar(11) NOT NULL COMMENT '中国大陆手机号',
  `nickname` varchar(64) NOT NULL COMMENT '商户端昵称',
  `avatar_url` varchar(512) NULL DEFAULT NULL COMMENT '头像地址',
  `role` varchar(16) NOT NULL DEFAULT 'OWNER' COMMENT '固定角色：OWNER店主、MANAGER店长、VERIFIER核销员',
  `status` varchar(16) NOT NULL DEFAULT 'NOT_APPLIED' COMMENT '展示状态：NOT_APPLIED未入驻、PENDING审核中、ACTIVE已激活、REJECTED审核未通过、DISABLED已停用',
  `shop_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '绑定门店ID，逻辑关联shop.id',
  `disabled_source` varchar(24) NULL DEFAULT NULL COMMENT '停用来源：SHOP_SUSPENSION门店联动、ACCOUNT_GOVERNANCE平台治理、STAFF_MANAGEMENT员工管理',
  `disabled_reason` varchar(500) NULL DEFAULT NULL COMMENT '停用原因',
  `disabled_at` timestamp NULL DEFAULT NULL COMMENT '停用时间',
  `last_login_time` timestamp NULL DEFAULT NULL COMMENT '最近登录时间',
  `version` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_merchant_account_phone` (`phone`),
  INDEX `idx_merchant_account_shop_status_role` (`shop_id`, `status`, `role`, `id`),
  CONSTRAINT `chk_merchant_account_role` CHECK (`role` IN ('OWNER','MANAGER','VERIFIER')),
  CONSTRAINT `chk_merchant_account_status` CHECK (`status` IN ('NOT_APPLIED','PENDING','ACTIVE','REJECTED','DISABLED')),
  CONSTRAINT `chk_merchant_active_shop` CHECK (`status` <> 'ACTIVE' OR `shop_id` IS NOT NULL),
  CONSTRAINT `chk_merchant_disabled_source` CHECK (`disabled_source` IS NULL OR `disabled_source` IN ('SHOP_SUSPENSION','ACCOUNT_GOVERNANCE','STAFF_MANAGEMENT')),
  CONSTRAINT `chk_merchant_disabled_fields` CHECK ((`status`='DISABLED' AND `shop_id` IS NOT NULL AND `disabled_source` IS NOT NULL AND `disabled_reason` IS NOT NULL AND `disabled_at` IS NOT NULL) OR (`status`<>'DISABLED' AND `disabled_source` IS NULL AND `disabled_reason` IS NULL AND `disabled_at` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户店主与员工账号';

CREATE TABLE `merchant_application` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '入驻申请ID',
  `merchant_account_id` bigint UNSIGNED NOT NULL COMMENT '申请店主账号ID，逻辑关联merchant_account.id',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '申请状态：DRAFT草稿、PENDING审核中、APPROVED审核通过、REJECTED审核未通过',
  `shop_name` varchar(128) NULL DEFAULT NULL COMMENT '门店名称',
  `license_number` varchar(64) NULL DEFAULT NULL COMMENT '统一社会信用代码',
  `legal_representative` varchar(64) NULL DEFAULT NULL COMMENT '法定代表人',
  `contact_name` varchar(64) NULL DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(11) NULL DEFAULT NULL COMMENT '联系人手机号',
  `shop_type_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '门店类目ID，逻辑关联shop_type.id',
  `city_code` varchar(16) NULL DEFAULT NULL COMMENT '城市编码，逻辑关联city.code',
  `district` varchar(64) NULL DEFAULT NULL COMMENT '区县',
  `address` varchar(255) NULL DEFAULT NULL COMMENT '详细地址',
  `longitude` decimal(10,6) NULL DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10,6) NULL DEFAULT NULL COMMENT '纬度',
  `business_hours_json` json NULL COMMENT '星期一至星期日结构化营业时段',
  `license_media_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '营业执照媒体ID',
  `gallery_media_ids_json` json NULL COMMENT '零至九张有序经营图片ID',
  `settlement_account_name` varchar(64) NULL DEFAULT NULL COMMENT 'Mock结算户名',
  `settlement_bank_name` varchar(64) NULL DEFAULT NULL COMMENT 'Mock结算银行',
  `settlement_account_suffix` char(4) NULL DEFAULT NULL COMMENT 'Mock结算账号后四位',
  `rejection_reason` varchar(500) NULL DEFAULT NULL COMMENT '最近驳回原因',
  `submission_idempotency_key` varchar(128) NULL DEFAULT NULL COMMENT '最近提交幂等键',
  `review_decision` varchar(16) NULL DEFAULT NULL COMMENT '审核决定：APPROVAL通过、REJECTION驳回',
  `review_idempotency_key` varchar(128) NULL DEFAULT NULL COMMENT '成功审核命令幂等键',
  `review_request_fingerprint` char(64) NULL DEFAULT NULL COMMENT '审核请求SHA-256指纹',
  `submitted_at` timestamp NULL DEFAULT NULL COMMENT '提交时间',
  `reviewed_at` timestamp NULL DEFAULT NULL COMMENT '审核时间',
  `reviewer_admin_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '审核管理员ID',
  `approved_shop_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '审核生成门店ID',
  `version` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_merchant_application_account` (`merchant_account_id`),
  UNIQUE INDEX `uk_merchant_application_approved_shop` (`approved_shop_id`),
  INDEX `idx_merchant_application_status_submitted` (`status`,`submitted_at`,`id`),
  CONSTRAINT `chk_merchant_application_status` CHECK (`status` IN ('DRAFT','PENDING','APPROVED','REJECTED')),
  CONSTRAINT `chk_merchant_application_decision` CHECK (`review_decision` IS NULL OR `review_decision` IN ('APPROVAL','REJECTION')),
  CONSTRAINT `chk_merchant_application_review_fields` CHECK ((`status`='DRAFT' AND `submission_idempotency_key` IS NULL AND `submitted_at` IS NULL AND `review_decision` IS NULL AND `review_idempotency_key` IS NULL AND `review_request_fingerprint` IS NULL AND `reviewed_at` IS NULL AND `reviewer_admin_id` IS NULL AND `approved_shop_id` IS NULL AND `rejection_reason` IS NULL) OR (`status`='PENDING' AND `submission_idempotency_key` IS NOT NULL AND `submitted_at` IS NOT NULL AND `review_decision` IS NULL AND `review_idempotency_key` IS NULL AND `review_request_fingerprint` IS NULL AND `reviewed_at` IS NULL AND `reviewer_admin_id` IS NULL AND `approved_shop_id` IS NULL AND `rejection_reason` IS NULL) OR (`status`='APPROVED' AND `submission_idempotency_key` IS NOT NULL AND `submitted_at` IS NOT NULL AND `review_decision`='APPROVAL' AND `review_idempotency_key` IS NOT NULL AND CHAR_LENGTH(`review_request_fingerprint`)=64 AND `reviewed_at` IS NOT NULL AND `reviewer_admin_id` IS NOT NULL AND `approved_shop_id` IS NOT NULL AND `rejection_reason` IS NULL) OR (`status`='REJECTED' AND `submission_idempotency_key` IS NOT NULL AND `submitted_at` IS NOT NULL AND `review_decision`='REJECTION' AND `review_idempotency_key` IS NOT NULL AND CHAR_LENGTH(`review_request_fingerprint`)=64 AND `reviewed_at` IS NOT NULL AND `reviewer_admin_id` IS NOT NULL AND `approved_shop_id` IS NULL AND `rejection_reason` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户入驻申请';

CREATE TABLE `merchant_staff_invitation` (
  `id` bigint UNSIGNED NOT NULL COMMENT '邀请ID',
  `shop_id` bigint UNSIGNED NOT NULL,
  `inviter_account_id` bigint UNSIGNED NOT NULL,
  `invite_token_digest` char(64) NOT NULL,
  `target_phone` varchar(11) NOT NULL,
  `target_role` varchar(16) NOT NULL COMMENT 'MANAGER店长、VERIFIER核销员',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待接受、ACCEPTED已接受、REVOKED已撤销、EXPIRED已过期',
  `expire_time` timestamp NOT NULL,
  `accepted_time` timestamp NULL,
  `revoked_time` timestamp NULL,
  `accepted_account_id` bigint UNSIGNED NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_staff_invitation_token` (`invite_token_digest`),
  INDEX `idx_staff_invitation_shop_status` (`shop_id`,`status`,`expire_time`),
  CONSTRAINT `chk_staff_invitation_role` CHECK (`target_role` IN ('MANAGER','VERIFIER')),
  CONSTRAINT `chk_staff_invitation_status` CHECK (`status` IN ('PENDING','ACCEPTED','REVOKED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户员工邀请';

CREATE TABLE `business_media_asset` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '经营媒体ID',
  `uploader_merchant_account_id` bigint UNSIGNED NOT NULL COMMENT '上传商户账号ID',
  `purpose` varchar(24) NOT NULL COMMENT '用途：LICENSE营业执照、GALLERY经营图片、VOUCHER_COVER券封面、VOUCHER_DETAIL券详情图',
  `status` varchar(16) NOT NULL DEFAULT 'TEMPORARY' COMMENT '状态：TEMPORARY临时、BOUND已绑定、DELETED已删除',
  `bucket_name` varchar(128) NOT NULL COMMENT '对象存储桶',
  `object_key` varchar(512) NOT NULL COMMENT '服务端生成的私有对象键',
  `original_filename` varchar(255) NOT NULL COMMENT '原始文件名',
  `mime_type` varchar(64) NOT NULL COMMENT '实际图片MIME',
  `byte_size` bigint UNSIGNED NOT NULL COMMENT '字节数',
  `width` int UNSIGNED NOT NULL COMMENT '像素宽度',
  `height` int UNSIGNED NOT NULL COMMENT '像素高度',
  `owner_type` varchar(32) NULL DEFAULT NULL COMMENT '业务归属类型：MERCHANT_APPLICATION入驻申请、VOUCHER_PRODUCT团购券',
  `owner_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '业务归属ID',
  `sort_order` tinyint UNSIGNED NULL DEFAULT NULL COMMENT '业务内排序，从0开始',
  `bound_at` timestamp NULL DEFAULT NULL COMMENT '绑定时间',
  `expires_at` timestamp NULL DEFAULT NULL COMMENT '临时媒体过期时间',
  `deleted_at` timestamp NULL DEFAULT NULL COMMENT '删除时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_business_media_object_key` (`object_key`),
  INDEX `idx_business_media_uploader_status_expiry` (`uploader_merchant_account_id`,`status`,`expires_at`,`id`),
  INDEX `idx_business_media_owner` (`owner_type`,`owner_id`,`purpose`,`sort_order`,`id`),
  CONSTRAINT `chk_business_media_purpose` CHECK (`purpose` IN ('LICENSE','GALLERY','VOUCHER_COVER','VOUCHER_DETAIL')),
  CONSTRAINT `chk_business_media_status` CHECK (`status` IN ('TEMPORARY','BOUND','DELETED')),
  CONSTRAINT `chk_business_media_owner` CHECK ((`status`='TEMPORARY' AND `owner_type` IS NULL AND `owner_id` IS NULL) OR (`status`='BOUND' AND `owner_type` IS NOT NULL AND `owner_id` IS NOT NULL) OR `status`='DELETED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='经营与团购私有媒体';


CREATE TABLE `shop_type`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型名称',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图标',
  `sort` int(3) UNSIGNED NULL DEFAULT NULL COMMENT '顺序',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;


CREATE TABLE `shop_review` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint UNSIGNED NOT NULL COMMENT '商户ID，逻辑关联shop.id',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '点评用户ID，逻辑关联user.id',
  `verified_user_voucher_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '已核销用户券ID，消费认证由服务端维护',
  `score` tinyint UNSIGNED NOT NULL COMMENT '评分，1到5分',
  `content` varchar(2000) NOT NULL COMMENT '点评正文',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0正常，1审核隐藏，2已删除',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_review_shop_user` (`shop_id`, `user_id`) USING BTREE,
  INDEX `idx_review_shop_status_time` (`shop_id`, `status`, `create_time`, `id`) USING BTREE,
  INDEX `idx_review_shop_status_score` (`shop_id`, `status`, `score`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商户独立点评' ROW_FORMAT = Dynamic;

CREATE TABLE `shop_review_media` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `review_id` bigint UNSIGNED NOT NULL COMMENT '点评ID，逻辑关联shop_review.id',
  `media_asset_id` bigint UNSIGNED NOT NULL COMMENT '媒体资产ID，逻辑关联media_asset.id',
  `sort` tinyint UNSIGNED NOT NULL COMMENT '点评内展示顺序，从0开始',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_review_media_sort` (`review_id`, `sort`) USING BTREE,
  UNIQUE INDEX `uk_review_media_asset` (`media_asset_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商户点评媒体关系' ROW_FORMAT = Dynamic;


CREATE TABLE `user`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '手机号码',
  `password` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '密码，加密存储',
  `nick_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '昵称，默认是用户id',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '人物头像',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uniqe_key_phone`(`phone`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1010 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;


CREATE TABLE `user_info`  (
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，用户id',
  `city` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '城市名称',
  `city_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '当前城市编码',
  `introduce` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '个人介绍，不要超过128个字符',
  `fans` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '粉丝数量',
  `followee` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '关注的人的数量',
  `gender` tinyint(1) UNSIGNED NULL DEFAULT 0 COMMENT '性别，0：男，1：女',
  `birthday` date NULL DEFAULT NULL COMMENT '生日',
  `credits` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '积分',
  `level` tinyint(1) UNSIGNED NULL DEFAULT 0 COMMENT '会员级别，0~9级,0代表未开通会员',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;


CREATE TABLE `voucher_product` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` bigint UNSIGNED NOT NULL,
  `product_type` varchar(16) NOT NULL COMMENT '券型：PACKAGE套餐券、CASH代金券、DISCOUNT折扣券（仅核销）、MULTI_USE次卡',
  `title` varchar(120) NULL,
  `sub_title` varchar(255) NULL,
  `cover_media_id` bigint UNSIGNED NULL,
  `detail_media_ids_json` json NOT NULL,
  `price_amount` bigint UNSIGNED NULL,
  `market_amount` bigint UNSIGNED NULL,
  `face_value_amount` bigint UNSIGNED NULL,
  `minimum_spend_amount` bigint UNSIGNED NULL,
  `total_use_count` int UNSIGNED NULL,
  `total_stock` int UNSIGNED NOT NULL DEFAULT 0,
  `available_stock` int UNSIGNED NOT NULL DEFAULT 0,
  `sold_count` int UNSIGNED NOT NULL DEFAULT 0,
  `purchase_limit` int UNSIGNED NOT NULL DEFAULT 1,
  `sale_begin_time` timestamp NULL,
  `sale_end_time` timestamp NULL,
  `validity_type` varchar(32) NULL,
  `valid_begin_time` timestamp NULL,
  `valid_end_time` timestamp NULL,
  `valid_days` int UNSIGNED NULL,
  `usage_rules_json` json NOT NULL,
  `excluded_dates_json` json NOT NULL,
  `reservation_required` tinyint(1) UNSIGNED NOT NULL DEFAULT 0,
  `reservation_notice` varchar(500) NULL,
  `stackable` tinyint(1) UNSIGNED NOT NULL DEFAULT 0,
  `refund_anytime` tinyint(1) UNSIGNED NOT NULL DEFAULT 0,
  `refund_expired` tinyint(1) UNSIGNED NOT NULL DEFAULT 0,
  `review_status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '审核状态：DRAFT草稿、PENDING审核中、APPROVED审核通过、REJECTED审核未通过',
  `sale_status` varchar(16) NULL COMMENT '销售状态：SCHEDULED待开售、ON_SALE销售中、OFF_SALE已下架、SOLD_OUT已售罄、ENDED已结束',
  `rejection_reason` varchar(500) NULL,
  `submission_idempotency_key` varchar(128) NULL,
  `submission_request_fingerprint` char(64) NULL,
  `submitted_at` timestamp NULL,
  `review_decision` varchar(16) NULL,
  `review_idempotency_key` varchar(128) NULL,
  `review_request_fingerprint` char(64) NULL,
  `reviewed_at` timestamp NULL,
  `reviewer_admin_id` bigint UNSIGNED NULL,
  `version` int UNSIGNED NOT NULL DEFAULT 0,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_voucher_product_shop_review` (`shop_id`,`review_status`,`update_time`,`id`),
  INDEX `idx_voucher_product_public` (`shop_id`,`review_status`,`sale_status`,`sale_begin_time`,`sale_end_time`,`id`),
  INDEX `idx_voucher_product_submission` (`submission_idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='四类团购券商品';

CREATE TABLE `voucher_package_item` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `product_id` bigint UNSIGNED NOT NULL COMMENT '逻辑关联voucher_product.id',
  `name` varchar(80) NOT NULL,
  `quantity` int UNSIGNED NOT NULL,
  `unit` varchar(16) NOT NULL,
  `unit_price_amount` bigint UNSIGNED NULL,
  `sort_order` int UNSIGNED NOT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_voucher_package_item_product_sort` (`product_id`,`sort_order`),
  INDEX `idx_voucher_package_item_product` (`product_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐券与次卡有序明细';

CREATE TABLE `voucher_product_detail` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `product_id` bigint UNSIGNED NOT NULL,
  `section_type` varchar(32) NOT NULL COMMENT '详情分段类型，如PACKAGE_CONTENT、USAGE_RULE、NOTICE',
  `title` varchar(80) NOT NULL,
  `content` varchar(4000) NOT NULL,
  `sort_order` int UNSIGNED NOT NULL DEFAULT 0,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_voucher_product_detail_order` (`product_id`,`sort_order`),
  KEY `idx_voucher_product_detail_product` (`product_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户编写的券商品详情分段';

CREATE TABLE `voucher_product_tag` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `product_id` bigint UNSIGNED NOT NULL,
  `text` varchar(32) NOT NULL,
  `icon_key` varchar(32) NOT NULL COMMENT '统一图标资源键，不存 SVG 内容',
  `color_token` varchar(32) NULL,
  `sort_order` int UNSIGNED NOT NULL DEFAULT 0,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_voucher_product_tag_order` (`product_id`,`sort_order`),
  KEY `idx_voucher_product_tag_product` (`product_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='券商品展示标签';

CREATE TABLE `voucher_product_cash_rule` (
  `product_id` bigint UNSIGNED NOT NULL,
  `face_value_amount` bigint UNSIGNED NULL,
  `minimum_spend_amount` bigint UNSIGNED NULL,
  `description` varchar(500) NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代金券权益规则';

CREATE TABLE `voucher_product_discount_rule` (
  `product_id` bigint UNSIGNED NOT NULL,
  `discount_text` varchar(80) NULL COMMENT '商户编写的折扣说明，不用于计价',
  `applicable_scope` varchar(255) NULL,
  `usage_period_text` varchar(120) NULL,
  `description` varchar(500) NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='折扣券展示规则';

CREATE TABLE `voucher_product_multi_use_rule` (
  `product_id` bigint UNSIGNED NOT NULL,
  `total_use_count` int UNSIGNED NULL,
  `use_unit` varchar(16) NULL,
  `description` varchar(500) NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='次卡权益规则';

CREATE TABLE `voucher_order`  (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '下单的用户id',
  `product_id` bigint(20) UNSIGNED NULL COMMENT '团购商品ID',
  `shop_id` bigint(20) UNSIGNED NULL COMMENT '商户ID快照',
  `product_title` varchar(120) NULL COMMENT '商品标题快照',
  `unit_price` bigint UNSIGNED NULL COMMENT '单价，单位分',
  `quantity` int UNSIGNED NOT NULL DEFAULT 1 COMMENT '购买数量',
  `total_amount` bigint UNSIGNED NULL COMMENT '总金额，单位分',
  `pay_amount` bigint UNSIGNED NULL COMMENT '支付金额，单位分',
  `pay_type` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '支付方式 1：余额支付；2：支付宝；3：微信',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '订单状态：PENDING_PAYMENT待支付、PAID已支付、CANCELED已取消、REFUNDING退款中、REFUNDED已退款',
  `payment_expire_time` timestamp NULL COMMENT '待支付订单过期时间',
  `idempotency_key` varchar(128) NOT NULL COMMENT '当前用户下单幂等键',
  `request_fingerprint` char(64) NOT NULL COMMENT '下单请求SHA-256指纹',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `pay_time` timestamp NULL DEFAULT NULL COMMENT '支付时间',
  `use_time` timestamp NULL DEFAULT NULL COMMENT '核销时间',
  `refund_time` timestamp NULL DEFAULT NULL COMMENT '退款时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_voucher_order_user_idempotency` (`user_id`,`idempotency_key`),
  INDEX `idx_order_user_status_time` (`user_id`,`status`,`create_time`,`id`),
  INDEX `idx_order_product_user` (`product_id`,`user_id`,`id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;

CREATE TABLE `payment_transaction` (
  `id` bigint UNSIGNED NOT NULL COMMENT '支付交易ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '消费者ID',
  `idempotency_key` varchar(128) NOT NULL COMMENT '支付幂等键',
  `provider` varchar(16) NOT NULL COMMENT '支付渠道：MOCK模拟、WECHAT微信',
  `status` varchar(32) NOT NULL COMMENT '支付状态：PENDING待支付、SUCCEEDED支付成功、FAILED支付失败、CLOSED已关闭、PARTIALLY_REFUNDED部分退款、REFUNDED已退款',
  `amount` bigint UNSIGNED NOT NULL COMMENT '支付金额，单位分',
  `failure_reason` varchar(255) NULL COMMENT '失败原因',
  `created_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_payment_transaction_order_key` (`order_id`,`idempotency_key`),
  INDEX `idx_payment_transaction_order_status` (`order_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单支付交易';

CREATE TABLE `voucher_refund` (
  `id` bigint UNSIGNED NOT NULL COMMENT '退款ID',
  `voucher_id` bigint UNSIGNED NOT NULL COMMENT '用户券ID',
  `voucher_ids` varchar(2000) NULL COMMENT '本次退款包含的用户券ID列表',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '消费者ID',
  `shop_id` bigint UNSIGNED NULL COMMENT '门店快照',
  `source` varchar(16) NOT NULL DEFAULT 'CONSUMER' COMMENT '申请来源：CONSUMER消费者、MERCHANT商户、ADMIN管理员',
  `applicant_id` bigint UNSIGNED NULL COMMENT '申请人账号ID',
  `amount` bigint UNSIGNED NOT NULL COMMENT '退款金额，单位分',
  `status` varchar(16) NOT NULL COMMENT 'REQUESTED已申请、PROCESSING处理中、SUCCEEDED退款成功、FAILED退款失败、REJECTED退款被拒',
  `reason` varchar(255) NULL,
  `description` varchar(100) NULL COMMENT '消费者退款说明',
  `reject_reason` varchar(500) NULL COMMENT '平台驳回原因',
  `failure_code` varchar(64) NULL COMMENT '渠道失败编码',
  `failure_message` varchar(500) NULL COMMENT '渠道失败说明',
  `provider_refund_no` varchar(128) NULL COMMENT '渠道退款单号',
  `approved_amount` bigint UNSIGNED NULL COMMENT '最终批准金额，单位分',
  `payment_provider` varchar(32) NULL COMMENT '原支付渠道',
  `idempotency_key` varchar(128) NOT NULL,
  `requested_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `approved_time` timestamp NULL,
  `processed_time` timestamp NULL,
  `created_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_voucher_refund_voucher_key` (`voucher_id`,`idempotency_key`),
  INDEX `idx_voucher_refund_order_status` (`order_id`,`status`),
  INDEX `idx_voucher_refund_user_time` (`user_id`,`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单券退款记录';

CREATE TABLE `user_voucher` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint UNSIGNED NOT NULL,
  `order_id` bigint NOT NULL,
  `sequence_no` int UNSIGNED NOT NULL DEFAULT 1 COMMENT '订单内券序号',
  `product_id` bigint UNSIGNED NOT NULL,
  `shop_id` bigint UNSIGNED NOT NULL,
  `voucher_code` varchar(64) NOT NULL,
  `voucher_code_hmac` char(64) NULL,
  `voucher_code_last4` char(4) NULL,
  `total_use_count` int UNSIGNED NOT NULL DEFAULT 1,
  `remaining_use_count` int UNSIGNED NOT NULL DEFAULT 1,
  `status` varchar(16) NOT NULL DEFAULT 'UNUSED',
  `valid_begin_time` timestamp NULL,
  `expire_time` timestamp NULL,
  `use_time` timestamp NULL,
  `refund_time` timestamp NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_user_voucher_code` (`voucher_code`),
  UNIQUE INDEX `uk_user_voucher_code_hmac` (`voucher_code_hmac`),
  UNIQUE INDEX `uk_user_voucher_order` (`order_id`,`sequence_no`),
  INDEX `idx_user_voucher_user_status_expire` (`user_id`,`status`,`expire_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户券实例';

CREATE TABLE `user_voucher_qr_code` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `voucher_id` bigint UNSIGNED NOT NULL COMMENT '用户券ID，一券一条固定二维码',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '持券用户ID',
  `token_key` char(32) NOT NULL COMMENT '随机定位值，不保存明文二维码 token',
  `token_version` int UNSIGNED NOT NULL DEFAULT 1 COMMENT '主动换码版本',
  `expire_time` timestamp NULL COMMENT '沿用用户券有效期',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_user_voucher_qr_voucher` (`voucher_id`),
  UNIQUE INDEX `uk_user_voucher_qr_token_key` (`token_key`),
  INDEX `idx_user_voucher_qr_user` (`user_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户券固定二维码凭证';

CREATE TABLE `voucher_redemption` (
  `id` bigint UNSIGNED NOT NULL COMMENT '核销记录ID',
  `voucher_id` bigint UNSIGNED NOT NULL,
  `shop_id` bigint UNSIGNED NOT NULL,
  `merchant_account_id` bigint UNSIGNED NOT NULL,
  `use_count` int UNSIGNED NOT NULL DEFAULT 1,
  `status` varchar(16) NOT NULL COMMENT 'SUCCEEDED已核销、REVERSED已撤销',
  `idempotency_key` varchar(128) NOT NULL,
  `reversal_reason` varchar(255) NULL,
  `reversed_by_account_id` bigint UNSIGNED NULL,
  `redeemed_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `reversed_time` timestamp NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_voucher_redemption_shop_key` (`shop_id`,`idempotency_key`),
  INDEX `idx_voucher_redemption_voucher_time` (`voucher_id`,`redeemed_time`),
  INDEX `idx_voucher_redemption_shop_time` (`shop_id`,`redeemed_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户核销与撤销记录';

CREATE TABLE `commission_rule` (
  `id` bigint UNSIGNED NOT NULL COMMENT '佣金规则ID',
  `shop_id` bigint UNSIGNED NULL COMMENT '空值表示平台默认规则',
  `rate_bps` int UNSIGNED NOT NULL COMMENT '佣金费率，基点0至10000',
  `effective_from` timestamp NOT NULL,
  `effective_to` timestamp NULL,
  `version` int UNSIGNED NOT NULL DEFAULT 0,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), INDEX `idx_commission_rule_scope_time` (`shop_id`,`effective_from`,`effective_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台与门店佣金规则';

CREATE TABLE `fund_ledger_entry` (
  `id` bigint UNSIGNED NOT NULL COMMENT '账本分录ID',
  `shop_id` bigint UNSIGNED NULL,
  `order_id` bigint NULL,
  `voucher_id` bigint UNSIGNED NULL,
  `business_event_id` varchar(128) NOT NULL,
  `entry_type` varchar(32) NOT NULL COMMENT 'PAYMENT_FROZEN支付冻结、REDEMPTION_RECOGNIZED核销确认、COMMISSION_RECOGNIZED佣金确认、REFUND_REVERSED退款冲回、REDEMPTION_REVERSED核销撤销、COMMISSION_REVERSED佣金冲回',
  `account_side` varchar(16) NOT NULL COMMENT 'CREDIT贷方、DEBIT借方',
  `amount` bigint NOT NULL COMMENT '金额，单位分，可为负数',
  `commission_rate_bps` int UNSIGNED NULL,
  `occurred_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE INDEX `uk_fund_ledger_event_side` (`business_event_id`,`entry_type`,`account_side`), INDEX `idx_fund_ledger_shop_time` (`shop_id`,`occurred_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变资金账本分录';

CREATE TABLE `settlement_batch` (
  `id` bigint UNSIGNED NOT NULL COMMENT '结算批次ID',
  `shop_id` bigint UNSIGNED NOT NULL,
  `settlement_date` date NOT NULL,
  `status` varchar(16) NOT NULL COMMENT 'PROCESSING处理中、SUCCEEDED结算成功、FAILED结算失败',
  `total_amount` bigint NOT NULL DEFAULT 0,
  `failure_reason` varchar(255) NULL,
  `version` int UNSIGNED NOT NULL DEFAULT 0,
  `processed_time` timestamp NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE INDEX `uk_settlement_batch_shop_date` (`shop_id`,`settlement_date`), INDEX `idx_settlement_batch_status_date` (`status`,`settlement_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='T+1结算批次';

CREATE TABLE `settlement_item` (
  `id` bigint UNSIGNED NOT NULL COMMENT '结算明细ID',
  `batch_id` bigint UNSIGNED NOT NULL,
  `ledger_entry_id` bigint UNSIGNED NOT NULL,
  `amount` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE INDEX `uk_settlement_item_batch_ledger` (`batch_id`,`ledger_entry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算批次明细';


SET FOREIGN_KEY_CHECKS = 1;
