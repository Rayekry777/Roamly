# 数据库结构文档

当前结构来源：`ray-server/src/main/resources/schema-init.sql`；开发数据来源：`ray-server/src/main/resources/seed-dev.sql`。两者由 `ray-server/src/main/resources/application-dev.yml` 按“先结构、后数据”的顺序初始化。

结构版本：开发初始化快照（截至 2026-09-02，阶段 1）
业务表数量：14 张
数据库：MySQL / InnoDB / utf8mb4

数据库不做版本管理：结构直接维护在 `schema-init.sql`，开发数据直接维护在 `seed-dev.sql`。2026-09-02 阶段 1 新增城市、官方分区、分区关注和媒体资产快照，并扩展商户与用户资料的城市字段；当前运行数据库是否已重建需单独确认。

## 表目录索引

| 序号 | 表 | 用途 | 字段明细 |
|---:|---|---|---|
| 1 | `tb_user` | 用户账号 | [查看字段](#tb_user-用户账号) |
| 2 | `tb_user_info` | 用户扩展资料 | [查看字段](#tb_user_info-用户扩展资料) |
| 3 | `tb_shop_type` | 商户分类 | [查看字段](#tb_shop_type-商户分类) |
| 4 | `tb_shop` | 商户信息与地理坐标 | [查看字段](#tb_shop-商户信息与地理坐标) |
| 5 | `tb_blog` | 探店笔记 | [查看字段](#tb_blog-探店笔记) |
| 6 | `tb_blog_comments` | 笔记评论与回复 | [查看字段](#tb_blog_comments-笔记评论与回复) |
| 7 | `tb_follow` | 用户关注关系 | [查看字段](#tb_follow-用户关注关系) |
| 8 | `tb_voucher` | 商户优惠券 | [查看字段](#tb_voucher-商户优惠券) |
| 9 | `tb_seckill_voucher` | 秒杀优惠券 | [查看字段](#tb_seckill_voucher-秒杀优惠券) |
| 10 | `tb_voucher_order` | 优惠券订单 | [查看字段](#tb_voucher_order-优惠券订单) |
| 11 | `tb_city` | 城市字典 | [查看字段](#tb_city-城市字典) |
| 12 | `tb_content_section` | 官方内容分区 | [查看字段](#tb_content_section-官方内容分区) |
| 13 | `tb_section_follow` | 用户关注分区 | [查看字段](#tb_section_follow-用户关注分区) |
| 14 | `tb_media_asset` | 临时和已绑定媒体 | [查看字段](#tb_media_asset-媒体资产) |

## 表索引总览

本节置于字段明细之前，索引名称、类型和列顺序均以 `schema-init.sql` 的实际声明为准。未列出的字段没有单独索引；表之间当前没有数据库级外键，关联完整性由应用维护。

| 表 | 索引名 | 类型 | 索引列（顺序） | 用途 |
|---|---|---|---|---|
| `tb_blog` | `PRIMARY` | 主键（BTREE） | `id` | 笔记唯一标识 |
| `tb_blog_comments` | `PRIMARY` | 主键（BTREE） | `id` | 评论唯一标识 |
| `tb_city` | `PRIMARY` | 主键（BTREE） | `id` | 城市唯一标识 |
| `tb_city` | `uk_city_code` | 唯一索引（BTREE） | `code` | 保证城市编码唯一 |
| `tb_city` | `idx_city_status_sort` | 普通索引（BTREE） | `status, sort, id` | 启用城市稳定排序 |
| `tb_content_section` | `PRIMARY` | 主键（BTREE） | `id` | 分区唯一标识 |
| `tb_content_section` | `uk_section_code` | 唯一索引（BTREE） | `code` | 保证稳定分区编码唯一 |
| `tb_content_section` | `idx_section_status_sort` | 普通索引（BTREE） | `status, sort, id` | 启用分区稳定排序 |
| `tb_follow` | `PRIMARY` | 主键（BTREE） | `id` | 关注记录唯一标识 |
| `tb_media_asset` | `PRIMARY` | 主键（BTREE） | `id` | 媒体资产唯一标识 |
| `tb_media_asset` | `uk_media_storage_path` | 唯一索引（BTREE） | `storage_path` | 保证存储路径唯一 |
| `tb_media_asset` | `idx_media_status_expire` | 普通索引（BTREE） | `status, expire_time, id` | 扫描过期临时媒体 |
| `tb_media_asset` | `idx_media_owner_status` | 普通索引（BTREE） | `owner_user_id, status, id` | 按用户和状态查询媒体 |
| `tb_media_asset` | `idx_media_bound` | 普通索引（BTREE） | `bound_type, bound_id, id` | 查询业务绑定媒体 |
| `tb_section_follow` | `PRIMARY` | 主键（BTREE） | `id` | 分区关注记录唯一标识 |
| `tb_section_follow` | `uk_section_follow_user_section` | 唯一索引（BTREE） | `user_id, section_id` | 防止重复关注分区 |
| `tb_section_follow` | `idx_section_follow_section_time` | 普通索引（BTREE） | `section_id, create_time, id` | 分区关注者时间序查询 |
| `tb_seckill_voucher` | `PRIMARY` | 主键（BTREE） | `voucher_id` | 秒杀券与优惠券一对一记录标识 |
| `tb_shop` | `PRIMARY` | 主键（BTREE） | `id` | 商户唯一标识 |
| `tb_shop` | `foreign_key_type` | 普通索引（BTREE） | `type_id` | 按商户分类查询；名称沿用脚本中的历史命名，不代表实际外键 |
| `tb_shop` | `idx_shop_city_type_status` | 普通索引（BTREE） | `city_code, type_id, status, id` | 按城市、分类和经营状态查询 |
| `tb_shop_type` | `PRIMARY` | 主键（BTREE） | `id` | 分类唯一标识 |
| `tb_user` | `PRIMARY` | 主键（BTREE） | `id` | 用户唯一标识 |
| `tb_user` | `uniqe_key_phone` | 唯一索引（BTREE） | `phone` | 保证手机号唯一；名称沿用脚本中的拼写 |
| `tb_user_info` | `PRIMARY` | 主键（BTREE） | `user_id` | 用户扩展资料唯一标识 |
| `tb_voucher` | `PRIMARY` | 主键（BTREE） | `id` | 优惠券唯一标识 |
| `tb_voucher_order` | `PRIMARY` | 主键（BTREE） | `id` | 订单唯一标识 |

## 表清单与隔离范围

| 表 | 用途 | 隔离范围 | 主键 | 关系与约束摘要 |
|---|---|---|---|---|
| `tb_user` | 用户账号 | 平台级 | `id` | `phone` 唯一；无实际外键 |
| `tb_user_info` | 用户扩展资料 | 平台级（按用户） | `user_id` | 与 `tb_user` 逻辑一对一 |
| `tb_city` | 城市字典 | 平台级 | `id` | `code` 唯一；无实际外键 |
| `tb_content_section` | 官方内容分区 | 平台级 | `id` | `code` 唯一；`ROAM_DAILY` 为系统分区 |
| `tb_section_follow` | 用户关注分区 | 平台级（按用户） | `id` | 用户与分区组合唯一；均为逻辑关联 |
| `tb_media_asset` | 临时和已绑定媒体 | 平台级（按用户） | `id` | 路径唯一；所有者和绑定业务为逻辑关联 |
| `tb_shop_type` | 商户分类 | 平台级 | `id` | 无实际外键 |
| `tb_shop` | 商户信息与地理坐标 | 平台级 | `id` | `type_id` 逻辑关联 `tb_shop_type.id`；有普通索引 |
| `tb_blog` | 探店笔记 | 平台级（按用户） | `id` | `shop_id`、`user_id` 分别逻辑关联商户和用户 |
| `tb_blog_comments` | 笔记评论/回复 | 平台级（按用户） | `id` | `blog_id`、`user_id`、`parent_id`、`answer_id` 均为逻辑关联 |
| `tb_follow` | 用户关注关系 | 平台级（按用户） | `id` | `user_id`、`follow_user_id` 为逻辑关联 |
| `tb_voucher` | 商户优惠券 | 平台级 | `id` | `shop_id` 逻辑关联 `tb_shop.id` |
| `tb_seckill_voucher` | 秒杀券库存与时间 | 平台级 | `voucher_id` | `voucher_id` 逻辑关联 `tb_voucher.id`，业务上一对一 |
| `tb_voucher_order` | 优惠券订单 | 平台级（按用户） | `id` | `user_id`、`voucher_id` 为逻辑关联 |

## 表字段明细

### `tb_user` 用户账号

| 字段 | 类型 | 可空 | 默认值 | 约束/说明 |
|---|---|---|---|---|
| `id` | `bigint(20) UNSIGNED` | 否 | 自增 | 主键；用户 ID |
| `phone` | `varchar(11)` | 否 | 无 | 手机号码；唯一索引 `uniqe_key_phone` |
| `password` | `varchar(128)` | 是 | `''` | 加密存储的密码 |
| `nick_name` | `varchar(32)` | 是 | `''` | 昵称，默认可使用用户 ID |
| `icon` | `varchar(255)` | 是 | `''` | 人物头像路径 |
| `create_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `update_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP`，更新时自动刷新 | 更新时间 |

### `tb_user_info` 用户扩展资料

| 字段 | 类型 | 可空 | 默认值 | 约束/说明 |
|---|---|---|---|---|
| `user_id` | `bigint(20) UNSIGNED` | 否 | 无 | 主键；逻辑关联 `tb_user.id` |
| `city` | `varchar(64)` | 是 | `''` | 城市名称 |
| `city_code` | `varchar(16)` | 是 | `NULL` | 当前城市编码；逻辑关联 `tb_city.code` |
| `introduce` | `varchar(128)` | 是 | `NULL` | 个人介绍，业务上不超过 128 个字符 |
| `fans` | `int(8) UNSIGNED` | 是 | `0` | 粉丝数量 |
| `followee` | `int(8) UNSIGNED` | 是 | `0` | 关注人数 |
| `gender` | `tinyint(1) UNSIGNED` | 是 | `0` | 性别：0 男，1 女 |
| `birthday` | `date` | 是 | `NULL` | 生日 |
| `credits` | `int(8) UNSIGNED` | 是 | `0` | 积分 |
| `level` | `tinyint(1) UNSIGNED` | 是 | `0` | 会员级别 0~9，0 表示未开通会员 |
| `create_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `update_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP`，更新时自动刷新 | 更新时间 |

### `tb_shop_type` 商户分类

| 字段 | 类型 | 可空 | 默认值 | 约束/说明 |
|---|---|---|---|---|
| `id` | `bigint(20) UNSIGNED` | 否 | 自增 | 主键；分类 ID |
| `name` | `varchar(32)` | 是 | `NULL` | 类型名称 |
| `icon` | `varchar(255)` | 是 | `NULL` | 分类图标路径 |
| `sort` | `int(3) UNSIGNED` | 是 | `NULL` | 展示顺序 |
| `create_time` | `timestamp` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |
| `update_time` | `timestamp` | 是 | `CURRENT_TIMESTAMP`，更新时自动刷新 | 更新时间 |

### `tb_shop` 商户信息与地理坐标

| 字段 | 类型 | 可空 | 默认值 | 约束/说明 |
|---|---|---|---|---|
| `id` | `bigint(20) UNSIGNED` | 否 | 自增 | 主键；商户 ID |
| `name` | `varchar(128)` | 否 | 无 | 商铺名称 |
| `type_id` | `bigint(20) UNSIGNED` | 否 | 无 | 商铺分类 ID；逻辑关联 `tb_shop_type.id`；索引 `foreign_key_type` |
| `city_code` | `varchar(16)` | 否 | `'330100'` | 城市编码；逻辑关联 `tb_city.code` |
| `images` | `varchar(1024)` | 否 | 无 | 商铺图片，多张以逗号分隔 |
| `area` | `varchar(128)` | 是 | `NULL` | 商圈，例如“陆家嘴” |
| `address` | `varchar(255)` | 否 | 无 | 商铺地址 |
| `x` | `double UNSIGNED` | 否 | 无 | 经度 |
| `y` | `double UNSIGNED` | 否 | 无 | 纬度（脚本注释原文为“维度”） |
| `avg_price` | `bigint(10) UNSIGNED` | 是 | `NULL` | 人均价格，取整数 |
| `sold` | `int(10) UNSIGNED` | 否 | 无 | 销量 |
| `comments` | `int(10) UNSIGNED` | 否 | 无 | 评论数量 |
| `score` | `int(2) UNSIGNED` | 否 | 无 | 评分 1~5 分，乘 10 后保存 |
| `open_hours` | `varchar(32)` | 是 | `NULL` | 营业时间，例如 `10:00-22:00` |
| `status` | `tinyint UNSIGNED` | 否 | `1` | 经营状态：0 停用，1 启用 |
| `create_time` | `timestamp` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |
| `update_time` | `timestamp` | 是 | `CURRENT_TIMESTAMP`，更新时自动刷新 | 更新时间 |

### `tb_blog` 探店笔记

| 字段 | 类型 | 可空 | 默认值 | 约束/说明 |
|---|---|---|---|---|
| `id` | `bigint(20) UNSIGNED` | 否 | 自增 | 主键；笔记 ID |
| `shop_id` | `bigint(20)` | 否 | 无 | 商户 ID；逻辑关联 `tb_shop.id` |
| `user_id` | `bigint(20) UNSIGNED` | 否 | 无 | 发布用户 ID；逻辑关联 `tb_user.id` |
| `title` | `varchar(255)` | 否 | 无 | 笔记标题 |
| `images` | `varchar(2048)` | 否 | 无 | 探店照片，最多 9 张，逗号分隔 |
| `content` | `varchar(2048)` | 否 | 无 | 探店文字描述 |
| `liked` | `int(8) UNSIGNED` | 是 | `0` | 点赞数量（脚本默认值为 `00000000`，等价于 0） |
| `comments` | `int(8) UNSIGNED` | 是 | `NULL` | 评论数量 |
| `create_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `update_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP`，更新时自动刷新 | 更新时间 |

### `tb_blog_comments` 笔记评论与回复

| 字段 | 类型 | 可空 | 默认值 | 约束/说明 |
|---|---|---|---|---|
| `id` | `bigint(20) UNSIGNED` | 否 | 自增 | 主键；评论 ID |
| `user_id` | `bigint(20) UNSIGNED` | 否 | 无 | 评论用户 ID；逻辑关联 `tb_user.id` |
| `blog_id` | `bigint(20) UNSIGNED` | 否 | 无 | 笔记 ID；逻辑关联 `tb_blog.id` |
| `parent_id` | `bigint(20) UNSIGNED` | 否 | 无 | 所属一级评论 ID；一级评论使用 0 |
| `answer_id` | `bigint(20) UNSIGNED` | 否 | 无 | 被回复的评论 ID |
| `content` | `varchar(255)` | 否 | 无 | 回复内容 |
| `liked` | `int(8) UNSIGNED` | 是 | `NULL` | 点赞数 |
| `status` | `tinyint(1) UNSIGNED` | 是 | `NULL` | 状态：0 正常，1 被举报，2 禁止查看 |
| `create_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `update_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP`，更新时自动刷新 | 更新时间 |

### `tb_follow` 用户关注关系

| 字段 | 类型 | 可空 | 默认值 | 约束/说明 |
|---|---|---|---|---|
| `id` | `bigint(20)` | 否 | 自增 | 主键；关注记录 ID |
| `user_id` | `bigint(20) UNSIGNED` | 否 | 无 | 发起关注的用户 ID；逻辑关联 `tb_user.id` |
| `follow_user_id` | `bigint(20) UNSIGNED` | 否 | 无 | 被关注的用户 ID；逻辑关联 `tb_user.id` |
| `create_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP` | 关注创建时间 |

### `tb_voucher` 商户优惠券

| 字段 | 类型 | 可空 | 默认值 | 约束/说明 |
|---|---|---|---|---|
| `id` | `bigint(20) UNSIGNED` | 否 | 自增 | 主键；优惠券 ID |
| `shop_id` | `bigint(20) UNSIGNED` | 是 | `NULL` | 商铺 ID；逻辑关联 `tb_shop.id` |
| `title` | `varchar(255)` | 否 | 无 | 代金券标题 |
| `sub_title` | `varchar(255)` | 是 | `NULL` | 副标题 |
| `rules` | `varchar(1024)` | 是 | `NULL` | 使用规则 |
| `pay_value` | `bigint(10) UNSIGNED` | 否 | 无 | 支付金额，单位为分，如 200 表示 2 元 |
| `actual_value` | `bigint(10)` | 否 | 无 | 抵扣金额，单位为分，如 200 表示 2 元 |
| `type` | `tinyint(1) UNSIGNED` | 否 | `0` | 类型：0 普通券，1 秒杀券 |
| `status` | `tinyint(1) UNSIGNED` | 否 | `1` | 状态：1 上架，2 下架，3 过期 |
| `create_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `update_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP`，更新时自动刷新 | 更新时间 |

### `tb_seckill_voucher` 秒杀优惠券

| 字段 | 类型 | 可空 | 默认值 | 约束/说明 |
|---|---|---|---|---|
| `voucher_id` | `bigint(20) UNSIGNED` | 否 | 无 | 主键；逻辑关联 `tb_voucher.id`，业务上一对一 |
| `stock` | `int(8)` | 否 | 无 | 秒杀库存 |
| `create_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `begin_time` | `timestamp` | 是 | `NULL` | 生效时间；为空表示未配置生效时间 |
| `end_time` | `timestamp` | 是 | `NULL` | 失效时间；为空表示未配置失效时间 |
| `update_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP`，更新时自动刷新 | 更新时间 |

说明：`begin_time` 与 `end_time` 使用 `timestamp NULL DEFAULT NULL`，以兼容 MySQL 严格模式；业务层应明确处理空时间。

### `tb_voucher_order` 优惠券订单

| 字段 | 类型 | 可空 | 默认值 | 约束/说明 |
|---|---|---|---|---|
| `id` | `bigint(20)` | 否 | 无 | 主键；订单 ID，由应用生成 |
| `user_id` | `bigint(20) UNSIGNED` | 否 | 无 | 下单用户 ID；逻辑关联 `tb_user.id` |
| `voucher_id` | `bigint(20) UNSIGNED` | 否 | 无 | 购买的代金券 ID；逻辑关联 `tb_voucher.id` |
| `pay_type` | `tinyint(1) UNSIGNED` | 否 | `1` | 支付方式：1 余额，2 支付宝，3 微信 |
| `status` | `tinyint(1) UNSIGNED` | 否 | `1` | 订单状态：1 未支付，2 已支付，3 已核销，4 已取消，5 退款中，6 已退款 |
| `create_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP` | 下单时间 |
| `pay_time` | `timestamp` | 是 | `NULL` | 支付时间 |
| `use_time` | `timestamp` | 是 | `NULL` | 核销时间 |
| `refund_time` | `timestamp` | 是 | `NULL` | 退款时间 |
| `update_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP`，更新时自动刷新 | 更新时间 |

### `tb_city` 城市字典

| 字段 | 类型 | 可空 | 默认值 | 约束/说明 |
|---|---|---|---|---|
| `id` | `bigint UNSIGNED` | 否 | 自增 | 主键；城市 ID |
| `code` | `varchar(16)` | 否 | 无 | 稳定城市编码；唯一索引 `uk_city_code` |
| `name` | `varchar(64)` | 否 | 无 | 城市名称 |
| `status` | `tinyint UNSIGNED` | 否 | `1` | 状态：0 停用，1 启用 |
| `sort` | `int UNSIGNED` | 否 | `0` | 展示顺序 |
| `create_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `update_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP`，更新时自动刷新 | 更新时间 |

### `tb_content_section` 官方内容分区

| 字段 | 类型 | 可空 | 默认值 | 约束/说明 |
|---|---|---|---|---|
| `id` | `bigint UNSIGNED` | 否 | 自增 | 主键；分区 ID |
| `code` | `varchar(32)` | 否 | 无 | 稳定分区编码；唯一索引 `uk_section_code` |
| `name` | `varchar(32)` | 否 | 无 | 分区名称 |
| `description` | `varchar(255)` | 是 | `NULL` | 分区说明 |
| `icon` | `varchar(255)` | 是 | `NULL` | 图标相对路径 |
| `cover` | `varchar(255)` | 是 | `NULL` | 封面相对路径 |
| `allow_shop_visit` | `tinyint UNSIGNED` | 否 | `0` | 是否允许探店：0 否，1 是 |
| `status` | `tinyint UNSIGNED` | 否 | `1` | 状态：0 停用，1 启用 |
| `sort` | `int UNSIGNED` | 否 | `0` | 展示顺序 |
| `create_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `update_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP`，更新时自动刷新 | 更新时间 |

### `tb_section_follow` 用户关注分区

| 字段 | 类型 | 可空 | 默认值 | 约束/说明 |
|---|---|---|---|---|
| `id` | `bigint UNSIGNED` | 否 | 自增 | 主键 |
| `user_id` | `bigint UNSIGNED` | 否 | 无 | 用户 ID；逻辑关联 `tb_user.id` |
| `section_id` | `bigint UNSIGNED` | 否 | 无 | 分区 ID；逻辑关联 `tb_content_section.id` |
| `create_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP` | 关注时间 |

`user_id + section_id` 使用唯一索引，保证关注操作幂等。

### `tb_media_asset` 媒体资产

| 字段 | 类型 | 可空 | 默认值 | 约束/说明 |
|---|---|---|---|---|
| `id` | `bigint UNSIGNED` | 否 | 自增 | 主键；媒体资产 ID |
| `owner_user_id` | `bigint UNSIGNED` | 否 | 无 | 上传用户；逻辑关联 `tb_user.id` |
| `storage_path` | `varchar(512)` | 否 | 无 | 相对存储路径；唯一索引 |
| `mime_type` | `varchar(64)` | 否 | 无 | 实际识别的图片 MIME 类型 |
| `file_size` | `bigint UNSIGNED` | 否 | 无 | 文件字节数 |
| `width` | `int UNSIGNED` | 否 | 无 | 图片像素宽度 |
| `height` | `int UNSIGNED` | 否 | 无 | 图片像素高度 |
| `status` | `tinyint UNSIGNED` | 否 | `0` | 0 临时，1 已绑定，2 已删除 |
| `bound_type` | `tinyint UNSIGNED` | 是 | `NULL` | 绑定类型：1 动态，2 商户点评 |
| `bound_id` | `bigint UNSIGNED` | 是 | `NULL` | 绑定业务 ID |
| `expire_time` | `timestamp` | 是 | `NULL` | 临时资产过期时间 |
| `create_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `update_time` | `timestamp` | 否 | `CURRENT_TIMESTAMP`，更新时自动刷新 | 更新时间 |

应用层维护状态约束：临时媒体的绑定字段为空且过期时间非空；已绑定媒体的绑定字段非空且过期时间为空。

## 关系、初始化与演进说明

- 当前没有声明数据库级外键。`user_id`、`shop_id`、`type_id`、`blog_id`、`voucher_id` 等关联由应用层校验和维护，不能将字段命名或 Entity 注解视为数据库约束。
- `tb_user_info.user_id`、`tb_seckill_voucher.voucher_id` 使用主键承载逻辑一对一关系；`tb_follow` 暂无数据库唯一约束，关注关系去重由应用负责。
- `seed-dev.sql` 仅供开发环境初始化，现包含杭州、5 个官方分区、用户、商户、分类、笔记和优惠券等示例数据。
- 固定系统分区为 `ROAM_DAILY`“漫游日常”；业务必须按编码查询，不能依赖初始化生成的数据库 ID。
- 数据库不做版本管理，后续结构继续直接维护 `schema-init.sql` 和 `seed-dev.sql`，并同步更新本文件。
- `schema-init.sql` 包含 `DROP TABLE`，只能用于明确允许重建的开发数据库；生产环境和需保留数据的数据库不得直接执行。
