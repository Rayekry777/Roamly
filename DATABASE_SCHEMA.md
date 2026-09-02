# 数据库结构文档

当前结构来源：`src/main/resources/schema-init.sql`；开发数据来源：`src/main/resources/seed-dev.sql`。两者由 `application-dev.yml` 按“先结构、后数据”的顺序初始化。原始合并脚本 `src/main/resources/db/dp.sql` 仅作为历史备份保留。

结构版本：开发初始化脚本（截至 2026-09-02）
业务表数量：10 张
数据库：MySQL / InnoDB / utf8mb4

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

## 表索引总览

本节置于字段明细之前，索引名称、类型和列顺序均以 `schema-init.sql` 的实际声明为准。未列出的字段没有单独索引；表之间当前没有数据库级外键，关联完整性由应用维护。

| 表 | 索引名 | 类型 | 索引列（顺序） | 用途 |
|---|---|---|---|---|
| `tb_blog` | `PRIMARY` | 主键（BTREE） | `id` | 笔记唯一标识 |
| `tb_blog_comments` | `PRIMARY` | 主键（BTREE） | `id` | 评论唯一标识 |
| `tb_follow` | `PRIMARY` | 主键（BTREE） | `id` | 关注记录唯一标识 |
| `tb_seckill_voucher` | `PRIMARY` | 主键（BTREE） | `voucher_id` | 秒杀券与优惠券一对一记录标识 |
| `tb_shop` | `PRIMARY` | 主键（BTREE） | `id` | 商户唯一标识 |
| `tb_shop` | `foreign_key_type` | 普通索引（BTREE） | `type_id` | 按商户分类查询；名称沿用脚本中的历史命名，不代表实际外键 |
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

## 关系、初始化与演进说明

- 当前没有声明数据库级外键。`user_id`、`shop_id`、`type_id`、`blog_id`、`voucher_id` 等关联由应用层校验和维护，不能将字段命名或 Entity 注解视为数据库约束。
- `tb_user_info.user_id`、`tb_seckill_voucher.voucher_id` 使用主键承载逻辑一对一关系；`tb_follow` 暂无数据库唯一约束，关注关系去重由应用负责。
- `seed-dev.sql` 仅供开发环境初始化，包含用户、商户、分类、笔记和优惠券等示例数据；生产环境不得重复执行 `schema-init.sql` 中的 `DROP TABLE` 初始化脚本。
- 当前未使用 Flyway/Liquibase，后续结构变更应迁移到有序迁移管理，并同步更新本文件中的索引、字段和结构版本。
