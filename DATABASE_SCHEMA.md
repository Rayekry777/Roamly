# Roamly 数据库结构契约

```yaml
updatedAt: 2026-09-04
schemaMode: Demo 可重建快照
businessTableCount: 25
database: MySQL / InnoDB / utf8mb4
runtimeVerification: 已验证（25 张当前业务表）
targetBusinessTableCount: 33
targetDesignVersion: 6
targetDesignStatus: 已冻结
targetImplementationStatus: 开发中
```

结构真源为 [schema-init.sql](./ray-server/src/main/resources/schema-init.sql)，开发样例真源为 [seed-dev.sql](./ray-server/src/main/resources/seed-dev.sql)。两者只服务于已授权可清空的 Demo 开发库。

当前源码快照为 25 表：原 19 表、阶段 16 的 `admin_user`、`operation_audit_log`、阶段 17 的 `merchant_account`，阶段 18 的 `merchant_application`、`business_media_asset`，以及阶段 20 新增的 `voucher_package_item` 和阶段 19/20 对业务字段的治理均已完成真实重建和业务场景验证。本文后部其余“阶段 15 至 30 目标结构”仍是冻结设计；在目标 DDL、种子和集成测试全部通过前，禁止把目标 33 表写成已实现。

## 规则

- 不使用 Flyway、Liquibase、版本化迁移或历史表；结构变化直接更新完整快照。
- 不声明物理外键，跨表关系由 Service 在事务内校验和维护。
- 金额以分存储；Java 内部 ID 为 `Long`，HTTP 业务 ID 为字符串。
- 已退役 `blog`、`blog_comments`、`voucher`、`seckill_voucher`，不保留兼容表或转换脚本。
- `schema-init.sql` 包含 `DROP TABLE`，不得用于非 Demo 数据库或生产环境。

## 表目录

| 表 | 用途 | 隔离范围 | 关键约束 |
|---|---|---|---|
| `admin_user` | 管理员账号与固定角色 | 平台级 | 用户名唯一；固定角色；启停状态与乐观锁 |
| `operation_audit_log` | 敏感操作审计 | 平台级 | 操作者、对象、动作和时间索引；只追加 |
| `merchant_account` | 店主及员工账号 | 商户/门店级 | 手机号唯一；激活账号必须绑定门店；角色、状态与乐观锁 |
| `merchant_application` | 商户入驻草稿与审核快照 | 商户账号级 | 每账号唯一申请；状态、提交时间与乐观锁 |
| `business_media_asset` | 营业执照、经营图与后续券图片 | 商户/业务对象级 | 对象键唯一；上传者、用途、生命周期与业务归属约束 |
| `city` | 城市字典 | 平台级 | `code` 唯一 |
| `content_section` | 官方内容分区 | 平台级 | `code` 唯一 |
| `section_follow` | 用户关注分区 | 用户级 | `user_id,section_id` 唯一 |
| `media_asset` | 临时/已绑定媒体 | 用户级 | 存储路径唯一；归属与状态索引 |
| `post` | 统一动态 | 城市/用户级 | 分区、作者、商户、城市流索引 |
| `post_media` | 动态媒体顺序 | 动态级 | 动态顺序与媒体各自唯一 |
| `post_like` | 动态点赞事实 | 用户/动态级 | `post_id,user_id` 唯一 |
| `post_comment` | 根评论与回复 | 动态级 | 根讨论、父评论、状态索引 |
| `post_comment_like` | 评论点赞事实 | 用户/评论级 | `comment_id,user_id` 唯一 |
| `follow` | 用户关注关系 | 用户级 | `user_id,follow_user_id` 唯一 |
| `shop` | 商户、坐标与聚合 | 平台级 | 城市、分类、状态组合索引 |
| `shop_type` | 商户分类 | 平台级 | 主键 |
| `shop_review` | 独立点评 | 商户/用户级 | `shop_id,user_id` 唯一 |
| `shop_review_media` | 点评媒体顺序 | 点评级 | 点评顺序与媒体各自唯一 |
| `user` | 用户账号 | 平台级 | 手机号唯一 |
| `user_info` | 用户资料 | 用户级 | `user_id` 主键 |
| `voucher_product` | 团购商品 | 商户级 | 商户状态与销售期索引 |
| `voucher_order` | 团购订单 | 用户级 | 用户状态时间、商品用户索引 |
| `user_voucher` | 用户券实例 | 用户级 | 券码唯一、订单唯一 |

## 业务约束

- `media_asset.status` 使用 0 临时、1 已绑定、2 已删除；绑定类型 1 动态、2 点评。
- 普通动态自动进入 `ROAM_DAILY`（漫游日常分区）；探店动态必须关联允许探店的启用分区和同城启用商户。
- `post_like`、`post_comment_like`、`section_follow`、`follow` 是关系事实，冗余计数必须与关系行数一致。
- 评论删除清空正文；有有效回复的根评论保留删除占位。
- 用户对同一商户最多一条点评；`shop.comments` 和 `shop.score` 由正常点评重算。
- 商品库存满足总库存、有效占用与可售库存之间的一致性；用户限购按未取消订单的 `quantity` 汇总，并由应用层用户加商品锁串行校验；`sold_count` 只在支付确认成功后累计。
- 订单状态码映射为 1 待支付、2 已支付、4 已取消、5 退款中、6 已退款；对外名称使用 `CANCELED`（已取消）。
- `user_voucher.order_id` 唯一保证支付确认幂等；状态为 `UNUSED`（未使用）、`USED`（已使用）、`EXPIRED`（已过期）、`REFUNDED`（已退款）。

## 开发种子

种子包含：1 个城市、5 个官方分区、3 个商户分类、3 个用户及资料、3 个商户、5 个覆盖全部账号状态的商户账号、2 条审核中/驳回入驻申请及其已绑定私有营业执照、分区关注和用户关注、已绑定动态/点评媒体、3 条动态及其点赞、根评论/回复及点赞、3 条点评、2 个团购商品、待支付/已支付/已取消订单，以及与已支付订单一一对应的未使用券。

种子聚合可由 SQL 事实复核：

- `post.liked_count = count(post_like)`；评论点赞同理。
- `shop.comments = 正常点评数`；`shop.score = round(avg(review.score) * 10)`。
- 商品 3001 的 200 份库存中，一份由待支付订单占用、一份已支付，`available_stock=198`、`sold_count=1`。
- 已支付订单 6002 只对应用户券 7001；已取消订单不占库存、不发券。

## 阶段 15 至 30 目标结构

### 直接覆盖策略

- 目标实现直接重写 `schema-init.sql` 与 `seed-dev.sql`，按依赖逆序删除全部业务表后重建，不提供增量迁移、旧数据导入、双写或兼容视图。
- 允许删除旧列、旧数字状态码、旧枚举语义和 `user_voucher.order_id` 单列唯一约束；所有后端模型、OpenAPI 和三端类型在同一阶段切换。
- 目标业务表固定为 33 张。SnailJob 使用自身独立数据库，其框架表不计入业务表；Fesod 本轮只提供同步导出，不新增导出任务表。
- 金额统一以分保存并使用 `*_amount`；费率以基点保存并使用 `*_rate_bps`；业务状态使用稳定字符串；HTTP ID 继续使用字符串。
- 目标实现前必须再次解析 `.env` 并确认获授权的 Demo 数据库名称。目标快照不得用于生产或任何需要保留数据的库。

### 目标新增表

| 表 | 归属阶段 | 用途 | 关键约束 |
|---|---:|---|---|
| `admin_user` | 16 | 管理员账号与固定角色 | 用户名唯一；BCrypt 密码；固定角色与状态索引；乐观锁 |
| `merchant_application` | 18-19 | 店主入驻资料与审核 | 申请人、状态、提交时间索引；保留驳回原因 |
| `merchant_account` | 17、25 | 店主及员工账号 | 手机号唯一；激活后必须绑定且只能绑定一家门店 |
| `merchant_staff_invitation` | 25 | 单次员工邀请 | 令牌摘要唯一；24 小时过期；只可消费一次 |
| `business_media_asset` | 18、20 | 入驻和团购经营媒体 | 归属类型、归属 ID、状态与顺序索引 |
| `voucher_package_item` | 20 | 套餐券和次卡服务明细 | 商品内顺序唯一 |
| `payment_transaction` | 23 | Mock/微信支付尝试与幂等结果 | 支付单号、订单幂等键唯一 |
| `voucher_refund` | 24 | 单券退款申请和结果 | 退款单号唯一；券级活动记录 |
| `voucher_redemption` | 26 | 核销、按次使用和撤销记录 | 核销幂等键唯一；记录门店和操作人快照 |
| `commission_rule` | 28 | 平台默认及门店覆盖佣金 | 生效区间内规则不可重叠 |
| `fund_ledger_entry` | 28 | 冻结、确认、佣金、退款和调整流水 | 业务事件加分录类型唯一；只追加不更新 |
| `settlement_batch` | 29 | T+1 结算批次 | 结算日和门店唯一 |
| `settlement_item` | 29 | 结算批次与账本明细关系 | 批次和分录唯一 |
| `operation_audit_log` | 16-29 | 管理、商户和资金操作审计 | 操作者、对象、动作和时间索引 |

### 阶段 18 字段冻结

`merchant_application` 字段固定为：

- `id`、唯一 `merchant_account_id`、`status`、`shop_name`、`license_number`、`legal_representative`、`contact_name`、`contact_phone`、`shop_type_id`、`city_code`、`district`、`address`、`longitude`、`latitude`。
- `business_hours_json` 保存七日结构化营业时段；`license_media_id` 保存单张营业执照；`gallery_media_ids_json` 保存零至九张有序经营图片 ID。
- Mock 结算仅保存 `settlement_account_name`、`settlement_bank_name` 与四位 `settlement_account_suffix`，不保存真实完整银行卡号。
- `rejection_reason`、`submission_idempotency_key`、`submitted_at`、`reviewed_at`、`reviewer_admin_id`、`approved_shop_id`、`version`、`create_time`、`update_time`。
- 唯一索引为 `uk_merchant_application_account(merchant_account_id)`；审核队列索引为 `idx_merchant_application_status_submitted(status,submitted_at,id)`；状态只允许 `DRAFT`（草稿）、`PENDING`（审核中）、`APPROVED`（审核通过）、`REJECTED`（审核未通过）。

`business_media_asset` 字段固定为：

- `id`、`uploader_merchant_account_id`、`purpose`、`status`、`bucket_name`、唯一 `object_key`、`original_filename`、`mime_type`、`byte_size`、`width`、`height`。
- 绑定事实为 `owner_type`、`owner_id`、`sort_order`、`bound_at`；生命周期为 `expires_at`、`deleted_at`、`create_time`、`update_time`。
- 上传者清理索引为 `idx_business_media_uploader_status_expiry(uploader_merchant_account_id,status,expires_at,id)`；业务读取索引为 `idx_business_media_owner(owner_type,owner_id,purpose,sort_order,id)`。
- `purpose` 允许 `LICENSE`（营业执照）、`GALLERY`（经营图片）、`VOUCHER_COVER`（券封面）、`VOUCHER_DETAIL`（券详情图）；阶段 18 只开放前两种。`status` 允许 `TEMPORARY`（临时）、`BOUND`（已绑定）、`DELETED`（已删除）；临时记录不得带业务归属，已绑定记录必须同时具备归属类型与 ID。

### 阶段 19 已实现字段

阶段 19 不新增业务表，仍直接覆盖 24 表完整快照；只重构 `merchant_application`、`shop` 与 `merchant_account`。完整事务与接口设计见 [阶段 19 详细设计](./docs/stages/STAGE_19_MERCHANT_REVIEW_AND_SHOP_GOVERNANCE.md)。

`merchant_application` 在阶段 18 字段基础上新增或收紧：

- `review_decision` 只允许 `APPROVAL`（通过）、`REJECTION`（驳回）或空；`review_idempotency_key` 保存首个成功命令的 8 至 128 字符幂等键；`review_request_fingerprint` 保存 64 位小写十六进制 SHA-256 指纹。
- `reviewer_admin_id`、`reviewed_at`、`rejection_reason` 和 `approved_shop_id` 必须与审核状态及决定组合一致；通过必须有唯一门店且无驳回原因，驳回必须有规范化原因且无门店。
- 新增唯一索引 `uk_merchant_application_approved_shop(approved_shop_id)`；MySQL 允许多个空值，仅非空门店 ID 唯一。审核成功使 `version` 自增 1。

`shop` 直接把旧数字启停字段重构为经营事实：

- `status` 只允许 `PENDING`（待激活）、`ACTIVE`（营业中）、`SUSPENDED`（已停用）、`CLOSED`（已关闭）；本阶段审核通过创建的门店直接为 `ACTIVE`。
- 新增唯一非空 `source_application_id`、`business_hours_json`、`activated_at`、`suspended_at`、`suspension_reason`、`status_changed_by_admin_id`、`status_command_type`、`status_idempotency_key`、`status_request_fingerprint` 和 `version`。
- `status_command_type` 只允许 `SUSPENSION`（停用）、`ACTIVATION`（恢复）或空；相同幂等键和指纹只重放结果，不重复联动账号或写审计。
- 新增唯一索引 `uk_shop_source_application(source_application_id)` 与列表索引 `idx_shop_status_city_type(status,city_code,shop_type_id,id)`；既有 `images` 只保留消费者摘要，不保存证照或审核治理事实。

`merchant_account` 新增选择性恢复事实：

- `disabled_source` 只允许 `SHOP_SUSPENSION`（门店停用联动）、`ACCOUNT_GOVERNANCE`（平台账号治理）、`STAFF_MANAGEMENT`（员工管理）或空。
- `disabled_reason` 最多 500 字，`disabled_at` 保存停用时间；非 `DISABLED`（已停用）账号必须清空三项。
- 门店停用只条件更新当前 `ACTIVE`（已激活）账号并写 `SHOP_SUSPENSION`；门店恢复只条件恢复该来源账号，其他来源保持停用。

阶段 19 种子必须至少包含一个 `PENDING`（审核中）申请、一个 `REJECTED`（审核未通过）申请、可审核的绑定证照，以及可执行停用/恢复的活动门店和店主。审核与治理数据库测试结束后必须重新执行完整快照，恢复相同的 25 表纯种子状态。

### 阶段 20 字段冻结

阶段 20 直接重构 `voucher_product` 并新增 `voucher_package_item`，当前快照已由 24 表变为 25 表；目标 33 表总数不变。完整字段、索引、媒体同步、状态约束和种子规则以 [阶段 20 详细设计](./docs/stages/STAGE_20_VOUCHER_AUTHORING.md) 为真源。

- `voucher_product` 删除 `cover`、`rules`、`pay_price`、`original_price`、`deduction_value`、`sale_type` 和旧单一 `status`，改为四类券专属字段、结构化时间/使用规则、`review_status`（审核状态）与 `sale_status`（销售状态）。
- 商品索引固定为 `idx_voucher_product_shop_review(shop_id,review_status,update_time,id)`、`idx_voucher_product_public(shop_id,review_status,sale_status,sale_begin_time,sale_end_time,id)` 与 `idx_voucher_product_submission(submission_idempotency_key)`。
- `voucher_package_item` 保存套餐券和次卡的有序明细，以 `product_id,sort_order` 唯一；代金券和折扣券不得存在明细。
- 券媒体保存即绑定 `VOUCHER_PRODUCT`（券商品），封面恰好一张、详情图最多九张；复制创建独立对象键，删除在事务提交后清理。
- 既有商品 3001/3002 直接转换为已审核在售种子，保持订单、库存、销量与消费者 Demo；新增四类草稿和一个审核中商品。阶段 20 数据库级测试已验证媒体绑定、版本冲突、角色隔离、提交幂等和 25 表快照，并在结束后恢复纯种子状态。

### 目标重构表

- `shop`：使用字符串经营状态，保存入驻申请来源、激活/停用时间和停用原因；经营媒体转为 `business_media_asset`，不再以逗号字符串扩展新能力。
- `admin_user`：保存不可变用户名、密码摘要、显示名、固定角色、启停状态、强制改密标记、最后登录时间、创建人、版本和创建/更新时间；不引入若依 `sys_user`、`sys_role`、`sys_menu` 或关联表。
- `voucher_product`：直接使用四种券型、审核状态、销售状态、结构化有效期/时段、退款规则、库存、限购及乐观锁字段；删除含义模糊的旧字段。
- `voucher_order`：使用字符串订单状态，新增 `payment_expire_time`、支付状态、履约状态、佣金快照和服务端时间契约；支付状态与核销履约状态分离。
- `user_voucher`：按订单数量逐份生成，以 `order_id,sequence_no` 组合唯一；保存加密券码、HMAC 索引、规则/价格快照、总次数、剩余次数和履约状态。

### 目标业务枚举

- 管理角色：`PLATFORM_ADMIN`（平台超级管理员）、`MERCHANT_REVIEWER`（商户审核员）、`FINANCE`（财务管理员）。
- 管理员账号状态：`ACTIVE`（已启用）、`DISABLED`（已停用）。
- 商户角色：`OWNER`（店主）、`MANAGER`（店长）、`VERIFIER`（核销员）。
- 商户账号状态：`NOT_APPLIED`（未入驻）、`PENDING`（审核中）、`ACTIVE`（已激活）、`REJECTED`（审核未通过）、`DISABLED`（已停用）。
- 门店经营状态：`PENDING`（待激活）、`ACTIVE`（营业中）、`SUSPENDED`（已停用）、`CLOSED`（已关闭）。
- 券型：`PACKAGE`（套餐券）、`CASH`（代金券）、`DISCOUNT`（折扣券）、`MULTI_USE`（次卡）。
- 券审核状态：`DRAFT`（草稿）、`PENDING`（审核中）、`APPROVED`（审核通过）、`REJECTED`（审核未通过）。
- 券销售状态：`SCHEDULED`（待开售）、`ON_SALE`（销售中）、`OFF_SALE`（已下架）、`SOLD_OUT`（已售罄）、`ENDED`（已结束）。
- 订单状态：`PENDING_PAYMENT`（待支付）、`PAID`（已支付）、`CANCELED`（已取消）、`REFUNDING`（退款中）、`REFUNDED`（已退款）。
- 支付状态：`PENDING`（待支付）、`SUCCEEDED`（支付成功）、`FAILED`（支付失败）、`CLOSED`（已关闭）、`PARTIALLY_REFUNDED`（部分退款）、`REFUNDED`（已退款）。
- 用户券状态：`UNUSED`（未使用）、`PARTIALLY_USED`（部分使用）、`USED`（已使用）、`EXPIRED`（已过期）、`REFUNDING`（退款中）、`REFUNDED`（已退款）。
- 退款状态：`REQUESTED`（已申请）、`PROCESSING`（处理中）、`SUCCEEDED`（退款成功）、`FAILED`（退款失败）、`REJECTED`（退款被拒）。
- 结算金额状态：`FROZEN`（冻结中）、`SETTLEABLE`（待结算）、`SETTLED`（已结算）、`ADJUSTMENT`（调整项）。
- 账本事件：`PAYMENT_FROZEN`（支付资金冻结）、`REDEMPTION_RECOGNIZED`（核销收入确认）、`COMMISSION_RECOGNIZED`（平台佣金确认）、`REFUND_REVERSED`（退款冲回）、`REDEMPTION_REVERSED`（核销撤销）、`SETTLEMENT_POSTED`（结算入账）、`SETTLEMENT_ADJUSTMENT`（结算调整）。

### 目标一致性

- 待支付订单占用库存；15 分钟关单通过条件更新只返库一次；支付成功后销量按数量累计并逐份发券。
- 手输券码只通过 HMAC 索引定位，动态二维码 60 秒过期；核销预览不改变状态，确认核销使用唯一幂等键。
- 次卡每次核销扣减一次，最后一次转为已使用；核销撤销必须在进入结算前完成并追加反向记录。
- 支付成功记冻结账本，核销后按 5% 默认佣金或门店覆盖费率确认，次日 02:00 生成 T+1 Mock 结算。
- 所有资金表只追加事实或显式状态迁移，不覆盖历史金额；已结算退款通过负向调整进入后续结算。

### 设计冻结门禁

目标数据库必须先在对应阶段文档中完成表、字段、唯一约束、索引、状态机、事务边界和测试数据设计。冻结后改变任一数据库公共事实时，必须先解冻并升级设计版本；不得先改 DDL 再补文档。

## 集成验收

`DatabaseBusinessClosureIntegrationTest` 仅在 `RUN_DATABASE_INTEGRATION_TESTS=true` 时运行。它在开始前重建当前快照，使用 Redis DB 15，完成真实 HTTP/Service/SQL 场景后再次重建种子并清空测试 Redis，确保日常开发环境回到纯种子状态。

2026-09-05 已在 `.env` 当前指向且获授权的开发库完成 25 表 Demo 验收：

- 当次完整执行 `schema-init.sql` 与 `seed-dev.sql`，确认 25 张业务表、关键唯一索引、旧表退役和种子一致性。
- `DatabaseBusinessClosureIntegrationTest` 8 项全部通过，覆盖商户登录/限流/五种状态/首次建号/停用会话/三域隔离、管理员账号与审计、入驻媒体、申请审核、门店停用与选择性恢复、四类券建券/媒体/复制/提交/角色隔离，以及社区、点评、订单、发券、过期刷新和用户隔离。
- `OpenApiAndAuthRuntimeTest` 8 项全部通过，确认运行时 OpenAPI 98 个唯一 `operationId`、券审核与下架 Schema、全部 `$ref`、Bearer 声明和关键错误响应。
- 测试结束后再次重建快照并恢复纯种子数据，Redis DB 15 已清空，不保留测试期间生成的业务数据或登录状态。
- 阶段 21 不新增业务表；目标 33 表仍只完成冻结设计，阶段 22 至 29 的新增表不得提前标记为已实现。
