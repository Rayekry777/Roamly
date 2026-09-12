# 阶段 40：平台统一客服后端

```yaml
status: 已实现
updatedAt: 2026-09-12
scope: 后端、SQL、权限、OpenAPI、单元测试与开发数据
databaseMode: 直接维护 schema-init.sql，不使用 Flyway
paymentMode: MOCK，与客服状态无耦合
```

## 目标与边界

本阶段把原有三张表和基础消息接口升级为平台统一客服后端。业务拓扑固定为“消费者 → 平台客服”和“商户 → 平台客服”，不建设商户客服直接接待消费者的模型。管理端在线、WebSocket 或 SSE 连接只可通知刷新，不得认领工单或改变主状态。

本阶段不实现阶段 41 的三端完整页面，只冻结并实现页面所需的后端能力。退款审批仍由退款服务完成，客服只能在权限校验后查看关联 ID 和状态，不能借助客服接口审批或执行退款。

## 权限模型

- 工单访问真源为 `applicant_type + applicant_id`。
- 消费者查询条件固定为 `applicant_type='CONSUMER' AND applicant_id=当前消费者ID`。
- 商户查询条件固定为 `applicant_type='MERCHANT' AND applicant_id=当前商户账号ID`；禁止仅按 `shop_id` 查询。
- `related_user_id`、`related_shop_id`、订单、用户券、退款和核销 ID 只提供经过归属校验的业务上下文，不能扩大工单可见范围。
- 平台查看使用 `admin:customer-service:read`；认领、回复、状态、转交、标签和快捷回复维护使用 `admin:customer-service:manage`。
- 非平台超级管理员只能回复、变更或转交自己负责的工单；平台超级管理员保留治理能力。
- 消费者和商户只能读取 `PUBLIC` 消息及其附件；`INTERNAL` 内部备注和系统操作消息只对平台管理端可见。

## 状态机

状态固定为：

```text
OPEN
  ├── CLAIMED
  └── CLOSED

CLAIMED
  ├── WAITING_CUSTOMER
  ├── WAITING_MERCHANT
  ├── WAITING_INTERNAL
  └── RESOLVED

WAITING_CUSTOMER / WAITING_MERCHANT / WAITING_INTERNAL
  ├── CLAIMED
  └── CLOSED

RESOLVED
  ├── CLAIMED
  └── CLOSED
```

`CLOSED` 是普通业务接口的终态。消费者或商户在等待本人时发送消息，会把已认领工单唤醒为 `CLAIMED`；已解决工单在可继续沟通时也回到 `CLAIMED`。内部备注只设置 `has_internal_note=true` 并更新消息时间，不改变主状态。

认领使用单条条件更新：

```sql
UPDATE customer_service_ticket
SET assignee_admin_id = ?, status = 'CLAIMED', version = version + 1
WHERE id = ? AND assignee_admin_id IS NULL AND status = 'OPEN';
```

受影响行数不是 1 时返回冲突，因此两个客服并发认领只有一个成功。

## 消息、未读与附件

消息查询参数为 `before_message_id`、`after_message_id` 和 `limit`；前后游标不能同时提交。服务端稳定按消息 ID 排序，批次返回 `oldestMessageId`、`newestMessageId` 和 `hasMore`。

每个消费者、商户账号和管理员分别保存已读游标。数据库通过 `ticket_id,reader_type,reader_id` 唯一约束和 `GREATEST` 更新保证游标只前进；未读数量按当前角色可见消息计算，并排除本人发送的消息。

附件复用现有 `ObjectStoragePort`：

1. 在有权访问的工单内上传图片，生成 `TEMPORARY` 记录和私有对象键；
2. 消息提交时用附件 ID、工单 ID、上传人和未过期条件绑定为 `BOUND`；
3. 读取时再次校验工单归属、附件所属工单和消息可见性；
4. 只有上传者可删除未绑定附件；
5. 24 小时未绑定附件由定时任务标记删除并清理 Local/S3 私有对象。

跨工单读取、读取他人临时附件、消费者/商户读取内部备注附件均返回不存在，避免泄露目标是否存在。

## 标签、转交、快捷回复与 SLA

- 标签字典和工单标签关系分别持久化；替换标签时锁定工单、校验标签启用状态并防止重复关系。
- 转交先锁定工单，校验目标管理员为启用的客服或平台管理员，再把状态归一为 `CLAIMED` 并追加 `customer_service_transfer`。
- 快捷回复支持 `PERSONAL` 和 `TEAM`；普通客服只维护本人模板，团队模板只由平台超级管理员维护。
- 工单保存首次响应、最近响应、等待消费者/商户起点、解决和关闭时间、SLA 截止时间及超时标志。
- 默认队列按“已超时 → 优先级 → SLA 截止时间 → 最近消息”排序；定时任务刷新超时标志。
- 认领、公开回复、内部备注、状态变更、转交、标签和快捷回复变更均写事务后管理审计，不记录消息正文、附件内容或敏感凭据。

## 数据库变化

原三张表扩展：

- `customer_service_ticket`：增加申请人、关联上下文、响应/等待时间、SLA 和内部备注标志；保留旧 `user_id/shop_id` 兼容字段，但新权限查询禁止使用。
- `customer_service_attachment`：增加 `TEMPORARY/BOUND/DELETED` 生命周期、桶名、过期/绑定/删除时间和工单隔离索引。
- `customer_service_message`：继续以 `visibility` 区分公开消息和内部备注。

新增五张表：

- `customer_service_read_cursor`
- `customer_service_tag`
- `customer_service_ticket_tag`
- `customer_service_transfer`
- `customer_service_quick_reply`

业务表从 46 张增加为 51 张。没有物理外键，逻辑关系由服务事务和唯一约束维护。新环境直接使用更新后的 `schema-init.sql`；已有开发库执行顺序、回填和前置检查记录在 `DATABASE_SCHEMA.md`，本阶段未连接或重建未知数据库。

## HTTP 与 OpenAPI 变化

阶段 40 保留原工单路径，并新增：

- 消费者、商户、管理端工单消息游标接口；
- 商户工单详情和回复接口；
- 三端附件上传、私有读取和临时删除接口；
- 管理端转交、转交历史、标签替换和标签字典接口；
- 管理端快捷回复查询、创建和删除接口。

所有路径 ID、关联业务 ID、附件 ID、标签 ID、转交 ID 和消息 ID 在 HTTP/OpenAPI 中统一为字符串，服务内部才转换为 `Long`。源码共有 210 个唯一 `operationId`；阶段 40 新增 21 个客服操作，并通过不依赖数据库的 Controller 映射测试发现及补齐原契约遗漏的定位上下文路径。

## 风险与延后项

- 默认测试不连接真实 MySQL/Redis；真实 `FOR UPDATE`、唯一约束、游标 upsert 和 `/v3/api-docs` 运行时验证保留到阶段 42 的隔离环境。
- 阶段 40 使用当前 MyBatis-Plus 条件更新实现认领，不引入消息队列、搜索引擎或外部客服产品。
- 当前附件只开放 JPEG、PNG、WebP 图片；通用文档附件、病毒扫描和 CDN 签名 URL 属于生产适配器范围。
- 阶段 41 仍需实现管理端三栏工作台、消费者/商户会话页面和前端未读体验。

## 工作流记录

```text
阶段：40
目标：建立平台统一客服后端闭环
实际完成：申请人隔离、关联归属校验、原子认领、状态白名单、消息游标、未读、附件、标签、转交、快捷回复、SLA 和审计
未完成：真实 MySQL/Redis 与 /v3/api-docs 运行时复验延后至阶段42；三端页面属于阶段41
源码变更：重构客服 Service/Controller/DTO/VO/Entity/Mapper，新增附件服务和五类客服实体/Mapper
数据库变更：扩展客服工单与附件，新增5张客服表，业务表总数51
接口变更：新增21个客服操作，源码唯一 operationId 210；客服相关业务ID全部改为字符串
权限变更：消费者/商户按 applicant_type+applicant_id 隔离；平台读写权限分离；处理人和平台管理员门禁
前端变更：无，阶段41统一联调和页面重构
日志和注释：补充认领、状态、附件清理日志，Service/Mapper JavaDoc，敏感操作事务后审计
文档变更：BACKEND_DEVELOPMENT.md、DATABASE_SCHEMA.md、本阶段文档、四端路线图
测试命令：mvn -pl ray-server -am "-Dtest=CustomerServiceServiceImplTest,CustomerServiceAttachmentServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test；mvn test
测试结果：客服专项8/8；全量245项中223通过、22项按既有环境开关跳过、0失败
构建结果：后端 compile 与 test 生命周期通过
风险：真实数据库并发和运行时 OpenAPI 尚未在隔离环境启用；未知开发数据库未执行结构变更
下一阶段前置条件：阶段41前端只消费权威状态和消息接口，断线重连不得写业务状态
Git 提交：feat(customer-service): 完善平台客服权限与工单闭环
```
