SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `city`;
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


DROP TABLE IF EXISTS `content_section`;
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


DROP TABLE IF EXISTS `section_follow`;
CREATE TABLE `section_follow` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID，逻辑关联user.id',
  `section_id` bigint UNSIGNED NOT NULL COMMENT '分区ID，逻辑关联content_section.id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_section_follow_user_section` (`user_id`, `section_id`) USING BTREE,
  INDEX `idx_section_follow_section_time` (`section_id`, `create_time`, `id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户关注分区关系' ROW_FORMAT = Dynamic;


DROP TABLE IF EXISTS `media_asset`;
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


DROP TABLE IF EXISTS `post`;
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


DROP TABLE IF EXISTS `post_media`;
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


DROP TABLE IF EXISTS `post_like`;
CREATE TABLE `post_like` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `post_id` bigint UNSIGNED NOT NULL COMMENT '动态ID，逻辑关联post.id',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '点赞用户ID，逻辑关联user.id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_post_like_post_user` (`post_id`, `user_id`) USING BTREE,
  INDEX `idx_post_like_user_time` (`user_id`, `create_time`, `id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '动态点赞事实' ROW_FORMAT = Dynamic;


DROP TABLE IF EXISTS `post_comment`;
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


DROP TABLE IF EXISTS `post_comment_like`;
CREATE TABLE `post_comment_like` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `comment_id` bigint UNSIGNED NOT NULL COMMENT '评论ID，逻辑关联post_comment.id',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '点赞用户ID，逻辑关联user.id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_comment_like_comment_user` (`comment_id`, `user_id`) USING BTREE,
  INDEX `idx_comment_like_user_time` (`user_id`, `create_time`, `id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '动态评论点赞事实' ROW_FORMAT = Dynamic;


DROP TABLE IF EXISTS `follow`;
CREATE TABLE `follow`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `follow_user_id` bigint(20) UNSIGNED NOT NULL COMMENT '关联的用户id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_follow_user_target` (`user_id`, `follow_user_id`) USING BTREE,
  INDEX `idx_follow_target_user` (`follow_user_id`, `user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;


DROP TABLE IF EXISTS `shop`;
CREATE TABLE `shop`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商铺名称',
  `type_id` bigint(20) UNSIGNED NOT NULL COMMENT '商铺类型的id',
  `city_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '330100' COMMENT '城市编码',
  `images` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商铺图片，多个图片以\',\'隔开',
  `area` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商圈，例如陆家嘴',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '地址',
  `x` double UNSIGNED NOT NULL COMMENT '经度',
  `y` double UNSIGNED NOT NULL COMMENT '维度',
  `avg_price` bigint(10) UNSIGNED NULL DEFAULT NULL COMMENT '均价，取整数',
  `sold` int(10) UNSIGNED NOT NULL COMMENT '销量',
  `comments` int(10) UNSIGNED NOT NULL COMMENT '评论数量',
  `score` int(2) UNSIGNED NOT NULL COMMENT '评分，1~5分，乘10保存，避免小数',
  `open_hours` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '营业时间，例如 10:00-22:00',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '经营状态：0停用，1启用',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `foreign_key_type`(`type_id`) USING BTREE,
  INDEX `idx_shop_city_type_status`(`city_code`, `type_id`, `status`, `id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;


DROP TABLE IF EXISTS `shop_type`;
CREATE TABLE `shop_type`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型名称',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图标',
  `sort` int(3) UNSIGNED NULL DEFAULT NULL COMMENT '顺序',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;


DROP TABLE IF EXISTS `shop_review_media`;
DROP TABLE IF EXISTS `shop_review`;
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


DROP TABLE IF EXISTS `user`;
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


DROP TABLE IF EXISTS `user_info`;
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


DROP TABLE IF EXISTS `user_voucher`;
DROP TABLE IF EXISTS `voucher_product`;
DROP TABLE IF EXISTS `voucher_order`;
CREATE TABLE `voucher_product` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` bigint UNSIGNED NOT NULL,
  `title` varchar(120) NOT NULL,
  `sub_title` varchar(255) NULL,
  `cover` varchar(255) NULL,
  `rules` varchar(2000) NULL,
  `pay_price` bigint UNSIGNED NOT NULL,
  `original_price` bigint UNSIGNED NULL,
  `deduction_value` bigint UNSIGNED NULL,
  `sale_type` varchar(16) NOT NULL DEFAULT 'NORMAL',
  `total_stock` int UNSIGNED NOT NULL DEFAULT 0,
  `available_stock` int UNSIGNED NOT NULL DEFAULT 0,
  `sold_count` int UNSIGNED NOT NULL DEFAULT 0,
  `purchase_limit` int UNSIGNED NOT NULL DEFAULT 1,
  `sale_begin_time` timestamp NULL,
  `sale_end_time` timestamp NULL,
  `validity_type` varchar(32) NOT NULL DEFAULT 'DAYS_AFTER_PURCHASE',
  `valid_begin_time` timestamp NULL,
  `valid_end_time` timestamp NULL,
  `valid_days` int UNSIGNED NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `version` int UNSIGNED NOT NULL DEFAULT 0,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_voucher_product_shop_status` (`shop_id`,`status`,`id`),
  INDEX `idx_voucher_product_sale` (`status`,`sale_begin_time`,`sale_end_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团购商品';

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
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '订单状态，1：未支付；2：已支付；3：已核销；4：已取消；5：退款中；6：已退款',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `pay_time` timestamp NULL DEFAULT NULL COMMENT '支付时间',
  `use_time` timestamp NULL DEFAULT NULL COMMENT '核销时间',
  `refund_time` timestamp NULL DEFAULT NULL COMMENT '退款时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order_user_status_time` (`user_id`,`status`,`create_time`,`id`),
  INDEX `idx_order_product_user` (`product_id`,`user_id`,`id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;

CREATE TABLE `user_voucher` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint UNSIGNED NOT NULL,
  `order_id` bigint NOT NULL,
  `product_id` bigint UNSIGNED NOT NULL,
  `shop_id` bigint UNSIGNED NOT NULL,
  `voucher_code` varchar(64) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'UNUSED',
  `valid_begin_time` timestamp NULL,
  `expire_time` timestamp NULL,
  `use_time` timestamp NULL,
  `refund_time` timestamp NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_user_voucher_code` (`voucher_code`),
  UNIQUE INDEX `uk_user_voucher_order` (`order_id`),
  INDEX `idx_user_voucher_user_status_expire` (`user_id`,`status`,`expire_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户券实例';


SET FOREIGN_KEY_CHECKS = 1;
