# Roamly 后端开发契约

```yaml
version: 13
updatedAt: 2026-09-05
scope: 服务端、OpenAPI、数据库、事务、安全与基础设施
reviewStatus: accepted
designStatus: 已冻结
demoImplementationStatus: 已实现
extensionImplementationStatus: 未实现
demoDataClosureStatus: 已实现
deviceAcceptanceStatus: 不适用
```

## 契约职责

本文只管理 Roamly 后端。数据库结构以 [DATABASE_SCHEMA.md](./DATABASE_SCHEMA.md) 为真源，HTTP 线协议以运行时 `/v3/api-docs` 为真源，若依后端能力取舍以 [若依扩展技术决策](./docs/architecture/RUOYI_EXTENSION_COMPATIBILITY.md) 为准，阶段顺序以 [四端交付路线图](./docs/roadmap/FOUR_END_DELIVERY_ROADMAP.md) 为准。

本文不定义消费者、商户或管理员页面，不描述前端布局、视觉和组件。客户端契约只能消费本文和 OpenAPI 已公开的事实，不能反向改变服务端状态机、金额或权限语义。

冲突裁决固定为：运行时 OpenAPI 管 HTTP 线协议，数据库契约管持久化事实，本文管后端行为与基础设施，四端路线图只管实施顺序。

## 设计门禁

- 阶段编码前必须存在已冻结的详细阶段设计，覆盖接口、数据、权限、事务、失败模式和测试。
- 接口、字段、枚举、表、索引、权限、资金规则或基础设施模式变化时，先解冻并升级对应契约，再修改源码。
- 状态只使用“未确认、未实现、开发中、已实现、已废弃”。存在类、接口或依赖不等于已实现。
- 本项目允许直接重构旧字段和 Demo 数据，不建设双写或历史兼容层；数据库完整覆盖仅限明确授权的 Demo 开发库。

## 工程基线

- 当前运行基线为 Java 21、Spring Boot 3.5.15、Springdoc 2.8.17、Swagger Core 2.2.47、MyBatis-Plus 3.5.16、Hutool 5.8.43、Redisson 3.52.0、Sa-Token 1.46.0、Knife4j 5.2.1、MySQL 与 Redis。
- 阶段 16 已完成兼容基线对齐并通过依赖树与真实运行时验证；Java 21、Sa-Token、Jackson、Knife4j 和 Redisson 保持现有选择。
- Maven 模块当前为 `ray-common`、`ray-pojo`、`ray-server`，依赖方向为 `ray-server -> ray-common + ray-pojo`。
- `ray-common` 只保存稳定结果、错误码、权限码和通用类型；`ray-pojo` 保存 DTO、VO、实体和枚举；第三方 SDK 类型不得进入公共 DTO。
- Demo 已实现的基础设施能力通过稳定端口保留可替换边界：分布式锁使用 Redisson 原生实现，后台扫描使用 Spring Scheduling，XLSX 使用自有 OOXML writer；后续生产适配器可分别替换为 Lock4j、SnailJob 和 Fesod。
- 后续可选适配器使用 `ray-integration-*` 边界，SnailJob 执行器使用独立 `ray-job` 边界；未启用模块不得进入默认运行时依赖树。`extensionImplementationStatus` 只描述这些生产适配器，不影响 Demo 已实现状态。

## 已实现范围

| 领域 | 后端能力 | 状态 |
|---|---|---|
| 消费者认证 | 模拟短信、登录、当前会话注销、多端独立会话 | 已实现 |
| 城市与分区 | 城市、官方分区、详情、用户关注 | 已实现 |
| 媒体 | 图片校验、临时本地存储、绑定、过期清理 | 已实现 |
| 社区 | 动态、媒体、点赞、评论、回复、关注和三类信息流 | 已实现 |
| 本地生活 | 商户分类、筛选、距离、详情、点评和评分聚合 | 已实现 |
| 团购 Demo | 商品、并发下单、取消返库、15 分钟关单、Mock 支付、多份发券 | 已实现 |
| 券包 Demo | 用户隔离查询、详情、过期刷新、退款、动态二维码与核销关联 | 已实现 |
| 商户经营基础 | 独立认证、五种账号状态、经营媒体、入驻申请、审核与门店治理 | 已实现 |
| 管理与治理 | 管理员认证、账号生命周期、事务审计、商户审核和门店停用/恢复 | 已实现 |
| 旧链路 | Blog、旧上传、旧优惠券、旧秒杀接口与表 | 已废弃 |

## Demo 数据闭环

- `dev` 完整快照保证 33 张业务表全部具有可查询样例，不再出现支付、退款、员工、核销、账本、结算或审计页面只有空表的情况。
- 三个客户端都有稳定测试账号：消费者 `13456789011`、商户店主 `13900000001`、平台管理员 `admin`；开发环境短信验证码为 `123456`，管理员密码为 `Roamly123`。
- 管理端另有审核员、财务管理员和停用账号；商户端另有店长、核销员、停用员工与待接受邀请账号，用于验证服务端权限和状态隔离。
- 样例链路覆盖社区互动、已核销点评、四类券、五种订单状态、六种支付状态、六种用户券状态、五种退款状态、核销与撤销、佣金分录和三种结算状态。
- 测试账号、附加角色账号、邀请令牌、数据数量和代表性业务 ID 统一记录在 [DATABASE_SCHEMA.md](./DATABASE_SCHEMA.md)，不在客户端复制第二份凭据真源。

## 认证与授权

- 登录域固定为 `CONSUMER`（消费者端）、`MERCHANT`（商户端）、`ADMIN`（管理端），使用独立 Sa-Token 逻辑、Redis 键空间和路由拦截，Token 不得跨域复用。
- 管理员角色固定为 `PLATFORM_ADMIN`（平台超级管理员）、`MERCHANT_REVIEWER`（商户审核员）、`FINANCE`（财务管理员）。
- 商户角色固定为 `OWNER`（店主）、`MANAGER`（店长）、`VERIFIER`（核销员）；门店归属是业务数据范围，不映射为若依租户。
- 权限码由后端代码维护；客户端隐藏入口不能替代服务端逐接口授权。
- 管理员密码使用 BCrypt，新建和重置后必须改密；连续 5 次失败锁定 15 分钟，禁止停用自己及最后一个有效平台超级管理员。

固定管理员权限码目录如下；角色与权限的映射由后端代码维护，客户端只消费当前身份返回的权限集合：

| 权限码 | 中文释义 |
|---|---|
| `admin:dashboard:read` | 运营摘要查看 |
| `admin:user:manage` | 管理员账号管理 |
| `admin:merchant-application:review` | 商户申请审核 |
| `admin:shop:govern` | 门店治理 |
| `admin:voucher:review` | 团购券审核 |
| `admin:trade:read` | 订单、支付与核销查看 |
| `admin:refund:manage` | 退款处理 |
| `admin:commission:manage` | 佣金规则与资金账本管理 |
| `admin:settlement:manage` | 结算处理与失败重试 |
| `admin:audit:read` | 操作审计查看 |

## HTTP 线协议

- 路径统一位于 `/v1`，私有接口使用 `Authorization: Bearer <token>`，业务 ID 在 HTTP 与 OpenAPI 中均为字符串。
- 成功使用 `Result`、`PageResult` 或 `CursorPageResult`；失败使用 `ErrorResult`、真实 HTTP 状态和稳定字符串业务码。
- 高风险命令要求 `Idempotency-Key` 请求头，覆盖审核、下单、支付、退款、核销、撤销和结算重试；幂等窗口不能替代数据库正确性。
- 当前源码共 138 个唯一 `operationId`：包含阶段 23-29 的支付、退款、员工、核销、实时事件、账本、结算、导出、商户门店订单及管理端订单/审计查询操作；数量由运行时 `/v3/api-docs` 验证。
- Knife4j 为 `/doc.html`，OpenAPI 为 `/v3/api-docs`，Swagger UI 禁用；全局声明 400、500，私有接口声明 401，并按行为声明 403、404、409、413、429、503。

## 服务端状态机

- 管理员账号：`ACTIVE`（已启用）、`DISABLED`（已停用）。
- 商户账号展示：`NOT_APPLIED`（未入驻）、`PENDING`（审核中）、`ACTIVE`（已激活）、`REJECTED`（审核未通过）、`DISABLED`（已停用）。
- 商户申请：`DRAFT`（草稿）、`PENDING`（审核中）、`APPROVED`（审核通过）、`REJECTED`（审核未通过）。
- 门店经营：`PENDING`（待激活）、`ACTIVE`（营业中）、`SUSPENDED`（已停用）、`CLOSED`（已关闭）。
- 券型：`PACKAGE`（套餐券）、`CASH`（代金券）、`DISCOUNT`（折扣券）、`MULTI_USE`（次卡）。
- 券审核：`DRAFT`（草稿）、`PENDING`（审核中）、`APPROVED`（审核通过）、`REJECTED`（审核未通过）。
- 券销售：`SCHEDULED`（待开售）、`ON_SALE`（销售中）、`OFF_SALE`（已下架）、`SOLD_OUT`（已售罄）、`ENDED`（已结束）。
- 订单：`PENDING_PAYMENT`（待支付）、`PAID`（已支付）、`CANCELED`（已取消）、`REFUNDING`（退款中）、`REFUNDED`（已退款）。
- 支付：`PENDING`（待支付）、`SUCCEEDED`（支付成功）、`FAILED`（支付失败）、`CLOSED`（已关闭）、`PARTIALLY_REFUNDED`（部分退款）、`REFUNDED`（已退款）。
- 用户券：`UNUSED`（未使用）、`PARTIALLY_USED`（部分使用）、`USED`（已使用）、`EXPIRED`（已过期）、`REFUNDING`（退款中）、`REFUNDED`（已退款）。
- 退款：`REQUESTED`（已申请）、`PROCESSING`（处理中）、`SUCCEEDED`（退款成功）、`FAILED`（退款失败）、`REJECTED`（退款被拒）。

## 事务与一致性

- 审核、停用和恢复使用期望状态与版本条件更新；审计只在事务结果确定后记录，不得在外层回滚时提前留下成功记录。
- 最后一个有效平台超级管理员保护必须使用数据库串行化手段，不能只做无锁计数后更新。
- 下单锁按用户和商品隔离；库存使用条件扣减，15 分钟关单只返还一次，支付和关单竞态由数据库状态取得唯一执行权。
- 支付成功按订单唯一事实增加销量并按数量逐份发券；重复请求、回调或任务重放不得重复扣库存、发券、退款、核销或记账。
- 券码仅通过 HMAC 索引定位；动态二维码不包含裸券 ID 或手输编号，核销预览不改变状态。
- 资金使用整数分和基点；账本只追加，核销确认收入，已结算退款进入后续负向调整，不覆盖历史批次。

## 基础设施模式

- 短信模式为 `MOCK`（模拟）、`DISABLED`（禁用），生产扩展增加 `SMS4J`（真实供应商）；prod 无供应商时返回 `SMS_SERVICE_UNAVAILABLE`（短信服务不可用）。
- 支付模式为 `MOCK`（模拟）、`DISABLED`（禁用），预留 `WECHAT`（微信支付）；prod 未配置真实支付时返回 `PAYMENT_SERVICE_UNAVAILABLE`（支付服务不可用）。
- 存储通过自有端口隔离，dev/test 默认 `LOCAL`（本地存储），prod 使用 `S3`（S3 兼容对象存储）且无安全默认凭据。
- Demo 使用 Redisson 原生 `RLock` 完成订单并发协调，并保留 Lock4j 适配边界；数据库条件更新与唯一约束仍是最终事实。
- Demo 使用 Spring Scheduling 驱动关单、券过期、定时上下架、媒体清理、退款重试和 T+1 结算；任务服务保持幂等并保留 SnailJob 执行器适配边界，查询与支付继续保留惰性关单。
- 管理 SSE 和商户 WebSocket 只发送资源失效事件，客户端收到后回查权威接口；Redis 负责多实例会话分发。
- Demo 使用自有 OOXML writer 提供受权限控制的同步 XLSX 导出，字段与权限边界兼容 Fesod 适配；Spring Boot Admin、SkyWalking 与 WarmFlow 保持后续扩展。

## 阶段 18 服务端冻结设计

- 商户入驻只接受 `OWNER`（店主）角色；`NOT_APPLIED`（未入驻）与 `REJECTED`（审核未通过）可写，`PENDING`（审核中）只读，`ACTIVE`（已激活）不重复创建申请。
- `GET /v1/merchant/application` 在尚无草稿时返回成功且 `data=null`；`PUT /v1/merchant/application` 以 `version` 做乐观锁保存完整草稿快照；`POST /v1/merchant/application/submission` 使用 8 至 128 位 `Idempotency-Key` 提交。
- 草稿字段固定为门店名称、统一社会信用代码、法定代表人、联系人、联系电话、门店类目、城市、区县、详细地址、经纬度、七日营业时段、营业执照媒体、最多九张经营图片及 Mock 结算户名/银行/账号后四位。草稿允许字段缺省，提交时统一校验完整性。
- 营业时间使用七个唯一 `dayOfWeek`，取值为 `MONDAY`（星期一）至 `SUNDAY`（星期日）；营业日包含一至三个不重叠的 `HH:mm` 时段，休息日时段必须为空。
- 经营媒体接口为上传 `POST /v1/merchant/business-media/images`、临时删除 `DELETE /v1/merchant/business-media/images/{mediaId}` 与鉴权读取 `GET /v1/merchant/business-media/images/{mediaId}/content`。上传表单包含 `file` 和 `purpose`，阶段 18 只接受 `LICENSE`（营业执照）与 `GALLERY`（经营图片）。
- 单图只接受 JPEG、PNG、WebP，最大 10 MB，宽高均为 320 至 8192 像素；对象键由服务端生成。数据库只保存 bucket、object key 与元数据，读取接口返回私有缓存响应，不暴露永久公网 URL。
- `GET /v1/merchant/reference/cities` 与 `GET /v1/merchant/reference/shop-types` 为商户端提供只读字典，商户小程序不得复用消费者 `/v1/cities` 或 `/v1/shop-types`。
- 上传先写对象存储再建临时记录，建档失败补偿删除对象；草稿保存只引用并续期临时媒体；提交事务按 ID 加锁并原子绑定媒体、迁移申请与商户账号状态。事务失败不得留下已绑定媒体。
- 存储端口使用 `LOCAL`（本地存储）与 `S3`（S3 兼容对象存储）两种模式；S3 采用冻结的 AWS SDK S3 2.28.22，生产缺少 endpoint、region、bucket 或凭据时启动失败，不回退本地目录。
- 阶段 18 新增错误码：`MERCHANT_APPLICATION_NOT_EDITABLE`（申请不可编辑）、`MERCHANT_APPLICATION_INCOMPLETE`（申请资料不完整）、`MERCHANT_APPLICATION_VERSION_CONFLICT`（申请版本冲突）、`MERCHANT_APPLICATION_STATE_CONFLICT`（申请状态冲突）、`MERCHANT_APPLICATION_IDEMPOTENCY_CONFLICT`（提交幂等键冲突）、`BUSINESS_MEDIA_NOT_FOUND`（经营媒体不存在）、`BUSINESS_MEDIA_NOT_OWNED`（经营媒体不属于当前商户）、`BUSINESS_MEDIA_INVALID_TYPE`（经营媒体类型不支持）、`BUSINESS_MEDIA_INVALID_DIMENSIONS`（经营媒体尺寸不合规）、`BUSINESS_MEDIA_EXPIRED`（临时经营媒体已过期）、`BUSINESS_MEDIA_ALREADY_BOUND`（经营媒体已绑定）、`OBJECT_STORAGE_UNAVAILABLE`（对象存储不可用）。

## 阶段 19 商户审核与门店治理

- 阶段 19 的完整接口、字段、事务和测试真源为 [商户审核与门店治理详细设计](./docs/stages/STAGE_19_MERCHANT_REVIEW_AND_SHOP_GOVERNANCE.md)，服务端、数据库、运行时 OpenAPI 与管理 Web 已完成闭环。
- 新增申请列表、详情、申请私有媒体、通过、驳回、门店列表、详情、停用和恢复共 9 个管理操作；全部使用 `ADMIN`（管理端）Bearer Token，其中审核与治理命令要求 8 至 128 位 `Idempotency-Key`。
- 申请审核权限为 `admin:merchant-application:review`（商户申请审核），门店治理权限为 `admin:shop:govern`（门店治理）；`PLATFORM_ADMIN`（平台超级管理员）与 `MERCHANT_REVIEWER`（商户审核员）可用，`FINANCE`（财务管理员）拒绝。
- 审核通过在一个事务中取得申请行锁、创建唯一来源门店、迁移申请、激活 `OWNER`（店主）账号并写审计；驳回在一个事务中迁移申请和账号并保存规范化原因。审核采用版本条件更新、幂等键和 SHA-256 请求指纹，禁止覆盖先完成的决定。
- 门店停用只把当前 `ACTIVE`（已激活）账号迁移为 `DISABLED`（已停用）并记录 `SHOP_SUSPENSION`（门店停用联动）；恢复只恢复该来源账号，不误激活 `ACCOUNT_GOVERNANCE`（平台账号治理）或 `STAFF_MANAGEMENT`（员工管理）停用的账号。
- 审核通过、驳回、停用和恢复提交后注销受影响商户会话并失效身份缓存；提交后清理失败只记录告警，后续经营鉴权仍实时拒绝非活动账号或已停用门店。
- 管理员私有媒体读取只允许申请已绑定的营业执照与经营图片，不暴露对象键或永久公网 URL；申请敏感详情成功读取写入 `MERCHANT_APPLICATION_SENSITIVE_VIEWED`（查看商户申请敏感资料）审计。
- 阶段 19 新增错误码：`MERCHANT_APPLICATION_NOT_FOUND`（商户申请不存在）、`MERCHANT_APPLICATION_ALREADY_REVIEWED`（商户申请已审核）、`MERCHANT_APPLICATION_REVIEW_VERSION_CONFLICT`（商户申请审核版本冲突）、`MERCHANT_APPLICATION_REVIEW_IDEMPOTENCY_CONFLICT`（商户申请审核幂等键冲突）、`SHOP_STATUS_CONFLICT`（门店经营状态冲突）、`SHOP_VERSION_CONFLICT`（门店版本冲突）、`SHOP_GOVERNANCE_IDEMPOTENCY_CONFLICT`（门店治理幂等键冲突）、`MERCHANT_SHOP_SUSPENDED`（所属门店已停用）；对象读取复用 `OBJECT_STORAGE_UNAVAILABLE`（对象存储不可用）。
- 本阶段不新增业务表，阶段 19 仍直接覆盖 24 张基础业务表；运行时 OpenAPI 已由 77 增至 86 个唯一 `operationId`。

## 阶段 20 服务端实现

- 阶段 20 的字段、接口、事务、媒体和测试真源为 [四类券模型与商户建券详细设计](./docs/stages/STAGE_20_VOUCHER_AUTHORING.md)，四类券建模和商户建券闭环已实现。
- 旧 `NORMAL`（普通团购）/`SECKILL`（秒杀团购）商品直接重构为 `PACKAGE`（套餐券）、`CASH`（代金券）、`DISCOUNT`（折扣券）、`MULTI_USE`（次卡），审核状态与销售状态分离，不保留旧字段兼容层。
- 新增商户券列表、创建、详情、更新、删除、复制与提交共 7 个操作；仅 `OWNER`（店主）和 `MANAGER`（店长）的活动门店可用，`VERIFIER`（核销员）拒绝。
- 草稿保存绑定私有券图片，复制生成独立对象；商品、明细、媒体和事务感知商户审计原子更新，提交使用版本、幂等键和 SHA-256 请求指纹。
- 阶段 20 已完成 25 张业务表范围内的四类券建券、媒体绑定、复制、提交和商户端体验；阶段 21 增加券审核、上下架和公开销售状态计算，阶段 22 增加服务端确认与幂等下单，阶段 23-29 已完成支付、退款、员工、核销、实时事件、账本、结算、导出、商户门店订单及管理端订单/审计查询，当前运行时为 138 个唯一 `operationId`。

## 阶段 23-29 收口补充（v12）

- `GET /v1/merchant/orders` 在查询前强制校验 `merchant:order:read`（商户订单查看）；仅 `OWNER`（店主）和 `MANAGER`（店长）的激活账号拥有该权限，`VERIFIER`（核销员）统一返回 403 `MERCHANT_FORBIDDEN`（商户权限不足），且不触发订单查询。
- 管理导出固定覆盖商户申请、团购券、订单、退款、核销、账本、结算和审计八类资源；每类资源先校验对应 `admin:*`（管理端权限）权限，未知资源返回 400 `EXPORT_RESOURCE_INVALID`（导出资源无效）。
- 导出使用 10,001 行边界探测，超过 10,000 行返回 400 `EXPORT_TOO_LARGE`（导出数据超限），不截断、不生成部分文件；字符串业务 ID 按文本写入并转义公式前缀。
- 上述权限和导出校验属于服务端强制规则，客户端菜单隐藏、筛选参数和门店字段都不能替代或绕过；数据库仍保持 33 张业务表，不新增导出任务表。

## 后端阶段

| 阶段 | 后端交付 | 状态 |
|---:|---|---|
| 1-14 | 消费者 Demo 与旧链路退役 | 已实现 |
| 15 | 契约拆分、版本和适配器边界 | 已实现 |
| 16 | 依赖对齐、管理员认证/账号、限流、脱敏和事务审计 | 已实现 |
| 17 | 商户认证与账号状态 | 已实现 |
| 18 | 经营媒体、存储端口和入驻 | 已实现 |
| 19 | 商户审核与门店治理 | 已实现 |
| 20 | 四类券、商户草稿、复制、删除和提交 | 已实现 |
| 21 | 平台券审核、上下架和消费者可见性 | 已实现 |
| 22 | 确认订单、服务端计价、限购和库存锁 | 已实现 |
| 23-24 | 关单、Mock 支付、多份发券和退款 | 已实现 |
| 25-27 | 员工、核销、动态二维码和实时事件 | 已实现 |
| 28-29 | 佣金账本、结算和导出 | 已实现 |
| 30 | 全量运行时与数据验收 | 已实现 |

## 验收

- 默认 `mvn test` 不重建数据库；真实数据库测试仅在 `RUN_DATABASE_INTEGRATION_TESTS=true` 时运行并使用 Redis DB 15。
- HTTP 运行时测试验证三类 Token、Knife4j、OpenAPI、安全声明、全部 `$ref`、错误响应和唯一 `operationId`。
- 依赖升级或可选模块启用必须执行依赖树、编译、最小运行时和关闭模块后的默认启动验证。
- 数据库测试结束后恢复纯种子数据并清空测试 Redis；不执行 `package`、`install`、`deploy`。

## 验证记录

| 日期 | 范围 | 结果 |
|---|---|---|
| 2026-09-04 | 消费者小程序契约 | 33 个测试文件、120 项通过 |
| 2026-09-04 | 后端默认测试 | 140 项中 120 项通过、20 项按条件跳过，0 失败；默认运行未重建数据库 |
| 2026-09-04 | 数据库 Demo | 24 张业务表真实重建；7 项数据库闭环通过并恢复纯种子状态，Redis DB 15 为空 |
| 2026-09-04 | 运行时 HTTP/OpenAPI | 86 个唯一操作、全部 `$ref`、Knife4j、三域 Token 和管理/商户错误响应共 8 项通过 |
| 2026-09-04 | 阶段 19 开发运行时 | dev 配置真实启动；商户登录、私有证照、申请审核、门店停用/恢复及会话失效共 1 项通过 |
| 2026-09-04 | 阶段 16 管理 Web | 4 个 Vitest 文件 16 项通过；Playwright 7 项通过、1 项按项目跳过；3 个冻结尺寸截图通过 |
| 2026-09-04 | 阶段 17 商户小程序 | 4 个 Vitest 文件 19 项通过；TypeScript、ESLint、Stylelint、Prettier、npm 构建和微信开发者工具构建通过 |
| 2026-09-04 | 阶段 18 商户小程序 | 6 个 Vitest 文件 29 项通过；TypeScript、ESLint、Stylelint、Prettier、npm 构建和微信开发者工具自动化入口通过 |
| 2026-09-04 | 阶段 19 管理 Web | 6 个 Vitest 文件 24 项通过；Playwright 16 项通过、2 项按项目条件跳过；3 个冻结尺寸截图通过 |
| 2026-09-05 | 阶段 21 券审核与销售状态 | 后端单元测试、消费者/商户/管理端契约测试和管理 Web 构建通过；阶段完成时运行时 OpenAPI 为 98 个唯一操作 |
| 2026-09-05 | 阶段 22-30 交易、履约与资金闭环 | 服务端计价、关单、Mock 支付、退款、员工、核销、实时事件、佣金、结算、导出、商户门店订单、管理端订单/审计查询和四端契约验证通过；当前运行时 OpenAPI 为 138 个唯一操作 |
| 2026-09-05 | 三端账号与 Demo 数据闭环 | 33 张业务表全部非空，三端推荐账号与附加权限账号可用；数据库闭环测试 9 项全部通过并恢复纯种子状态；后端默认测试 159 项中 137 项通过、22 项按条件跳过，0 失败、0 错误 |

## 非目标

当前 Demo 不包含真实微信支付/退款/分账、生产短信、动态 RBAC、多租户、部门岗位、通用字典、代码生成、社交登录、动态数据源、WarmFlow、Spring Boot Admin 和 SkyWalking。未实现能力不得因文档或依赖存在而标记完成。
