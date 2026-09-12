# Roamly 后端开发契约

```yaml
version: 27
updatedAt: 2026-09-12
scope: 服务端、OpenAPI、数据库、事务、安全与基础设施
reviewStatus: accepted
designStatus: 已冻结
demoImplementationStatus: 已实现
extensionImplementationStatus: 客服生产适配器未实现
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

## 阶段 37 契约、模块和数据库基础

- 状态：已实现。
- 订单交易状态与售后状态分离；退款审核状态与渠道执行状态分离；客服状态只能按白名单转换。
- 数据库不引入 Flyway 或其他运行时迁移框架，`ray-server/src/main/resources/schema-init.sql` 是可重建 Demo 数据库的结构真源；已有开发库只执行经过审查的 SQL 修改段，不由应用启动自动改表。
- 当前结构基线为 43 张业务表；阶段 37 已新增退款明细、退款执行尝试和结算执行尝试三张表，结构为 46 张业务表。
- 业务事件用于审计、通知和统计，不能替代核心事务状态；WebSocket/SSE 只刷新权威接口，不改变退款、客服、核销或结算状态。
- 阶段验收已同步本契约、`DATABASE_SCHEMA.md`、阶段文档、路线图和验证记录；源码、SQL、文档和编译验证完成。

## 阶段 38 资金、收银和结算校正

- 状态：已实现。详细实现与验证见 [阶段 38 文档](./docs/stages/STAGE_38_FINANCE_CASHIER_SETTLEMENT.md)。
- `platform_discount_amount` 当前表达平台承担的优惠，资金口径等同平台补贴；核销确认的商家毛应收为顾客实付加平台补贴，预计收入为毛应收减服务费。
- 今日收银接口增量返回商品售价、商家补贴、平台补贴、顾客实付、服务费和预计收入；商户端不增加外卖入口。
- 结算生成与失败重试均新增 `settlement_attempt`，Mock 执行成功后才推进批次为成功；重试不再直接修改成功状态。
- 2026-09-12 默认后端测试 222 项中 200 项通过、22 项按既有环境开关跳过；商户小程序 73 项通过。

## 阶段 39 退款核心重构

- 状态：已实现。详细实现与验收见 [阶段 39 文档](./docs/stages/STAGE_39_REFUND_CORE.md)。
- 退款申请使用 `voucher_refund_item` 逐券保存金额和核销快照，源码、查询与账本不再读写 `voucher_ids` CSV。
- 审核状态统一为 `PENDING_REVIEW/AUTO_APPROVED/MANUAL_APPROVED/REJECTED`；审核只生成决定和执行任务，重复审批由退款主记录行锁串行化。
- Coordinator 扫描任务，LeaseService 在短事务中原子领取并提交租约，Worker 在事务外调用 `RefundGateway`，ResultService 在新的短事务中校验租约所有者并落地结果。
- Mock 网关支持 `SUCCESS`、`FAIL_ONCE`、`ALWAYS_FAIL` 和 `DELAYED`；失败进入重试等待，达到次数上限后进入人工处理。
- 订单交易状态保持 `PAID`，退款进度写入独立 `after_sale_status`；多券部分退款通过用户券实际状态计数判断。
- 三个登录域分别提供退款逐券明细和时间线；WebSocket/SSE 仍只通知刷新，不参与审核或执行状态转换。

## 阶段 40 客服后端重构

- 状态：已实现。详细实现与验证见 [阶段 40 文档](./docs/stages/STAGE_40_CUSTOMER_SERVICE_BACKEND.md)。
- 平台客服工单以 `applicant_type + applicant_id` 作为三端权限真源；商户只能访问当前商户账号主动创建的工单，`shop_id` 和 `related_shop_id` 只提供业务上下文，不能扩大可见范围。
- 工单状态统一为 `OPEN/CLAIMED/WAITING_CUSTOMER/WAITING_MERCHANT/WAITING_INTERNAL/RESOLVED/CLOSED`，全部状态转换在服务层白名单校验；内部备注只写消息和 `has_internal_note`，不改变主状态。
- 开放工单通过带 `assignee_admin_id IS NULL AND status='OPEN'` 条件的单条更新原子认领；转交锁定工单，校验目标客服账号，并追加不可覆盖的转交记录。
- 消息接口支持 `before_message_id/after_message_id/limit` 游标，消费者和商户只读取公开消息；三类阅读者各自维护只前进的已读游标和未读数量。
- 客服附件使用现有 Local/S3 私有对象存储端口，经历 `TEMPORARY/BOUND/DELETED` 生命周期；上传、绑定、读取和删除均复核工单归属，外部申请人不能读取内部备注附件。
- 已增加标签、工单标签、个人/团队快捷回复、首次及最近响应时间、等待时间起点、SLA 截止和超时排序；客服敏感操作同步写入事务后审计。
- 2026-09-12 客服专项 8 项通过；默认后端测试 245 项中 223 项通过、22 项按既有环境开关跳过，0 失败。51 表 DDL 与客服种子列数完成静态校验，未知数据库未执行重建。

## 阶段 41 三端体验重构

- 状态：已实现。详细设计与交付记录见 [阶段 41 文档](./docs/stages/STAGE_41_FRONTEND_EXPERIENCE.md)。
- 管理端退款与客服工作台、消费者客服会话、商户客服与售后详情只消费退款主表、审核状态、执行状态和客服工单权威状态；实时连接只触发重新查询。
- 为消费者补充本人工单关闭与七天内重新打开入口；为管理端补充待认领、我的、超时、高优先级和退款工单服务端队列筛选，避免分页后客户端过滤失真。
- 本阶段不变更数据库结构，不接真实退款或支付渠道，不新增外卖入口。

## 工程基线

- 当前运行基线为 Java 21、Spring Boot 3.5.15、Springdoc 2.8.17、Swagger Core 2.2.47、MyBatis-Plus 3.5.16、Hutool 5.8.43、Redisson 3.52.0、Sa-Token 1.46.0、Knife4j 5.2.1、MySQL 与 Redis。
- 阶段 16 已完成兼容基线对齐并通过依赖树与真实运行时验证；Java 21、Sa-Token、Jackson、Knife4j 和 Redisson 保持现有选择。
- Maven 模块当前为 `ray-common`、`ray-pojo`、`ray-server`，依赖方向为 `ray-server -> ray-common + ray-pojo`。
- `ray-common` 只保存稳定结果、错误码、权限码和通用类型；`ray-pojo` 保存 `dto`（接口入参及内部/可复用传输结构）、`vo`（接口出参）、`entity`（数据库对象）和枚举；第三方 SDK 类型不得进入公共模型。
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
| 券包 Demo | 用户隔离查询、详情、过期刷新、退款、固定二维码与核销关联 | 已实现 |
| 商户经营基础 | 独立认证、五种账号状态、经营媒体、入驻申请、审核与门店治理 | 已实现 |
| 商家收银 | 今日团购收银、核销收入拆解、平台补贴、服务费规则、T+1 Mock 结算尝试 | 已实现 |
| 退款执行 | 逐券申请、独立审核、租约执行、Mock 重试、账本和时间线 | 已实现 |
| 客服工单 | 申请人隔离、原子认领、状态机、游标消息、附件、未读、标签、转交、快捷回复与 SLA | 已实现 |
| 管理与治理 | 管理员认证、账号生命周期、事务审计、商户审核和门店停用/恢复 | 已实现 |
| 旧链路 | Blog、旧上传、旧优惠券、旧秒杀接口与表 | 已废弃 |

## Demo 数据闭环

- `dev` 完整快照保证当前 51 张业务表全部具有可查询样例，不再出现支付、退款、执行尝试、员工、核销、客服、结算或审计页面只有空表的情况。
- 三个客户端都有稳定测试账号：消费者 `13456789011`、商户租户 `13900000001`、平台管理员 `admin`；开发环境短信验证码为 `123456`，消费者和商户密码以及管理员密码均为 `Roamly123`。
- 管理端另有审核员、财务管理员和停用账号；商户端另有店长、核销员、停用员工与待接受邀请账号，用于验证服务端权限和状态隔离。
- 样例链路覆盖社区互动、已核销点评、四类券、订单交易与独立售后状态、六种支付状态、六种用户券状态、五种退款主状态、核销与撤销、退款/结算执行尝试、佣金分录和三种结算状态。
- 测试账号、附加角色账号、邀请凭证、数据数量和代表性业务 ID 统一记录在 [DATABASE_SCHEMA.md](./DATABASE_SCHEMA.md)，不在客户端复制第二份凭据真源。

## 认证与授权

- 登录域固定为 `CONSUMER`（消费者端）、`MERCHANT`（商户端）、`ADMIN`（管理端），使用独立 Sa-Token 逻辑、Redis 键空间和路由拦截，Token 不得跨域复用。
- 管理员角色固定为 `PLATFORM_ADMIN`（平台超级管理员）、`MERCHANT_REVIEWER`（商户审核员）、`FINANCE`（财务管理员）、`CUSTOMER_SERVICE`（客服）。
- 商户角色固定为 `VISITOR`（游客）、`TENANT`（租户）、`MANAGER`（店长）、`VERIFIER`（核销员）；`shop_id` 是公司和数据隔离边界，不映射为若依租户。
- 权限码由后端代码维护；客户端隐藏入口不能替代服务端逐接口授权。
- 管理员密码使用 BCrypt，新建和重置后必须改密；连续 5 次失败锁定 15 分钟，禁止停用自己及最后一个有效平台超级管理员。
- 管理员请求鉴权在单次 HTTP 请求内复用已校验的管理员实体，跨请求不缓存；账号停用、注销和改密仍按原有实时校验与会话失效规则执行。

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
| `admin:customer-service:read` | 客服队列和消息查看 |
| `admin:customer-service:manage` | 工单认领、回复、关闭和内部备注 |

## HTTP 线协议

- 路径统一位于 `/v1`，私有接口使用 `Authorization: Bearer <token>`，业务 ID 在 HTTP 与 OpenAPI 中均为字符串。
- 成功使用 `Result`、`PageResult` 或 `CursorPageResult`；失败使用 `ErrorResult`、真实 HTTP 状态和稳定字符串业务码。
- 高风险命令要求 `Idempotency-Key` 请求头，覆盖审核、下单、支付、退款、核销、撤销和结算重试；幂等窗口不能替代数据库正确性。
- 当前源码预期共 212 个唯一 `operationId`；阶段 40 增加三端消息游标和附件、商户详情/回复、管理端转交/标签/快捷回复接口，阶段 41 增加消费者工单关闭与重新打开入口。精确集合已同步到静态映射和 OpenAPI 运行时契约测试，真实 `/v3/api-docs` 复验随阶段 42 的隔离运行环境执行。

### 位置与同城推荐

- 消费者端通过 `POST /v1/location/context` 提交微信 `gcj02` 真实坐标，服务端分别返回城市和区县编码/名称；当前已开放杭州 `330100` 与西宁 `630100`，POI 字段预留但不作为地域判断依据。
- 推荐动态和门店列表在客户端提供坐标时由服务端重新解析城市并覆盖客户端 `cityCode`，避免手工城市参数改变地域。推荐动态仅按城市硬过滤，解析出的区县作为当前区县软加权（+20%）排序上下文；商品综合推荐按当前区县软加权（+15%）并叠加 3 公里距离衰减。距离排序继续使用门店经纬度，缺少任一坐标时返回稳定参数错误。
- 商品公开发现接口与门店列表使用相同规则：提供完整真实经纬度时由服务端重新解析城市并覆盖客户端 `cityCode`；未提供经纬度时才使用 `cityCode` 作为兼容入口。
- 普通动态保存发布时区县和粗粒度位置标签；不保存用户完整精确坐标。定位失败不再由首页使用旧城市替代。
- `city` 与 `district` 是独立字典，门店和动态分别保存 `city_code`、`district_code`；商户入驻时校验区县归属城市，审核建店时写入两个编码。
- 地域隔离、推荐公式、开发种子区县/门店/商品/动态和验收场景详见 [地域推荐与测试数据细节](./docs/project-details/LOCATION_RECOMMENDATION_AND_TEST_DATA.md)。
- 管理端 SSE 使用已登录管理员申请的 30 秒一次性事件票据连接；30 秒仅表示票据有效期，事件流本身为长连接并发送 25 秒注释心跳。事件流不依赖浏览器 `EventSource` 的 Bearer Header，管理员退出、停用、改密或角色变化时主动断开其旧连接。
- Knife4j 为 `/doc.html`，OpenAPI 为 `/v3/api-docs`，Swagger UI 禁用；全局声明 400、500，私有接口声明 401，并按行为声明 403、404、409、413、429、503。

## 消费者商品发现改版（v14）

- 状态：已实现。
- 新增按城市分页的公开团购商品发现接口，支持门店分类、商品或门店关键词、综合、销量、距离与价格排序；只返回活动门店下当前审核通过且可售的商品。
- 公开商品详情增量提供四类券权益、结构化使用规则、退款规则和套餐明细；业务 ID 继续使用字符串，金额继续使用整数分。
- 已审核券封面通过受控公开内容接口读取，只允许访问与商品匹配的已绑定券媒体，不开放营业执照、临时媒体、删除媒体或跨商品媒体。
- 消费者订单摘要在 `VoucherOrderVO.productCover` 返回同一受控商品封面路径；无封面时为空，客户端必须保留明确空封面状态，不生成占位业务图片。
- 本改版不新增业务表，不改变单商品归属单门店、订单计价、库存、限购、支付或退款状态机。

## 服务端状态机

- 管理员账号：`ACTIVE`（已启用）、`DISABLED`（已停用）。
- 商户账号展示：`NOT_APPLIED`（未入驻）、`PENDING`（审核中）、`ACTIVE`（已激活）、`REJECTED`（审核未通过）、`DISABLED`（已停用）。
- 商户申请：`DRAFT`（草稿）、`PENDING`（审核中）、`APPROVED`（审核通过）、`REJECTED`（审核未通过）。
- 门店经营：`PENDING`（待激活）、`ACTIVE`（营业中）、`SUSPENDED`（已停用）、`CLOSED`（已关闭）。
- 券型：`PACKAGE`（套餐券）、`CASH`（代金券）、`DISCOUNT`（折扣券，仅核销）、`MULTI_USE`（次卡）；商品详情统一由商户维护，折扣券说明仅展示不参与计价。
- 券审核：`DRAFT`（草稿）、`PENDING`（审核中）、`APPROVED`（审核通过）、`REJECTED`（审核未通过）。
- 券销售：`SCHEDULED`（待开售）、`ON_SALE`（销售中）、`OFF_SALE`（已下架）、`SOLD_OUT`（已售罄）、`ENDED`（已结束）。
- 订单交易：`PENDING_PAYMENT`（待支付）、`PAID`（已支付）、`CANCELED`（已取消）、`COMPLETED`（已完成）；旧 `REFUNDING/REFUNDED` 只为读取历史数据保留，不再由新退款流程写入。
- 订单售后：`NONE`、`APPLYING`、`UNDER_REVIEW`、`APPROVED`、`REJECTED`、`REFUNDING`、`PARTIALLY_REFUNDED`、`REFUNDED`、`REFUND_FAILED`、`CLOSED`，持久化于 `after_sale_status`。
- 消费者订单查询的 `status=REFUNDING` 对应“退款/售后”聚合页签，实际按非 `NONE` 售后状态查询并兼容旧订单状态。
- 支付：`PENDING`（待支付）、`SUCCEEDED`（支付成功）、`FAILED`（支付失败）、`CLOSED`（已关闭）、`PARTIALLY_REFUNDED`（部分退款）、`REFUNDED`（已退款）。
- 用户券：`UNUSED`（未使用）、`PARTIALLY_USED`（部分使用）、`USED`（已使用）、`EXPIRED`（已过期）、`REFUNDING`（退款中）、`REFUNDED`（已退款）。
- 退款申请：`REQUESTED`（已申请）、`PROCESSING`（处理中）、`SUCCEEDED`（退款成功）、`FAILED`（退款失败）、`REJECTED`（退款被拒）；审核和执行另以独立字段表达。
- 客服工单：`OPEN`（待认领）、`CLAIMED`（处理中）、`WAITING_CUSTOMER`（等待消费者）、`WAITING_MERCHANT`（等待商户）、`WAITING_INTERNAL`（等待平台内部）、`RESOLVED`（已解决）、`CLOSED`（已关闭）。WebSocket、SSE 和管理端在线状态不参与状态转换。

## 事务与一致性

- 审核、停用和恢复使用期望状态与版本条件更新；审计只在事务结果确定后记录，不得在外层回滚时提前留下成功记录。
- 最后一个有效平台超级管理员保护必须使用数据库串行化手段，不能只做无锁计数后更新。
- 下单锁按用户和商品隔离；库存使用条件扣减，15 分钟关单只返还一次，支付和关单竞态由数据库状态取得唯一执行权。
- 支付成功按订单唯一事实增加销量并按数量逐份发券；重复请求、回调或任务重放不得重复扣库存、发券、退款、核销或记账。
- 支付准备接口先校验订单仍为待支付且未过期，已支付、已取消或已关闭订单不会再次返回可支付能力。
- 券码仅通过 HMAC 索引定位；固定二维码不包含裸券 ID 或手输编号，核销预览不改变状态，预览令牌不保存线下消费金额。
- 固定二维码凭证按用户券一对一持久化，二维码内容为 `rq1.{tokenKey}.{signature}`；签名绑定券、用户、版本和有效期，重复申请返回相同内容。退款中、已核销、已过期或已退款券统一返回不可核销冲突。
- 资金使用整数分和基点；订单支付冻结、核销收入和佣金均以团购券线上实付金额为基础，线下额外消费不进入账本或结算。

## 基础设施模式

- 短信模式为 `MOCK`（模拟）、`DISABLED`（禁用），生产扩展增加 `SMS4J`（真实供应商）；prod 无供应商时返回 `SMS_SERVICE_UNAVAILABLE`（短信服务不可用）。
- 支付模式为 `MOCK`（模拟）、`WECHAT`（微信参数契约）和 `DISABLED`（禁用）；Mock 成功/失败保持订单与发券事务一致，WECHAT 缺少签名参数时返回可展示的不可用原因，不伪造支付成功。
- 存储通过自有端口隔离，dev/test 默认 `LOCAL`（本地存储），prod 使用 `S3`（S3 兼容对象存储）且无安全默认凭据。
- Demo 使用 Redisson 原生 `RLock` 完成订单并发协调，并保留 Lock4j 适配边界；数据库条件更新与唯一约束仍是最终事实。
- Demo 使用 Spring Scheduling 驱动关单、券过期、定时上下架、媒体清理、退款重试和 T+1 结算；任务服务保持幂等并保留 SnailJob 执行器适配边界，查询与支付继续保留惰性关单。
- 管理 SSE 和商户 WebSocket 只发送资源失效事件，客户端收到后回查权威接口；Redis 负责多实例会话分发。
- Demo 使用自有 OOXML writer 提供受权限控制的同步 XLSX 导出，字段与权限边界兼容 Fesod 适配；Spring Boot Admin、SkyWalking 与 WarmFlow 保持后续扩展。
- 固定二维码使用 `RAY_QR_HMAC_SECRET` 注入的服务端 HMAC 密钥；开发环境仅提供占位默认值，生产环境必须显式配置，不记录或提交真实密钥。

## 阶段 18 服务端冻结设计

- 商户入驻只接受 `VISITOR`（游客）角色；`NOT_APPLIED`（未入驻）与 `REJECTED`（审核未通过）可写，`PENDING`（审核中）只读，`ACTIVE`（已激活）不重复创建申请。
- `GET /v1/merchant/application` 在尚无草稿时返回成功且 `data=null`；`PUT /v1/merchant/application` 以 `version` 做乐观锁保存完整草稿快照；`POST /v1/merchant/application/submission` 使用 8 至 128 位 `Idempotency-Key` 提交。
- 草稿字段固定为门店名称、统一社会信用代码、法定代表人、联系人、联系电话、门店类目、城市、区县、详细地址、经纬度、七日营业时段、营业执照媒体、最多九张经营图片及 Mock 结算户名/银行/账号后四位。草稿允许字段缺省，提交时统一校验完整性。
- 营业时间使用七个唯一 `dayOfWeek`，取值为 `MONDAY`（星期一）至 `SUNDAY`（星期日）；营业日包含一至三个不重叠的 `HH:mm` 时段，休息日时段必须为空。
- 经营媒体接口为上传 `POST /v1/merchant/business-media/images`、临时删除 `DELETE /v1/merchant/business-media/images/{mediaId}` 与鉴权读取 `GET /v1/merchant/business-media/images/{mediaId}/content`。上传表单包含 `file` 和 `purpose`，阶段 18 只接受 `LICENSE`（营业执照）与 `GALLERY`（经营图片）。
- 单图只接受 JPEG、PNG、WebP，最大 10 MB，宽高均为 320 至 8192 像素；对象键由服务端生成。数据库只保存 bucket、object key 与元数据，读取接口返回私有缓存响应，不暴露永久公网 URL。
- `GET /v1/merchant/reference/cities` 与 `GET /v1/merchant/reference/shop-types` 为商户端提供只读字典，商户小程序不得复用消费者 `/v1/cities` 或 `/v1/shop-types`。
- 上传先写对象存储再建临时记录，建档失败补偿删除对象；草稿保存只引用并续期临时媒体；提交事务按 ID 加锁并原子绑定媒体、迁移申请与商户账号状态。事务失败不得留下已绑定媒体。
- 存储端口使用 `LOCAL`（本地存储）与 `S3`（S3 兼容对象存储）两种模式；S3 采用冻结的 AWS SDK S3 2.28.22，生产缺少 endpoint、region、bucket 或凭据时启动失败，不回退本地目录。
- 本地开发统一使用仓库根目录 `uploads/` 并通过 `MEDIA_STORAGE_ROOT` 配置；路径解析兼容从仓库根目录或 `ray-server` 模块目录启动，两种方式均落到同一目录。消费者媒体按 `user/avatar/{userId}`、`user/post/{userId}`、`user/review/{userId}` 分类；商户媒体按 `merchant/avatar/{accountId}`、`merchant/onboarding/license|gallery/{accountId}`、`merchant/voucher/cover|detail/{accountId}` 分类；均继续按年/月分层，`other/` 仅作预留。历史 `/blogs/**` 消费者路径只保留兼容读取，不再写入新文件；历史商户 `merchant/{accountId}` 对象迁入 `merchant/legacy/accounts/{accountId}` 并由本地适配器兼容旧对象键。
- 阶段 18 新增错误码：`MERCHANT_APPLICATION_NOT_EDITABLE`（申请不可编辑）、`MERCHANT_APPLICATION_INCOMPLETE`（申请资料不完整）、`MERCHANT_APPLICATION_VERSION_CONFLICT`（申请版本冲突）、`MERCHANT_APPLICATION_STATE_CONFLICT`（申请状态冲突）、`MERCHANT_APPLICATION_IDEMPOTENCY_CONFLICT`（提交幂等键冲突）、`BUSINESS_MEDIA_NOT_FOUND`（经营媒体不存在）、`BUSINESS_MEDIA_NOT_OWNED`（经营媒体不属于当前商户）、`BUSINESS_MEDIA_INVALID_TYPE`（经营媒体类型不支持）、`BUSINESS_MEDIA_INVALID_DIMENSIONS`（经营媒体尺寸不合规）、`BUSINESS_MEDIA_EXPIRED`（临时经营媒体已过期）、`BUSINESS_MEDIA_ALREADY_BOUND`（经营媒体已绑定）、`OBJECT_STORAGE_UNAVAILABLE`（对象存储不可用）。

## 阶段 19 商户审核与门店治理

- 阶段 19 的完整接口、字段、事务和测试真源为 [商户审核与门店治理详细设计](./docs/stages/STAGE_19_MERCHANT_REVIEW_AND_SHOP_GOVERNANCE.md)，服务端、数据库、运行时 OpenAPI 与管理 Web 已完成闭环。
- 新增申请列表、详情、申请私有媒体、通过、驳回、门店列表、详情、停用和恢复共 9 个管理操作；全部使用 `ADMIN`（管理端）Bearer Token，其中审核与治理命令要求 8 至 128 位 `Idempotency-Key`。
- 申请审核权限为 `admin:merchant-application:review`（商户申请审核），门店治理权限为 `admin:shop:govern`（门店治理）；`PLATFORM_ADMIN`（平台超级管理员）与 `MERCHANT_REVIEWER`（商户审核员）可用，`FINANCE`（财务管理员）拒绝。
- 审核通过在一个事务中取得申请和游客账号行锁、创建唯一来源门店、迁移申请、把账号激活为 `TENANT`（租户）并写审计；驳回后账号仍为游客。审核采用版本条件更新、幂等键和 SHA-256 请求指纹，禁止覆盖先完成的决定。
- 门店停用只把当前 `ACTIVE`（已激活）账号迁移为 `DISABLED`（已停用）并记录 `SHOP_SUSPENSION`（门店停用联动）；恢复只恢复该来源账号，不误激活 `ACCOUNT_GOVERNANCE`（平台账号治理）或 `STAFF_MANAGEMENT`（员工管理）停用的账号。
- 审核通过、驳回、停用和恢复提交后注销受影响商户会话并失效身份缓存；提交后清理失败只记录告警，后续经营鉴权仍实时拒绝非活动账号或已停用门店。
- 管理员私有媒体读取只允许申请已绑定的营业执照与经营图片，不暴露对象键或永久公网 URL；申请敏感详情成功读取写入 `MERCHANT_APPLICATION_SENSITIVE_VIEWED`（查看商户申请敏感资料）审计。
- 阶段 19 新增错误码：`MERCHANT_APPLICATION_NOT_FOUND`（商户申请不存在）、`MERCHANT_APPLICATION_ALREADY_REVIEWED`（商户申请已审核）、`MERCHANT_APPLICATION_REVIEW_VERSION_CONFLICT`（商户申请审核版本冲突）、`MERCHANT_APPLICATION_REVIEW_IDEMPOTENCY_CONFLICT`（商户申请审核幂等键冲突）、`SHOP_STATUS_CONFLICT`（门店经营状态冲突）、`SHOP_VERSION_CONFLICT`（门店版本冲突）、`SHOP_GOVERNANCE_IDEMPOTENCY_CONFLICT`（门店治理幂等键冲突）、`MERCHANT_SHOP_SUSPENDED`（所属门店已停用）；对象读取复用 `OBJECT_STORAGE_UNAVAILABLE`（对象存储不可用）。
- 本阶段不新增业务表，阶段 19 仍直接覆盖 24 张基础业务表；运行时 OpenAPI 已由 77 增至 86 个唯一 `operationId`。

## 阶段 20 服务端实现

- 阶段 20 的字段、接口、事务、媒体和测试真源为 [四类券模型与商户建券详细设计](./docs/stages/STAGE_20_VOUCHER_AUTHORING.md)，四类券建模和商户建券闭环已实现。
- 旧 `NORMAL`（普通团购）/`SECKILL`（秒杀团购）商品直接重构为 `PACKAGE`（套餐券）、`CASH`（代金券）、`DISCOUNT`（折扣券）、`MULTI_USE`（次卡），审核状态与销售状态分离，不保留旧字段兼容层。
- 券商品结构由 `voucher_product_detail`、`voucher_product_tag` 及三张类型规则表组成；前端只消费数据库返回的详情和 `iconKey`，不拼接固定权益标签。
- 新增商户券列表、创建、详情、更新、删除、复制与提交共 7 个操作；仅 `TENANT`（租户）和 `MANAGER`（店长）的活动门店可用，`VERIFIER`（核销员）拒绝。
- 草稿保存绑定私有券图片，复制生成独立对象；商品、明细、媒体和事务感知商户审计原子更新，提交使用版本、幂等键和 SHA-256 请求指纹。
- 阶段 20 已完成四类券建券、媒体绑定、复制、提交和商户端体验；阶段 21-29 完成审核、下单、支付、退款、员工、核销、实时事件、账本、结算、导出和审计查询；阶段 34-35 增加账号资料、今日收银、收入拆解和客服数据模型。
- 消费者退款 VO 的 `reasonCode` 保留固定枚举，`reason` 返回对应中文文案；退款详情同时携带原支付渠道、退款单号、商家单号和处理时间，客户端不自行推导资金状态。
- 退款统一由 `/v1/users/me/refunds`、`/v1/merchant/after-sales` 和 `/v1/admin/refunds` 三端入口提交；商户只能发起申请，平台负责审批、驳回和失败重试。退款成功同步用户券、订单、支付状态，并追加 `REFUND_REVERSED` 账本分录，资金摘要按冲回后的账本事实计算。

## 阶段 23-29 收口补充（v12）

- `GET /v1/merchant/orders` 在查询前强制校验 `merchant:order:read`（商户订单查看）；仅 `TENANT`（租户）和 `MANAGER`（店长）的激活账号拥有该权限，`VERIFIER`（核销员）统一返回 403 `MERCHANT_FORBIDDEN`（商户权限不足），且不触发订单查询。
- 管理导出固定覆盖商户申请、团购券、订单、退款、核销、账本、结算和审计八类资源；每类资源先校验对应 `admin:*`（管理端权限）权限，未知资源返回 400 `EXPORT_RESOURCE_INVALID`（导出资源无效）。
- 导出使用 10,001 行边界探测，超过 10,000 行返回 400 `EXPORT_TOO_LARGE`（导出数据超限），不截断、不生成部分文件；字符串业务 ID 按文本写入并转义公式前缀。
- 上述权限和导出校验属于服务端强制规则，客户端菜单隐藏、筛选参数和门店字段都不能替代或绕过；数据库当前为 51 张业务表，导出仍不新增任务表。

## 商户售后体验增强（v16）

- 状态：已实现。
- 商户售后列表在保留精确 `status` 查询的基础上，增加面向页面页签的 `stage` 聚合筛选与退款单、订单、券码精确搜索；同一请求不得同时提交 `status` 和 `stage`。
- 新增商户退款候选查询，按本店订单号优先、券码其次定位订单，并返回每张券的可退资格、不可退原因和服务端分摊金额；候选查询与退款创建必须复用同一资格规则。
- 商户退款创建的订单与券 ID 统一使用 HTTP 字符串，进入 Service 前通过 `IdUtils` 转换，禁止客户端转换为 JavaScript `Number`。
- 券码搜索只通过现有摘要索引定位，响应仅返回券码后四位；跨店数据与不存在数据统一返回未找到。本增强不改变退款状态机、审批权限、资金处理或数据库结构。

## 消费者订单券型筛选（v17）

- 状态：已实现。
- `GET /v1/users/me/orders` 新增可选 `productType` 查询参数，允许 `PACKAGE`（套餐券）、`CASH`（代金券）、`DISCOUNT`（折扣券）和 `MULTI_USE`（次卡），并与既有 `status` 筛选组合使用。
- 消费者订单摘要 `VoucherOrderVO` 返回 `productType` 与 `productTypeLabel`，类型事实来自关联的 `voucher_product`，不在客户端按商品标题猜测。
- 订单列表分类下拉通过服务端 `productType` 筛选刷新分页结果；不新增数据库字段或表，订单状态机、退款聚合和权限边界保持不变。
- 非法 `productType` 返回 400 `INVALID_PRODUCT_TYPE`；既有非法 `status` 继续返回 `INVALID_STATUS`。

## 消费者账号与资料重构（v18）

- 状态：已实现。完整接口、数据、事务和测试设计见 [阶段 33 消费者账号与资料重构](./docs/stages/STAGE_33_CONSUMER_ACCOUNT_AND_PROFILE.md)。
- 消费者认证改为显式注册、短信登录和密码登录；验证码按场景隔离，密码使用 BCrypt，手机号或密码修改后注销全部消费者会话。
- 本人资料与公开主页分离；公开资料不暴露手机号、生日或内部城市偏好，昵称按北京时间自然日限制每天修改一次。
- 昵称、手机号和密码的新值不得与当前值相同；分别在昵称确认更新、手机号换绑验证码发送和密码确认修改时返回明确业务错误。
- `user_info` 重构为精简的 `user_profile`，头像复用消费者媒体资产生命周期。

## 商户账号与个人信息（v19）

- 状态：开发中。完整接口、数据、权限和测试设计见 [阶段 34 商户账号与个人信息](./docs/stages/STAGE_34_MERCHANT_ACCOUNT_AND_PROFILE.md)。
- 商户认证增加显式注册和密码登录，短信验证码按登录与注册场景隔离；短信登录不再隐式创建账号。
- 商户本人可修改头像、昵称、手机号和密码，角色、账号状态和所属门店保持只读；手机号或密码修改后注销全部商户会话。
- 商户头像使用私有经营媒体生命周期。

## 商户租户身份与短时邀请（v20）

- 状态：开发中。完整身份、数据、接口和测试设计见 [阶段 36 商户租户身份与短时邀请](./docs/stages/STAGE_36_MERCHANT_TENANT_AND_SHORT_INVITATION.md)。
- 注册账号为游客，入驻审核通过后成为租户；租户是唯一具备员工管理权限的公司主账号。
- 员工邀请改为校验已注册纯游客后签发六位数字凭证，凭证实际有效 60 秒、只可消费一次且数据库不保存明文。
- `shop_id` 继续作为公司归属和数据隔离边界，不新增租户表或跨公司切换。

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
| 25-27 | 员工、核销、固定二维码和实时事件 | 已实现 |
| 28-29 | 佣金账本、结算和导出 | 开发中 |
| 30 | 全量运行时与数据验收 | 开发中 |
| 31 | 核销与线下收款解耦 | 已实现 |
| 33 | 消费者显式注册、密码认证与个人资料 | 已实现 |
| 34 | 商户显式注册、密码认证与个人资料 | 开发中 |
| 36 | 游客/租户身份与 60 秒员工邀请 | 开发中 |
| 37 | 退款、客服与资金领域契约和数据库基础 | 已实现 |
| 38 | 收银、收入、服务费与结算执行校正 | 已实现 |
| 39 | 退款审核、执行、重试和账本闭环 | 已实现 |
| 40 | 平台客服权限、工单、消息、附件与 SLA | 已实现 |
| 41 | 三端退款与客服工作台 | 已实现 |
| 42 | 集成测试、运行时 OpenAPI 与监控收口 | 未实现 |

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
| 2026-09-05 | 阶段 22-30 交易、履约与资金闭环 | 服务端计价、关单、Mock 支付准备/支付、退款、员工、核销、实时事件、佣金、结算、导出、商户门店订单、管理端订单/审计查询和四端契约验证通过；当前运行时 OpenAPI 为 141 个唯一操作 |
| 2026-09-05 | 三端账号与 Demo 数据闭环 | 33 张业务表全部非空，三端推荐账号与附加权限账号可用；数据库闭环测试 9 项全部通过并恢复纯种子状态；后端默认测试 159 项中 137 项通过、22 项按条件跳过，0 失败、0 错误 |
| 2026-09-05 | 消费者商品发现改版 v14 | 同城商品分页、四种排序、商品/门店关键词、公开券媒体权限、商品结构化权益与套餐明细已实现；后端默认测试 163 项中 141 项通过、22 项按条件跳过，0 失败、0 错误；运行时 OpenAPI 为 141 个唯一操作 |
| 2026-09-05 | 阶段 31 核销与线下收款解耦 | 核销请求/记录和数据库删除线下金额字段；订单实付金额、佣金及次卡分摊守恒；撤销冲回、T+1 02:00 生成、四端契约、数据库闭环和 OpenAPI 运行时验证通过 |
| 2026-09-05 | 管理员鉴权与 SSE 重连修复 | 单次 HTTP 请求复用已校验管理员实体；SSE 改用 30 秒一次性票据并实时校验账号状态，`OpenApiAndAuthRuntimeTest`、`MvcConfigTest` 与 `AdminAuthServiceImplTest` 通过 |
| 2026-09-12 | 管理 SSE 会话超时修复 | 区分 30 秒票据有效期与 SSE 会话寿命，增加 25 秒注释心跳和异步超时专用收口，避免 `text/event-stream` 响应再次序列化 JSON 错误体；退出、停用、改密和角色变化会主动断开旧流；专项测试通过，默认后端测试 254 项中 232 项通过、22 项按既有环境开关跳过，0 失败 |
| 2026-09-05 | Demo 数据库脚本重建入口 | `schema-init.sql` 已将 43 张业务表的 `DROP TABLE IF EXISTS` 集中到文件开头并按逆依赖顺序执行，后续仅保留建表语句 |
| 2026-09-06 | 消费者 Mock 支付状态补强 | 新增 Mock 成功、失败和禁用渠道的服务测试；`mvn -q test`、`mvn -q -DskipTests compile` 通过 |
| 2026-09-06 | 消费者订单列表前后端打通 | 修复 MyBatis-Plus 链式查询分页导致的 500；退款/售后聚合返回 `REFUNDING` 与 `REFUNDED`；后端全量测试 172 项中 150 项通过、22 项按条件跳过 |
| 2026-09-06 | 全项目基线复核 | 删除无引用的旧 Redis 常量；消费者与商户小程序统一前端工具链版本；四端类型检查、Lint、样式检查、单元测试和构建验证通过 |
| 2026-09-08 | 阶段 34 商户账号与个人信息 | 商户注册、短信/密码登录、本人资料、私有头像、手机号与密码修改已实现；默认后端测试 201 项中 179 项通过、22 项按环境开关跳过，0 失败；编译通过。运行时 OpenAPI 与数据库闭环因未获 Demo 环境授权未执行，阶段保持开发中 |
| 2026-09-08 | 阶段 36 商户租户身份与短时邀请 | 后端默认测试共 209 项，187 项通过、22 项按环境开关跳过，0 失败；`MerchantStaffServiceImplTest` 10 项通过；运行时 OpenAPI 契约测试 8 项通过并确认 185 个唯一 `operationId`；商户小程序 `npm run verify`、格式检查及构建通过（15 个 Vitest 文件、73 项）；管理 Web `pnpm verify`、格式检查及生产构建通过（8 个 Vitest 文件、28 项）。商户端 320/375/390/430px 视觉脚本因缺少基准图未执行，Demo 数据库闭环未经授权未执行，因此阶段保持开发中 |
| 2026-09-12 | 阶段 39 退款核心重构 | 默认后端测试 236 项中 214 项通过、22 项按既有环境开关跳过；退款专项 14 项通过。管理 Web 生产构建通过、路由用例隔离复跑 4 项通过；商户小程序 73 项通过。消费者小程序类型检查/Lint/样式检查通过，157 项测试中 156 项通过，唯一失败是用户工作区现有局域网 IP `192.168.2.101` 与旧测试期望 `192.168.2.105` 不一致。未重建未知数据库，运行时 OpenAPI 和数据库恢复性验证留在阶段 42。 |
| 2026-09-12 | 阶段 41 三端退款与客服体验 | 后端编译通过，消费者关闭/重开、客服队列、退款队列与映射专项 15 项通过；默认后端测试 249 项中 227 项通过、22 项按既有环境开关跳过，0 失败；管理 Web `pnpm verify`、格式检查和生产构建通过（9 个文件、31 项）；消费者小程序 `npm run verify` 和微信 npm 构建通过（40 个文件、160 项）；商户小程序 `npm run verify` 和微信 npm 构建通过（16 个文件、76 项）。两个小程序的阶段 41 变更文件格式检查通过；全仓格式检查分别被 3 个和 1 个本阶段外既有文件阻塞。未知数据库、真实 MySQL/Redis、运行时 OpenAPI 和真机视觉验收未执行。 |

## 非目标

当前 Demo 不包含真实微信支付/退款/分账、生产短信、动态 RBAC、多租户、部门岗位、通用字典、代码生成、社交登录、动态数据源、WarmFlow、Spring Boot Admin 和 SkyWalking。未实现能力不得因文档或依赖存在而标记完成。
