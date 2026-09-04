# 阶段 19：商户审核与门店治理

```yaml
designVersion: 2
designStatus: 已冻结
implementationStatus: 未实现
dependsOn: 阶段 18 已实现
affectedEnds: 后端、管理 Web
```

## 目标与边界

本阶段交付平台商户申请审核、审核资料私有读取、门店激活、停用和恢复，形成“店主提交申请 -> 管理员审核 -> 门店营业 -> 店主获得经营权限 -> 平台治理”的事务闭环。

- 后端负责申请查询、敏感资料投影、审核事务、门店治理、账号联动、权限、审计、幂等与并发控制。
- 管理 Web 负责申请队列、申请详情、私有图片预览、审核命令、门店列表、门店详情和治理命令。
- 商户小程序不新增页面或接口；阶段 17 的当前身份和阶段 18 的申请读取会自然观察审核结果。
- 本阶段不实现门店永久关闭、申请重新提交、独立商户账号治理、券上下架、员工管理、资金或结算。
- 审核通过默认创建并直接激活门店，不增加人工二次激活步骤。

## 进入条件

- 阶段 18 已实现并通过真实数据库、运行时 HTTP/OpenAPI、私有经营媒体和商户小程序验收。
- `merchant_application`（商户申请）、`business_media_asset`（经营媒体）、`merchant_account`（商户账号）和对象存储均可用。
- `ADMIN`（管理端）认证域、固定角色、权限校验和事务感知 `operation_audit_log`（操作审计日志）已经实现。
- 开发库仍采用可直接覆盖的 Demo 快照；本阶段不新增业务表，完成后仍为 24 张业务表。

## 角色与权限

| 能力 | 权限码 | 允许角色 |
|---|---|---|
| 申请列表、详情、私有媒体与审核 | `admin:merchant-application:review`（商户申请审核） | `PLATFORM_ADMIN`（平台超级管理员）、`MERCHANT_REVIEWER`（商户审核员） |
| 门店列表、详情、停用与恢复 | `admin:shop:govern`（门店治理） | `PLATFORM_ADMIN`（平台超级管理员）、`MERCHANT_REVIEWER`（商户审核员） |

`FINANCE`（财务管理员）、消费者 Token、商户 Token 和未登录请求均不得调用；前端菜单与按钮隐藏不能替代服务端授权。

## 状态机

### 商户申请

- `PENDING`（审核中） -> `APPROVED`（审核通过）。
- `PENDING`（审核中） -> `REJECTED`（审核未通过）。
- `DRAFT`（草稿）、`APPROVED`（审核通过）和 `REJECTED`（审核未通过）不能再次执行阶段 19 审核命令。
- 首个成功事务取得唯一决定权；其他幂等键或过期版本不得覆盖审核人、决定、原因或门店。

### 门店经营

- `PENDING`（待激活） -> `ACTIVE`（营业中）。
- `ACTIVE`（营业中） -> `SUSPENDED`（已停用）。
- `SUSPENDED`（已停用） -> `ACTIVE`（营业中）。
- `CLOSED`（已关闭）为保留终态，本阶段无关闭接口，也不能由恢复命令重新激活。
- 审核通过新建门店时直接写入 `ACTIVE`（营业中），记录激活时间和来源申请。

### 商户账号联动

- 审核通过把申请账号设置为 `OWNER`（店主）、绑定新门店并迁移为 `ACTIVE`（已激活）。
- 门店停用只把该门店当前 `ACTIVE`（已激活）账号改为 `DISABLED`（已停用），同时写入 `SHOP_SUSPENSION`（门店停用联动）来源、原因和时间。
- 门店恢复只恢复仍为 `DISABLED` 且 `disabled_source=SHOP_SUSPENSION` 的账号，清空停用来源、原因和时间。
- `ACCOUNT_GOVERNANCE`（平台账号治理）与 `STAFF_MANAGEMENT`（员工管理）导致的停用不会被门店恢复误激活。
- 停用或恢复后注销受影响账号已有商户会话，并失效商户身份缓存；已下架商品不自动恢复。

## 数据设计

### `merchant_application`（商户申请）

在阶段 18 快照基础上新增或收紧以下事实：

| 字段 | 规则 |
|---|---|
| `review_decision` | 可空；只允许 `APPROVAL`（通过）或 `REJECTION`（驳回） |
| `review_idempotency_key` | 可空；首个成功审核命令的 8 至 128 字符幂等键 |
| `review_request_fingerprint` | 可空；64 位小写十六进制 SHA-256 请求指纹 |
| `reviewer_admin_id` | 成功审核管理员 ID |
| `reviewed_at` | 成功审核时间 |
| `rejection_reason` | 仅驳回时保存规范化原因；通过时为空 |
| `approved_shop_id` | 仅通过时保存门店 ID，非空值必须唯一 |
| `version` | 审核条件更新成功后自增 1 |

新增唯一索引 `uk_merchant_application_approved_shop(approved_shop_id)`；保留账号唯一与审核队列索引。数据库约束或服务端事务必须保证决定、原因、审核人和门店之间的组合一致。

审核请求指纹固定为 UTF-8 文本经 SHA-256 后的小写十六进制值：

```text
动作\n申请ID\n期望版本\n规范化原因
```

通过动作使用 `APPROVAL` 且原因为空字符串；驳回动作使用 `REJECTION` 和去首尾空格后的原因。同一幂等键且指纹相同返回已提交结果；同一幂等键但指纹不同返回 409。

### `shop`（门店）

本阶段允许直接重构旧 Demo 字段，不保留数字状态兼容层。新增或调整：

| 字段 | 规则 |
|---|---|
| `status` | `PENDING`（待激活）、`ACTIVE`（营业中）、`SUSPENDED`（已停用）、`CLOSED`（已关闭） |
| `source_application_id` | 审核通过来源申请，非空且唯一 |
| `business_hours_json` | 审核通过时复制申请的七日营业时间快照 |
| `activated_at` | 首次激活时间；恢复不覆盖 |
| `suspended_at` | 最近一次停用时间，恢复后清空 |
| `suspension_reason` | 最近一次停用原因，恢复后清空 |
| `status_changed_by_admin_id` | 最近一次治理管理员 ID；审核创建时可空 |
| `status_command_type` | 最近一次命令：`SUSPENSION`（停用）或 `ACTIVATION`（恢复） |
| `status_idempotency_key` | 最近一次成功治理命令幂等键 |
| `status_request_fingerprint` | 最近一次成功治理命令 SHA-256 指纹 |
| `version` | 每次成功治理命令自增 1 |

新增唯一索引 `uk_shop_source_application(source_application_id)`，列表索引覆盖 `status,city_code,shop_type_id,id`。既有 `images` 仅作为消费者门店摘要兼容字段保留，不写入营业执照、审核决定或治理事实。

门店治理指纹格式与审核一致，动作为 `SUSPENSION` 或 `ACTIVATION`，对象 ID 为门店 ID，原因均为去首尾空格后的文本。表只保存最近一次治理命令；相同幂等键重放相同指纹时返回当前命令结果，不重复停用账号、注销会话或写审计。

### `merchant_account`（商户账号）

新增：

| 字段 | 规则 |
|---|---|
| `disabled_source` | `SHOP_SUSPENSION`（门店停用联动）、`ACCOUNT_GOVERNANCE`（平台账号治理）或 `STAFF_MANAGEMENT`（员工管理） |
| `disabled_reason` | 规范化停用原因，最多 500 字 |
| `disabled_at` | 停用时间 |

账号为 `DISABLED`（已停用）时允许三字段表达停用来源；其他状态必须清空。阶段 19 只写 `SHOP_SUSPENSION`。

### 媒体读取

- 管理员详情只返回媒体元数据与本阶段专用鉴权内容地址，不返回 bucket、对象键或永久公网 URL。
- `GET /v1/admin/merchant-applications/{applicationId}/media/{mediaId}/content` 仅能读取属于该申请的 `BOUND`（已绑定）营业执照或经营图片。
- 媒体归属、申请归属和审核权限任一不满足时不读取对象；跨申请媒体统一返回资源不存在，不泄露对象是否存在。
- 每次成功查看申请敏感详情记录 `MERCHANT_APPLICATION_SENSITIVE_VIEWED`（查看商户申请敏感资料）审计；同一次详情读取不因前端随后加载多张图片重复写敏感详情审计。

## HTTP 接口

所有接口使用 `ADMIN`（管理端）Bearer Token、真实 HTTP 状态、字符串 ID 及现有 `Result`/`PageResult`/`ErrorResult`。本阶段新增 9 个操作，运行时唯一 `operationId` 由 77 增至 86。

### 申请查询

`GET /v1/admin/merchant-applications`

- 查询参数：`status`、`cityCode`、字符串 `shopTypeId`、精确 `phone`、`submittedFrom`、`submittedTo`、`page`、`size`。
- 时间使用 ISO-8601 日期时间，`submittedFrom <= submittedTo`。
- `page>=1`，`1<=size<=100`，默认 1/20。
- 默认按 `submitted_at DESC,id DESC`；列表只返回联系人脱敏手机号和结算账号后四位，不返回营业执照号码全文、法定代表人或媒体存储信息。

`GET /v1/admin/merchant-applications/{applicationId}`

- 返回 `MerchantApplicationReviewDetailVO`，包含审核摘要、主体证照、门店地址、七日营业时间、经营媒体、Mock 结算摘要和审核记录。
- 联系电话与营业执照号码按审核白名单完整返回；结算仍只返回开户名、银行和后四位，不存在完整银行卡字段。

`GET /v1/admin/merchant-applications/{applicationId}/media/{mediaId}/content`

- 成功返回原始图片字节、真实图片 Content-Type、私有缓存头和内容长度，不包装 `Result`。
- 对象存储失败返回 503 `OBJECT_STORAGE_UNAVAILABLE`（对象存储不可用）。

### 审核命令

`POST /v1/admin/merchant-applications/{applicationId}/approval`

```json
{"version": 1}
```

`POST /v1/admin/merchant-applications/{applicationId}/rejection`

```json
{"version": 1, "reason": "经营地址资料不清晰"}
```

- 两个命令均要求 `Idempotency-Key`，长度 8 至 128 字符。
- `version` 为不小于 0 的整数；驳回原因去首尾空格后 1 至 500 字。
- 成功返回 `MerchantApplicationReviewResultVO`，包含申请 ID、申请状态、决定、审核人、审核时间、最新版本及可空门店摘要。

### 门店查询与治理

`GET /v1/admin/shops`

- 查询参数：`status`、`cityCode`、字符串 `shopTypeId`、`keyword`、`page`、`size`。
- `keyword` 去首尾空格后匹配门店名、联系人或联系电话；分页规则同申请列表。
- 默认按 `update_time DESC,id DESC`。

`GET /v1/admin/shops/{shopId}`

- 返回 `AdminShopDetailVO`，包含门店、来源申请、店主、类目、城市、地址、营业时间、账号状态汇总及最近治理信息。

`POST /v1/admin/shops/{shopId}/suspension`

`POST /v1/admin/shops/{shopId}/activation`

```json
{"version": 0, "reason": "平台治理复核"}
```

- 两个命令均要求 `Idempotency-Key`，长度 8 至 128 字符。
- 原因去首尾空格后 1 至 500 字；`version` 为不小于 0 的整数。
- 成功返回 `AdminShopGovernanceResultVO`，包含门店 ID、最新经营状态、版本、原因、操作人、操作时间和本次联动账号数。

## DTO 与 VO

- 查询 DTO 使用不可变对象表达筛选、分页和时间范围，并在 Controller 边界执行枚举、长度和区间校验。
- 命令 DTO：`MerchantApplicationApprovalRequest(version)`、`MerchantApplicationRejectionRequest(version,reason)`、`ShopGovernanceRequest(version,reason)`。
- 列表 VO：`MerchantApplicationReviewListItemVO`、`AdminShopListItemVO`。
- 详情 VO：`MerchantApplicationReviewDetailVO`、`AdminShopDetailVO`。
- 命令结果 VO：`MerchantApplicationReviewResultVO`、`AdminShopGovernanceResultVO`。
- 嵌套结构必须定义稳定 Schema，不使用裸 `Map` 或 `JsonNode`；全部业务 ID 对外为字符串。

## 事务与并发

### 审核通过

单事务依次完成：

1. 按申请 ID 加锁读取并校验状态、版本、申请完整性和媒体绑定事实。
2. 校验幂等键与请求指纹；相同重放返回既有结果，冲突重用返回 409。
3. 创建唯一来源申请门店，复制申请、坐标、营业时间、聚合初值与展示图片摘要。
4. 条件更新申请为 `APPROVED`（审核通过），保存决定、审核人、时间、门店、幂等键、指纹并递增版本。
5. 条件更新申请账号为 `OWNER`（店主）、绑定门店、迁移为 `ACTIVE`（已激活）并清空停用信息。
6. 写入一次成功审核审计；事务提交后注销该账号旧商户会话并失效身份缓存。

唯一索引、行锁和条件更新共同防止并发创建两家门店。任何数据库步骤失败时申请、门店、账号与审计全部回滚；提交后会话清理失败只能记录告警，不回滚已提交事实，后续鉴权仍必须实时校验账号状态。

### 审核驳回

按申请行加锁，校验幂等、状态和版本，条件更新申请为 `REJECTED`（审核未通过）并保存原因；账号迁移为 `REJECTED`（审核未通过）但不绑定门店。申请、账号和审计同事务提交，提交后注销旧商户会话并失效缓存。

### 门店停用与恢复

- 按门店行加锁，先处理相同幂等键重放，再校验期望版本和合法迁移。
- 停用门店后批量条件更新当前 `ACTIVE` 账号并记录 `SHOP_SUSPENSION`；恢复只条件更新对应来源的 `DISABLED` 账号。
- 门店、账号和审计同事务；提交后注销受影响账号会话并失效门店、账号缓存。
- 不允许用新的幂等键重复执行当前已达状态，返回 `SHOP_STATUS_CONFLICT`（门店经营状态冲突）。

## 审计

审计动作固定为：

- `MERCHANT_APPLICATION_SENSITIVE_VIEWED`（查看商户申请敏感资料）。
- `MERCHANT_APPLICATION_APPROVED`（商户申请审核通过）。
- `MERCHANT_APPLICATION_REJECTED`（商户申请审核驳回）。
- `SHOP_SUSPENDED`（门店停用）。
- `SHOP_ACTIVATED`（门店恢复营业）。

成功命令审计保存操作者、对象、幂等键、版本前后值、原因摘要和联动账号数，不记录完整营业执照号码、联系电话、密码、Token、对象键或结算信息。失败请求不伪造成功审计。

## 错误契约

| HTTP | 错误码 | 中文释义 |
|---:|---|---|
| 404 | `MERCHANT_APPLICATION_NOT_FOUND` | 商户申请不存在 |
| 409 | `MERCHANT_APPLICATION_ALREADY_REVIEWED` | 商户申请已审核 |
| 409 | `MERCHANT_APPLICATION_REVIEW_VERSION_CONFLICT` | 商户申请审核版本冲突 |
| 409 | `MERCHANT_APPLICATION_REVIEW_IDEMPOTENCY_CONFLICT` | 商户申请审核幂等键冲突 |
| 404 | `SHOP_NOT_FOUND` | 门店不存在 |
| 409 | `SHOP_STATUS_CONFLICT` | 门店经营状态冲突 |
| 409 | `SHOP_VERSION_CONFLICT` | 门店版本冲突 |
| 409 | `SHOP_GOVERNANCE_IDEMPOTENCY_CONFLICT` | 门店治理幂等键冲突 |
| 403 | `MERCHANT_SHOP_SUSPENDED` | 所属门店已停用 |
| 503 | `OBJECT_STORAGE_UNAVAILABLE` | 对象存储不可用 |

认证、权限、参数和通用错误继续使用既有 400、401、403、404、409、429、500、503 契约。

## 管理 Web 冻结设计

### `/merchant-applications`（商户申请）

- 顶部为紧凑筛选区，包含状态、城市、类目、精确手机号、提交时间和重置/查询命令；下方为分页表格。
- 表格展示申请编号、门店名、类目、城市、联系人、脱敏手机号、提交时间、状态和操作，不在列表下载私有图片。
- 详情使用最大 760px 抽屉，按审核摘要、主体证照、门店地址、七日营业时间、经营图片、Mock 结算和审核记录分区。
- 私有图片由带 `ADMIN` Token 的 Blob 请求加载；抽屉关闭、切换申请和组件卸载时调用 `URL.revokeObjectURL`。
- 仅 `PENDING`（审核中）显示通过和驳回；通过确认明确“通过后立即激活门店”，驳回必须填写 1 至 500 字原因。
- 命令发送稳定 `Idempotency-Key`，提交期间禁用重复点击。409 保留用户输入，刷新详情并提示重新核对服务端状态。

### `/shops`（门店治理）

- 筛选状态、城市、类目和关键词；表格展示门店、店主、类目、城市、账号摘要、经营状态、最近变更和停用原因。
- 详情抽屉展示来源申请、地址、七日营业时间、店主、账号状态计数和治理记录。
- 仅 `ACTIVE`（营业中）显示停用，仅 `SUSPENDED`（已停用）显示恢复；两者均要求原因和确认。
- 409 保留原因，刷新门店详情和列表；恢复提示不会自动恢复后续阶段已下架商品。

### 响应式与视觉

- 沿用 Roamly 珊瑚红、紫色辅助、紫灰文字、浅灰背景和白色内容面；不得引入若依默认蓝色或竞品视觉。
- 1440x900 与 1280x720 使用紧凑筛选、固定操作列和 760px 抽屉。
- 390x844 使用纵向筛选、可横向滚动表格和全屏抽屉；弹窗按钮、原因输入和长状态文案不得重叠。
- 所有状态、权限、错误和按钮面向用户显示中文；技术枚举仅作为内部值。

## 测试矩阵

### 后端单元与集成

- 两角色允许、`FINANCE`（财务管理员）拒绝、三类 Token 隔离、未登录 401。
- 申请列表筛选、分页边界、时间区间、手机号精确匹配和默认脱敏。
- 详情白名单投影、跨申请媒体拒绝、绑定媒体读取、对象存储 503 和敏感查看审计。
- 通过事务创建唯一门店、复制快照、激活店主、递增版本并写审计。
- 驳回事务保存规范化原因、迁移账号状态、递增版本并写审计。
- 同键同指纹重放、同键异指纹冲突、不同键重复决定、旧版本冲突和并发审核只成功一次。
- 审核任一步异常时申请、门店、账号和审计全部回滚。
- 停用只影响当前活动账号；恢复只恢复 `SHOP_SUSPENSION` 来源账号；其他停用来源保持不变。
- 停用/恢复同键重放、指纹冲突、版本冲突、非法状态迁移、审计和事务回滚。
- 账号会话在审核、驳回和停用后失效，门店停用账号的新经营请求返回 `MERCHANT_SHOP_SUSPENDED`。
- 完整快照重建后仍为 24 张业务表，新增字段、唯一索引、种子聚合和纯种子恢复通过。

### HTTP 与 OpenAPI

- 真实服务验证新增 9 个操作，总计 86 个唯一 `operationId`。
- 校验全部 `$ref`、Bearer 声明、字符串 ID、分页 Schema、命令请求/响应以及 400/401/403/404/409/500/503。
- `/doc.html` 可用、`/v3/api-docs` 可用、Swagger UI 禁用。
- 以真实管理员 Token 完成列表、详情、私有媒体、审核、门店查询、停用和恢复；验收后重建纯种子并注销 Token。

### 管理 Web

- Vitest 覆盖类型适配、筛选规范化、中文状态、Blob 生命周期、权限按钮、幂等键复用、409 刷新和错误反馈。
- Playwright 覆盖审核通过、审核驳回、私有图片、门店停用、选择性账号恢复、三角色菜单与权限。
- 执行 TypeScript、ESLint、Prettier、Vitest、生产构建及 1440x900、1280x720、390x844 截图检查。

## 完成判定

- 后端、数据库、OpenAPI、管理 Web 和真实 HTTP 全部完成，阶段状态才可改为“已实现”。
- 数据库测试结束后恢复 24 表纯种子并清空 Redis DB 15；不得保留审核或治理测试数据。
- 所有受影响契约、路线图和验证记录同步更新；后端与管理 Web 分别形成包含总结、明细和验证的 Conventional Commit。
- Android/iOS 真机状态可单列“未确认”，不伪造设备验收，但不阻塞本阶段自动化完成判定。
