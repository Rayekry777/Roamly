# Roamly 数据库结构契约

```yaml
updatedAt: 2026-09-12
schemaMode: Demo 可重建快照
businessTableCount: 51
database: MySQL / InnoDB / utf8mb4
runtimeVerification: 静态 DDL 已验证（51 张业务表）；真实数据库重建待授权
targetBusinessTableCount: 51
targetDesignVersion: 11
targetDesignStatus: 已冻结
targetImplementationStatus: 阶段 40 已实现
demoDataClosureStatus: 已实现
```

结构真源为 [schema-init.sql](./ray-server/src/main/resources/schema-init.sql)，开发样例真源为 [seed-dev.sql](./ray-server/src/main/resources/seed-dev.sql)。两者只服务于已授权可清空的 Demo 开发库。

当前源码快照为 51 张业务表：阶段 40 在原 46 表基础上新增客服已读游标、标签关系、转交和快捷回复五张表，并扩展客服工单 SLA 与附件生命周期。目标 DDL 与种子保持可重建快照。

## 规则

- 不使用 Flyway、Liquibase、版本化迁移或历史表；结构变化直接更新完整快照。
- 不声明物理外键，跨表关系由 Service 在事务内校验和维护。
- 金额以分存储；Java 内部 ID 为 `Long`，HTTP 业务 ID 为字符串。
- `platform_discount_amount` 是现有平台承担优惠的兼容字段，财务语义等同平台补贴；商家毛应收为顾客实付加平台补贴，预计收入再扣除服务费。
- 已退役 `blog`、`blog_comments`、`voucher`、`seckill_voucher`，不保留兼容表或转换脚本。
- `schema-init.sql` 在文件开头按依赖逆序集中执行全部业务表的 `DROP TABLE IF EXISTS`，随后统一建表；不得用于非 Demo 数据库或生产环境。

## 表目录

| 表 | 用途 | 隔离范围 | 关键约束 |
|---|---|---|---|
| `admin_user` | 管理员账号与固定角色 | 平台级 | 用户名唯一；固定角色；启停状态与乐观锁 |
| `operation_audit_log` | 敏感操作审计 | 平台级 | 操作者、对象、动作和时间索引；只追加 |
| `merchant_account` | 游客、租户及员工账号 | 商户/门店级 | 手机号唯一；BCrypt 密码；头像媒体；角色、状态、门店组合与乐观锁 |
| `merchant_application` | 商户入驻草稿与审核快照 | 商户账号级 | 每账号唯一申请；状态、提交时间与乐观锁 |
| `business_media_asset` | 营业执照、经营图、券图片与商户头像 | 商户/业务对象级 | 对象键唯一；上传者、用途、生命周期与业务归属约束 |
| `city` | 城市字典 | 平台级 | `code` 唯一；开发种子包含杭州、西宁 |
| `district` | 区县字典与定位服务范围 | 城市级 | `code` 唯一；`city_code` 归属城市；中心坐标和服务半径用于定位解析 |
| `content_section` | 官方内容分区 | 平台级 | `code` 唯一 |
| `section_follow` | 用户关注分区 | 用户级 | `user_id,section_id` 唯一 |
| `media_asset` | 临时/已绑定媒体 | 用户级 | 存储路径唯一；归属与状态索引 |
| `post` | 统一动态 | 城市/用户级 | 分区、作者、商户、城市过滤、区县推荐软加权和粗粒度位置索引 |
| `post_media` | 动态媒体顺序 | 动态级 | 动态顺序与媒体各自唯一 |
| `post_like` | 动态点赞事实 | 用户/动态级 | `post_id,user_id` 唯一 |
| `post_comment` | 根评论与回复 | 动态级 | 根讨论、父评论、状态索引 |
| `post_comment_like` | 评论点赞事实 | 用户/评论级 | `comment_id,user_id` 唯一 |
| `follow` | 用户关注关系 | 用户级 | `user_id,follow_user_id` 唯一 |
| `shop` | 商户、坐标与聚合 | 平台级 | `city_code` 与 `district_code` 分列保存；经纬度用于距离计算 |
| `shop_type` | 商户分类 | 平台级 | 主键 |
| `shop_review` | 独立点评 | 商户/用户级 | `shop_id,user_id` 唯一 |
| `shop_review_media` | 点评媒体顺序 | 点评级 | 点评顺序与媒体各自唯一 |
| `user` | 用户账号 | 平台级 | 手机号唯一 |
| `user_profile` | 消费者私有资料与城市偏好 | 用户级 | `user_id` 主键；性别 CHECK；内部城市编码 |
| `voucher_product` | 团购商品 | 商户级 | 商户状态与销售期索引 |
| `voucher_order` | 团购订单与独立售后聚合状态 | 用户级 | 交易状态与 `after_sale_status` 分离索引、商品用户索引 |
| `user_voucher` | 用户券实例 | 用户级 | 券码唯一、订单唯一 |
| `user_voucher_qr_code` | 用户券固定二维码凭证 | 用户级 | `voucher_id` 与 `token_key` 唯一；不保存明文二维码 token |
| `voucher_package_item` | 套餐券与次卡兼容明细 | 商品级 | `product_id,sort_order` 唯一 |
| `voucher_product_detail` | 商户编写的统一详情分段 | 商品级 | `product_id,sort_order` 唯一 |
| `voucher_product_tag` | 商品标签与图标键 | 商品级 | `product_id,sort_order` 唯一 |
| `voucher_product_cash_rule` | 代金券权益规则 | 商品级 | `product_id` 主键 |
| `voucher_product_discount_rule` | 折扣券说明规则（仅展示） | 商品级 | `product_id` 主键 |
| `voucher_product_multi_use_rule` | 次卡权益规则 | 商品级 | `product_id` 主键 |
| `payment_transaction` | 支付尝试与支付结果 | 用户/订单级 | `order_id,idempotency_key` 唯一 |
| `voucher_refund` | 统一消费者/商户/管理员退款申请与处理结果 | 用户/订单/门店级 | 幂等键唯一；保存审核人、当前处理人、版本、渠道失败和退款冲回关联 |
| `voucher_refund_item` | 退款申请逐券明细与财务快照 | 用户/订单/门店级 | `refund_id,voucher_id` 唯一；保存可退金额、核销快照和冲回金额 |
| `voucher_refund_attempt` | 每次 Mock 渠道退款执行尝试 | 订单/退款级 | 幂等键唯一；租约和下次重试时间索引 |
| `merchant_staff_invitation` | 租户员工短时邀请 | 商户/门店级 | 六位凭证 HMAC 摘要；签发幂等键唯一；手机号、状态和过期时间索引 |
| `voucher_redemption` | 核销与撤销记录 | 商户/门店级 | `shop_id,idempotency_key` 唯一 |
| `commission_rule` | 平台默认与门店佣金规则 | 平台/门店级 | 费率及生效区间索引 |
| `fund_ledger_entry` | 支付、核销、退款和结算账本 | 平台/门店级 | 业务事件、分录类型和账户方向唯一；只追加 |
| `customer_service_ticket` | 平台统一客服工单、申请人归属与 SLA | 申请人/订单/门店级 | 工单号唯一；`applicant_type,applicant_id` 为访问真源；原子认领与 SLA 队列索引 |
| `customer_service_message` | 公开回复与内部备注 | 工单级 | `visibility` 隔离消费者可见内容 |
| `customer_service_attachment` | 临时及已绑定的私有工单图片 | 工单/消息级 | 对象键唯一；上传者、工单、状态和过期时间索引 |
| `customer_service_read_cursor` | 每名阅读者的工单已读位置 | 工单/阅读者级 | `ticket_id,reader_type,reader_id` 唯一；游标只前进 |
| `customer_service_tag` | 平台客服标签字典 | 平台级 | 标签编码唯一；启停索引 |
| `customer_service_ticket_tag` | 工单标签关系 | 工单级 | `ticket_id,tag_id` 唯一；标签反向筛选索引 |
| `customer_service_transfer` | 工单转交审计记录 | 工单/客服级 | 按工单时间和目标客服索引；只追加 |
| `customer_service_quick_reply` | 个人及团队快捷回复 | 平台/客服级 | 范围、所有者、启停和排序索引 |
| `settlement_batch` | T+1 结算批次 | 商户/门店级 | `shop_id,settlement_date` 唯一 |
| `settlement_item` | 结算批次账本明细 | 商户/门店级 | `batch_id,ledger_entry_id` 唯一 |
| `settlement_attempt` | 结算批次 Mock 执行尝试 | 商户/门店级 | 幂等键唯一；租约、重试和失败原因索引 |

退款闭环补充：`voucher_refund` 通过 `source` 区分消费者、商户和管理员发起，申请涉及的券集合只由 `voucher_refund_item` 逐券保存；`voucher_ids` 已从 DDL 和实体删除，`voucher_id` 只保留第一张券兼容投影。审核状态为 `PENDING_REVIEW/AUTO_APPROVED/MANUAL_APPROVED/REJECTED`，执行状态为 `WAITING_EXECUTION/PROCESSING/SUCCESS/PARTIAL_SUCCESS/FAILED/RETRY_WAITING/MANUAL_REQUIRED`。执行尝试由 `voucher_refund_attempt` 记录，以条件更新竞争租约；领取事务先提交，Mock 调用不占用数据库事务，结果事务再锁定尝试并校验租约所有者。退款成功必须在 `fund_ledger_entry` 追加幂等冲回分录。商户售后权限为 `merchant:after-sales:read/create`，平台审批仍使用 `admin:refund:manage`。

客服闭环补充：消费者和商户只按 `applicant_type + applicant_id` 查询自己的工单，`related_user_id/related_shop_id/order_id/refund_id/voucher_id/redemption_id` 只保存经权限校验的上下文。旧 `user_id/shop_id` 保留兼容但不再参与权限查询。`customer_service_message.visibility=INTERNAL` 的消息及附件仅管理端可读。附件先以 `TEMPORARY` 写入私有 Local/S3 对象存储，绑定消息后变为 `BOUND`，过期或主动删除变为 `DELETED`。所有表均不声明物理外键，工单、消息、附件、标签和转交关系由服务事务校验。

## 阶段 39 已有开发数据库 SQL 顺序

以下只适用于已经完成阶段 38 且确认可修改的开发数据库；执行前必须备份并核对现有索引。应用启动不会自动执行这些语句。

```sql
ALTER TABLE voucher_order
  ADD COLUMN after_sale_status varchar(24) NOT NULL DEFAULT 'NONE' AFTER status,
  ADD INDEX idx_order_user_after_sale (user_id, after_sale_status, update_time, id);

ALTER TABLE voucher_refund
  DROP INDEX uk_voucher_refund_voucher_key,
  DROP COLUMN voucher_ids,
  MODIFY decision_status varchar(24) NOT NULL DEFAULT 'PENDING_REVIEW',
  MODIFY execution_status varchar(24) NOT NULL DEFAULT 'WAITING_EXECUTION',
  ADD COLUMN current_handler_id bigint UNSIGNED NULL AFTER payment_provider,
  ADD COLUMN reviewer_admin_id bigint UNSIGNED NULL AFTER current_handler_id,
  ADD COLUMN review_note varchar(500) NULL AFTER reviewer_admin_id,
  ADD COLUMN version int UNSIGNED NOT NULL DEFAULT 0 AFTER review_note,
  ADD UNIQUE INDEX uk_voucher_refund_idempotency (idempotency_key),
  ADD INDEX idx_voucher_refund_workbench (decision_status, execution_status, requested_time, id),
  ADD INDEX idx_voucher_refund_handler (current_handler_id, execution_status, id);
```

执行列删除和唯一索引前，必须先把旧 `voucher_ids` 拆分写入 `voucher_refund_item`，并排查重复 `idempotency_key`；本仓库 Demo 快照已直接完成数据切换，不为未知现有库自动生成或执行数据修复。

## 阶段 40 已有开发数据库 SQL 顺序

以下顺序只适用于已经完成阶段 39、确认允许修改并已备份的开发数据库。MySQL DDL 会隐式提交，不能依赖一个外层事务整体回滚；应用不会自动执行这些语句，本次也没有连接或修改任何现有数据库。

1. 先确认旧工单只由消费者或商户创建；若查询有结果，停止并人工确定申请人：

```sql
SELECT id, created_by_type, created_by_id
FROM customer_service_ticket
WHERE created_by_type NOT IN ('CONSUMER', 'MERCHANT') OR created_by_id IS NULL;
```

2. 先增加允许为空的归属和 SLA 字段，再回填旧数据：

```sql
ALTER TABLE customer_service_ticket
  ADD COLUMN applicant_type varchar(16) NULL AFTER priority,
  ADD COLUMN applicant_id bigint UNSIGNED NULL AFTER applicant_type,
  ADD COLUMN related_user_id bigint UNSIGNED NULL AFTER applicant_id,
  ADD COLUMN related_shop_id bigint UNSIGNED NULL AFTER related_user_id,
  ADD COLUMN last_response_time timestamp NULL AFTER first_response_time,
  ADD COLUMN waiting_customer_since timestamp NULL AFTER last_response_time,
  ADD COLUMN waiting_merchant_since timestamp NULL AFTER waiting_customer_since,
  ADD COLUMN sla_deadline timestamp NULL AFTER last_message_time,
  ADD COLUMN sla_breached tinyint(1) NOT NULL DEFAULT 0 AFTER sla_deadline,
  ADD COLUMN has_internal_note tinyint(1) NOT NULL DEFAULT 0 AFTER sla_breached;

UPDATE customer_service_ticket
SET applicant_type = created_by_type,
    applicant_id = created_by_id,
    related_user_id = CASE WHEN created_by_type = 'CONSUMER' THEN user_id ELSE NULL END,
    related_shop_id = shop_id,
    status = CASE WHEN status = 'NEW' THEN 'OPEN' ELSE status END,
    sla_deadline = DATE_ADD(COALESCE(create_time, CURRENT_TIMESTAMP), INTERVAL 4 HOUR),
    has_internal_note = EXISTS (
      SELECT 1 FROM customer_service_message m
      WHERE m.ticket_id = customer_service_ticket.id AND m.visibility = 'INTERNAL'
    );

ALTER TABLE customer_service_ticket
  MODIFY applicant_type varchar(16) NOT NULL,
  MODIFY applicant_id bigint UNSIGNED NOT NULL,
  MODIFY sla_deadline timestamp NOT NULL,
  MODIFY status varchar(24) NOT NULL DEFAULT 'OPEN';
```

3. 在确认旧索引名称与阶段 39 快照一致后替换队列和归属索引：

```sql
ALTER TABLE customer_service_ticket
  DROP INDEX idx_customer_service_ticket_queue,
  DROP INDEX idx_customer_service_ticket_user,
  DROP INDEX idx_customer_service_ticket_shop,
  ADD INDEX idx_customer_service_ticket_queue (sla_breached,status,priority,sla_deadline,id),
  ADD INDEX idx_customer_service_ticket_applicant (applicant_type,applicant_id,last_message_time,id),
  ADD INDEX idx_customer_service_ticket_related_user (related_user_id,update_time,id),
  ADD INDEX idx_customer_service_ticket_related_shop (related_shop_id,update_time,id);
```

4. 附件先增加兼容字段并回填现有对象。`<当前私有桶名>` 必须替换为环境实际配置，不得原样执行：

```sql
ALTER TABLE customer_service_attachment
  MODIFY message_id bigint UNSIGNED NULL,
  ADD COLUMN status varchar(16) NOT NULL DEFAULT 'TEMPORARY' AFTER message_id,
  ADD COLUMN bucket_name varchar(128) NULL AFTER object_key,
  ADD COLUMN expires_at timestamp NULL AFTER byte_size,
  ADD COLUMN bound_at timestamp NULL AFTER expires_at,
  ADD COLUMN deleted_at timestamp NULL AFTER bound_at,
  ADD COLUMN update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER create_time;

UPDATE customer_service_attachment
SET status = CASE WHEN message_id IS NULL THEN 'TEMPORARY' ELSE 'BOUND' END,
    bucket_name = '<当前私有桶名>',
    bound_at = CASE WHEN message_id IS NULL THEN NULL ELSE create_time END,
    expires_at = CASE WHEN message_id IS NULL THEN DATE_ADD(create_time, INTERVAL 24 HOUR) ELSE NULL END;

ALTER TABLE customer_service_attachment
  MODIFY bucket_name varchar(128) NOT NULL,
  DROP INDEX idx_customer_service_attachment_message,
  ADD INDEX idx_customer_service_attachment_message (message_id,status,id),
  ADD INDEX idx_customer_service_attachment_temporary (uploader_type,uploader_id,status,expires_at,id),
  ADD INDEX idx_customer_service_attachment_ticket (ticket_id,status,id);
```

5. 最后按 [schema-init.sql](./ray-server/src/main/resources/schema-init.sql) 中的完整定义依次创建：`customer_service_read_cursor`、`customer_service_tag`、`customer_service_ticket_tag`、`customer_service_transfer`、`customer_service_quick_reply`。创建后再按 [seed-dev.sql](./ray-server/src/main/resources/seed-dev.sql) 的标签与快捷回复样例选择性初始化字典；不得复制工单、消息、转交或已读游标业务样例到有保留数据的库。

6. 上线新代码前复核：不存在 `NEW` 状态、不存在空 `applicant_type/applicant_id/sla_deadline`、所有已绑定附件都有 `message_id` 和 `bound_at`，并确认五张新表及其唯一索引均已创建。

## 业务约束

- `media_asset.status` 使用 0 临时、1 已绑定、2 已删除；绑定类型 1 动态、2 点评、3 用户头像。上传时必须声明 `USER_AVATAR/POST/SHOP_REVIEW` 用途，文件按用途进入独立目录，绑定时再次校验路径用途，避免头像与内容图片交叉复用。
- 普通动态自动进入 `ROAM_DAILY`（漫游日常分区）；探店动态必须关联允许探店的启用分区和同城启用商户。
- `post_like`、`post_comment_like`、`section_follow`、`follow` 是关系事实，冗余计数必须与关系行数一致。
- 评论删除清空正文；有有效回复的根评论保留删除占位。
- 用户对同一商户最多一条点评；`shop.comments` 和 `shop.score` 由正常点评重算。
- 商品库存满足总库存、有效占用与可售库存之间的一致性；用户限购按未取消订单的 `quantity` 汇总，并由应用层用户加商品锁串行校验；`sold_count` 只在支付确认成功后累计。
- 订单交易状态为 `PENDING_PAYMENT/PAID/CANCELED/COMPLETED`；售后聚合状态独立保存于 `after_sale_status`。旧交易状态 `REFUNDING/REFUNDED` 仅为历史读取兼容，不再由新退款写入。
- 核销收入满足 `商家毛应收 = customer_paid_amount + platform_discount_amount`、`estimated_income_amount = 商家毛应收 - service_fee_amount`；历史商品和服务费规则变化不得改写核销快照。
- `user_voucher.order_id` 唯一保证支付确认幂等；状态为 `UNUSED`（未使用）、`PARTIALLY_USED`（部分使用）、`USED`（已使用）、`EXPIRED`（已过期）、`REFUNDING`（退款中）、`REFUNDED`（已退款）。
- `user_voucher_qr_code` 每张用户券仅一条，`token_key` 是不可猜测的随机定位值；服务端以 HMAC 校验 `rq1.{tokenKey}.{signature}`，二维码不写入 Redis、不因扫码删除。

## 开发测试账号

以下账号只存在于 `dev` Profile 每次可重建的 Demo 数据库，禁止复制到生产。消费者端和商户端登录前可获取短信验证码，默认 Mock 验证码由 `SMS_MOCK_CODE` 控制，未覆盖时为 `123456`；消费者与商户种子密码统一为 `Roamly123`。

| 端 | 推荐测试账号 | 凭据 | 可验证范围 |
|---|---|---|---|
| 消费者小程序 | `13456789011`（用户 ID `3`） | 短信验证码 `123456` 或密码 `Roamly123` | 社区、关注、探店、订单各状态、六种券状态、退款与固定二维码 |
| 商户小程序 | `13900000001`（租户，账号 ID `1`） | 验证码 `123456` 或密码 `Roamly123` | 工作台、商品、订单、员工、核销、财务与结算 |
| 管理 Web | `admin`（平台超级管理员，ID `1`） | 密码 `Roamly123` | 全部管理菜单和操作；无需首次改密 |
| 后端/Knife4j | 无独立账号 | 使用上述三类登录接口取得对应 Bearer Token | 验证三登录域及全部受保护接口 |

角色隔离附加账号：

| 登录域 | 账号 | 凭据 | 角色/状态 |
|---|---|---|---|
| 管理端 | `reviewer.demo` | `Roamly123` | `MERCHANT_REVIEWER`（商户审核员），已启用 |
| 管理端 | `finance.demo` | `Roamly123` | `FINANCE`（财务管理员），已启用 |
| 管理端 | `reviewer.disabled` | `Roamly123` | 商户审核员，已停用，用于登录拒绝 |
| 商户端 | `13900000031` | `123456` | `MANAGER`（店长），已激活 |
| 商户端 | `13900000032` | `123456` | `VERIFIER`（核销员），已激活 |
| 商户端 | `13900000033` | `123456` | 店长，被租户停用 |
| 商户端 | `13900000034` | `123456` | 未入驻，关联一条待接受核销员邀请 |

默认开发密钥下，待接受邀请的六位凭证为 `482731`，数据库只保存手机号与凭证组合的 HMAC-SHA256 摘要；接受后会把 `13900000034` 绑定到门店 1。若覆盖了开发邀请密钥，应由租户重新签发凭证；需要重置演示状态时只能在明确授权的 Demo 数据库重新执行完整快照。

## 开发种子闭环

当前种子保证 51 张业务表全部非空，并为列表、筛选、详情、状态标签、权限差异和操作按钮提供适量数据：

| 领域 | 数量与状态覆盖 |
|---|---|
| 账号与字典 | 5 个管理员、10 个商户账号、3 个消费者、2 个城市、6 个区县、5 个内容分区、3 个门店分类 |
| 入驻与门店 | 5 条入驻申请，覆盖 `PENDING/REJECTED/APPROVED`；6 家活动门店；6 条绑定经营媒体 |
| 商户员工 | 4 条邀请，完整覆盖 `PENDING/ACCEPTED/REVOKED/EXPIRED`；租户、店长、核销员和员工停用样例 |
| 社区与点评 | 12 条动态、26 个动态点赞、3 条评论/回复、5 个评论点赞、3 个关注、3 个分区关注、4 条点评和已核销消费点评 |
| 券商品 | 20 个商品，覆盖四种券型、`DRAFT/PENDING/APPROVED/REJECTED` 审核状态及全部五种销售状态；8 条套餐/次卡明细 |
| 订单与支付 | 14 笔订单，交易状态覆盖 `PENDING_PAYMENT/PAID/CANCELED`，售后状态独立覆盖审核、执行、失败、拒绝和完成；15 条支付尝试覆盖 `PENDING/SUCCEEDED/FAILED/CLOSED/PARTIALLY_REFUNDED/REFUNDED` |
| 券包与退款 | 12 张用户券，覆盖 `UNUSED/PARTIALLY_USED/USED/EXPIRED/REFUNDING/REFUNDED`；5 条退款覆盖全部退款状态 |
| 核销与资金 | 5 条核销/撤销、2 条佣金规则、14 条八类账本分录、4 个结算批次覆盖 `PROCESSING/SUCCEEDED/FAILED`、10 条结算明细、9 条审计记录 |

可复核的代表性闭环如下：

- 消费履约：订单 `6012` → 成功支付 `80013` → 用户券 `7010` → 成功核销 `9204` → 消费认证点评 `4004`。
- 退款：订单 `6007` → 成功支付 `80008` → 用户券 `7005` → 成功退款 `9101` → 退款冲回账本 `110004`。
- 核销结算：订单 `6006` → 用户券 `7004` → 核销 `9203` → 收入/佣金分录 `110005/110006` → 结算批次 `120001` 与明细 `130001/130002`。
- 次卡：订单 `6005` → 五次卡 `7003` → 两次成功核销 `9201/9202` → 每次收入 2560 分、佣金 128 分，剩余三次且状态为 `PARTIALLY_USED`。
- 员工：租户账号 `1` → 已接受邀请 `10001` → 店长账号 `31`；另有待接受、已撤销和已过期邀请。

种子聚合可由 SQL 事实复核：

- `post.liked_count = count(post_like)`；评论点赞同理。
- `shop.comments = 正常点评数`；`shop.score = round(avg(review.score) * 10)`。
- 商品 3001 的 200 份库存中，一份由待支付订单占用、一份已支付，`available_stock=198`、`sold_count=1`。
- 商品 3002 已售一份，`available_stock=79`、`sold_count=1`；商品 3003 已售九份，`available_stock=91`、`sold_count=9`；商品 3004 已售一份，`available_stock=49`、`sold_count=1`。
- 每个已支付订单按购买数量关联用户券；待支付与已取消订单不发券。退款、核销、点评、账本和结算样例均能沿业务 ID 反向追踪。

## 阶段 15 至 30 目标结构

### 直接覆盖策略

- 目标实现直接重写 `schema-init.sql` 与 `seed-dev.sql`，按依赖逆序删除全部业务表后重建，不提供增量迁移、旧数据导入、双写或兼容视图。
- 允许删除旧列、旧数字状态码、旧枚举语义和 `user_voucher.order_id` 单列唯一约束；所有后端模型、OpenAPI 和三端类型在同一阶段切换。
- 目标业务表固定为 43 张。未来 SnailJob 适配器如启用，将使用自身独立数据库，其框架表不计入业务表；Demo 的内置调度器不新增任务表。Fesod 适配器同样不改变业务表，本轮由自有 OOXML writer 提供同步导出。
- 金额统一以分保存并使用 `*_amount`；费率以基点保存并使用 `*_rate_bps`；业务状态使用稳定字符串；HTTP ID 继续使用字符串。
- 目标实现前必须再次解析 `.env` 并确认获授权的 Demo 数据库名称。目标快照不得用于生产或任何需要保留数据的库。

### 目标新增表

| 表 | 归属阶段 | 用途 | 关键约束 |
|---|---:|---|---|
| `admin_user` | 16 | 管理员账号与固定角色 | 用户名唯一；BCrypt 密码；固定角色与状态索引；乐观锁 |
| `merchant_application` | 18-19、36 | 游客入驻资料与审核 | 申请人、状态、提交时间索引；保留驳回原因 |
| `merchant_account` | 17、25、36 | 游客、租户及员工账号 | 手机号唯一；角色、状态和单公司归属组合约束 |
| `merchant_staff_invitation` | 25、36 | 单次员工邀请 | 六位凭证 HMAC 摘要；60 秒过期；只可消费一次 |
| `user_voucher_qr_code` | 31 | 用户券有效期内固定二维码定位与版本 | 用户券唯一；随机定位值唯一；HMAC 在应用层校验 |
| `business_media_asset` | 18、20 | 入驻和团购经营媒体 | 归属类型、归属 ID、状态与顺序索引 |
| `voucher_package_item` | 20 | 套餐券和次卡服务明细 | 商品内顺序唯一 |
| `payment_transaction` | 23 | Mock/微信支付尝试与幂等结果 | 支付单号、订单幂等键唯一 |
| `voucher_refund` | 24 | 单券退款申请和结果 | 退款单号唯一；券级活动记录；`description` 保存消费者退款说明 |
| `voucher_redemption` | 26、31 | 核销、按次使用和撤销记录 | 核销幂等键唯一；记录门店和操作人快照；不保存线下消费金额 |
| `commission_rule` | 28 | 平台默认及门店覆盖佣金 | 生效区间内规则不可重叠 |
| `fund_ledger_entry` | 28、31 | 冻结、线上订单核销确认、佣金、退款和调整流水 | 业务事件加分录类型唯一；只追加不更新；不接收线下微信支付 |
| `settlement_batch` | 29、31 | T+1 结算批次 | 结算日和门店唯一；每日 02:00 幂等生成 |
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
- `purpose` 允许 `LICENSE`（营业执照）、`GALLERY`（经营图片）、`VOUCHER_COVER`（券封面）、`VOUCHER_DETAIL`（券详情图）与 `MERCHANT_AVATAR`（商户头像）；阶段 18 只开放前两种，阶段 34 开放商户头像。`status` 允许 `TEMPORARY`（临时）、`BOUND`（已绑定）、`DELETED`（已删除）；临时记录不得带业务归属，已绑定记录必须同时具备归属类型与 ID。

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
- 新增唯一索引 `uk_shop_source_application(source_application_id)`、列表索引 `idx_shop_status_city_type(status,city_code,type_id,id)` 与地域索引 `idx_shop_status_city_district(status,city_code,district_code,id)`；既有 `images` 只保留消费者摘要，不保存证照或审核治理事实。

`merchant_account` 新增选择性恢复事实：

- `disabled_source` 只允许 `SHOP_SUSPENSION`（门店停用联动）、`ACCOUNT_GOVERNANCE`（平台账号治理）、`STAFF_MANAGEMENT`（员工管理）或空。
- `disabled_reason` 最多 500 字，`disabled_at` 保存停用时间；非 `DISABLED`（已停用）账号必须清空三项。
- 门店停用只条件更新当前 `ACTIVE`（已激活）账号并写 `SHOP_SUSPENSION`；门店恢复只条件恢复该来源账号，其他来源保持停用。

阶段 19 种子必须至少包含一个 `PENDING`（审核中）申请、一个 `REJECTED`（审核未通过）申请、可审核的绑定证照，以及可执行停用/恢复的活动门店和租户。审核与治理数据库测试结束后必须重新执行完整快照，恢复相同的 38 表纯种子状态。

### 阶段 20 字段冻结

阶段 20 直接重构 `voucher_product` 并新增统一的详情、标签与券型规则表，当前快照由 24 表扩展为 38 表。完整字段、索引、媒体同步、状态约束和种子规则以 [阶段 20 详细设计](./docs/stages/STAGE_20_VOUCHER_AUTHORING.md) 为真源。

- `voucher_product` 删除 `cover`、`rules`、`pay_price`、`original_price`、`deduction_value`、`sale_type` 和旧单一 `status`，改为四类券专属字段、结构化时间/使用规则、`review_status`（审核状态）与 `sale_status`（销售状态）。
- 商品索引固定为 `idx_voucher_product_shop_review(shop_id,review_status,update_time,id)`、`idx_voucher_product_public(shop_id,review_status,sale_status,sale_begin_time,sale_end_time,id)` 与 `idx_voucher_product_submission(submission_idempotency_key)`。
- `voucher_product_detail` 保存所有券型的商户编写详情分段，以 `product_id,sort_order` 唯一；`voucher_product_tag` 保存统一图标键和标签文本。
- `voucher_product_cash_rule`、`voucher_product_discount_rule`、`voucher_product_multi_use_rule` 分别保存代金券、折扣券和次卡的专属权益说明；折扣规则只用于展示和核销说明，不参与订单计价。
- 券媒体保存即绑定 `VOUCHER_PRODUCT`（券商品），封面恰好一张、详情图最多九张；复制创建独立对象键，删除在事务提交后清理。
- 既有商品 3001/3002 直接转换为已审核在售种子，保持订单、库存、销量与消费者 Demo；新增四类草稿和一个审核中商品。阶段 20 数据库级测试已验证媒体绑定、版本冲突、角色隔离、提交幂等和 38 表快照，并在结束后恢复纯种子状态。

### 阶段 34 商户账号资料字段

阶段 34 账号资料不新增业务表；阶段 35 新增客服三表，业务表总数为 42：

- `merchant_account` 新增非空 `password_hash` 保存 BCrypt 摘要，新增可空 `avatar_media_id` 作为对 `business_media_asset.id` 的应用层逻辑关联；旧 `avatar_url` 不再保留。
- 开发种子中的全部商户账号密码统一为 `Roamly123`；该快照只允许在明确授权可重建的 Demo 数据库执行，不作为保留数据环境的迁移脚本。
- `business_media_asset.purpose` 增加 `MERCHANT_AVATAR`，绑定后的 `owner_type` 为 `MERCHANT_ACCOUNT`、`owner_id` 为商户账号 ID；替换头像时旧媒体先标记删除，事务提交后清理对象。
- 手机号继续由 `uk_merchant_account_phone` 保证唯一。手机号或密码修改锁定并条件更新账号、递增版本，并在提交后注销该账号全部 `MERCHANT` 会话。

### 目标重构表

- `shop`：使用字符串经营状态，保存入驻申请来源、激活/停用时间和停用原因；经营媒体转为 `business_media_asset`，不再以逗号字符串扩展新能力。
- `admin_user`：保存不可变用户名、密码摘要、显示名、固定角色、启停状态、强制改密标记、最后登录时间、创建人、版本和创建/更新时间；不引入若依 `sys_user`、`sys_role`、`sys_menu` 或关联表。
- `voucher_product`：直接使用四种券型、审核状态、销售状态、结构化有效期/时段、退款规则、库存、限购及乐观锁字段；删除含义模糊的旧字段。
- `voucher_order`：使用字符串订单状态，新增 `payment_expire_time`、用户级幂等键与请求指纹，并保存商品/门店/价格快照；支付状态与履约状态在后续阶段继续分离。
- `user_voucher`：按订单数量逐份生成，以 `order_id,sequence_no` 组合唯一；保存加密券码、HMAC 索引、规则/价格快照、总次数、剩余次数和履约状态。

### 目标业务枚举

- 管理角色：`PLATFORM_ADMIN`（平台超级管理员）、`MERCHANT_REVIEWER`（商户审核员）、`FINANCE`（财务管理员）。
- 管理员账号状态：`ACTIVE`（已启用）、`DISABLED`（已停用）。
- 商户角色：`VISITOR`（游客）、`TENANT`（租户）、`MANAGER`（店长）、`VERIFIER`（核销员）。
- 商户账号状态：`NOT_APPLIED`（未入驻）、`PENDING`（审核中）、`ACTIVE`（已激活）、`REJECTED`（审核未通过）、`DISABLED`（已停用）。
- 门店经营状态：`PENDING`（待激活）、`ACTIVE`（营业中）、`SUSPENDED`（已停用）、`CLOSED`（已关闭）。
- 券型：`PACKAGE`（套餐券）、`CASH`（代金券）、`DISCOUNT`（折扣券，仅核销）、`MULTI_USE`（次卡）。
- 券审核状态：`DRAFT`（草稿）、`PENDING`（审核中）、`APPROVED`（审核通过）、`REJECTED`（审核未通过）。
- 券销售状态：`SCHEDULED`（待开售）、`ON_SALE`（销售中）、`OFF_SALE`（已下架）、`SOLD_OUT`（已售罄）、`ENDED`（已结束）。
- 订单状态：`PENDING_PAYMENT`（待支付）、`PAID`（已支付）、`CANCELED`（已取消）、`REFUNDING`（退款中）、`REFUNDED`（已退款）。
- 支付状态：`PENDING`（待支付）、`SUCCEEDED`（支付成功）、`FAILED`（支付失败）、`CLOSED`（已关闭）、`PARTIALLY_REFUNDED`（部分退款）、`REFUNDED`（已退款）。
- 用户券状态：`UNUSED`（未使用）、`PARTIALLY_USED`（部分使用）、`USED`（已使用）、`EXPIRED`（已过期）、`REFUNDING`（退款中）、`REFUNDED`（已退款）。
- 退款状态：`REQUESTED`（已申请）、`PROCESSING`（处理中）、`SUCCEEDED`（退款成功）、`FAILED`（退款失败）、`REJECTED`（退款被拒）。
- 结算金额状态：`FROZEN`（冻结中）、`SETTLEABLE`（待结算）、`SETTLED`（已结算）、`ADJUSTMENT`（调整项）。
- 退款审核状态：`PENDING_REVIEW/AUTO_APPROVED/MANUAL_APPROVED/REJECTED`；渠道执行状态：`WAITING_EXECUTION/PROCESSING/SUCCESS/PARTIAL_SUCCESS/FAILED/RETRY_WAITING/MANUAL_REQUIRED`。被拒绝申请兼容使用 `NOT_STARTED` 表示未进入渠道。
- 账本事件：`PAYMENT_FROZEN`、`REDEMPTION_RECOGNIZED`、`SERVICE_FEE_RECOGNIZED`、`REFUND_REVERSED`、`REFUND_REVENUE_REVERSED`、`SERVICE_FEE_REVERSED`、`SERVICE_FEE_REFUNDED`、`REDEMPTION_REVERSED`、`SETTLEMENT_POSTED`、`SETTLEMENT_ADJUSTMENT`。

### 目标一致性

- 待支付订单占用库存；15 分钟关单通过条件更新只返库一次；支付成功后销量按数量累计并逐份发券。
- 手输券码只通过 HMAC 索引定位，固定二维码在券有效期内保持不变；核销预览不改变状态，确认核销使用唯一幂等键。
- 次卡每次核销扣减一次，最后一次转为已使用；核销撤销必须在进入结算前完成并追加反向记录。
- 支付成功记冻结账本；核销时按（商品售价 - 商家补贴）×生效服务费率计算软件服务费，平台优惠真实参与顾客实付；次日 02:00 按门店生成幂等 T+1 Mock 结算批次和明细。到店额外消费由顾客和商户线下微信支付，不进入任何平台表。
- 所有资金表只追加事实或显式状态迁移，不覆盖历史金额；已结算退款通过负向调整进入后续结算。

### 设计冻结门禁

目标数据库必须先在对应阶段文档中完成表、字段、唯一约束、索引、状态机、事务边界和测试数据设计。冻结后改变任一数据库公共事实时，必须先解冻并升级设计版本；不得先改 DDL 再补文档。

## 集成验收

`DatabaseBusinessClosureIntegrationTest` 仅在 `RUN_DATABASE_INTEGRATION_TESTS=true` 时运行。它在开始前重建当前快照，使用 Redis DB 15，完成真实 HTTP/Service/SQL 场景后再次重建种子并清空测试 Redis，确保日常开发环境回到纯种子状态。

2026-09-05 已在 `.env` 当前指向且获授权的开发库完成 38 表 Demo 快照验收：

- 当次完整执行 `schema-init.sql` 与 `seed-dev.sql`，确认 43 张业务表、关键唯一索引、旧表退役和种子一致性。
- `DatabaseBusinessClosureIntegrationTest` 9 项全部通过，覆盖商户登录/限流/五种状态/首次建号/停用会话/三域隔离、管理员账号与审计、入驻媒体、申请审核、门店停用与选择性恢复、四类券建券/媒体/复制/提交/角色隔离，以及社区、点评、订单、支付、退款、员工、核销、账本、结算和用户隔离。
- 快照断言确认 43 张业务表全部非空，并覆盖三端推荐账号、客服账号、附加权限账号、四类券、五种订单状态、六种支付状态、六种用户券状态、五种退款状态、邀请/核销/账本/结算状态及四条代表性跨表业务链路。
- `OpenApiAndAuthRuntimeTest` 8 项全部通过，确认运行时 OpenAPI 147 个唯一 `operationId`、阶段 23-29 新增 Schema、全部 `$ref`、Bearer 声明和关键错误响应。
- 后端默认 `mvn test` 共执行 172 项，其中 150 项通过、22 项按环境开关跳过，0 失败、0 错误；默认测试未重建数据库。
- 测试结束后再次重建快照并恢复纯种子数据，Redis DB 15 已清空，不保留测试期间生成的业务数据或登录状态。
- 阶段 21 不新增业务表；阶段 22 仍不新增业务表，仅直接重构 `voucher_order` 字段与索引；阶段 23 至 29 新增表已随本快照完成重建和集成验证。
