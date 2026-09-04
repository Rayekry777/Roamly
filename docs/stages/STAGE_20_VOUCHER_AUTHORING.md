# 阶段 20：四类券模型与商户建券

```yaml
designVersion: 2
designStatus: 已冻结
implementationStatus: 已实现
dependsOn: 阶段 19 已实现
affectedEnds: 后端、商户小程序
```

## 目标与边界

本阶段直接重构旧团购商品模型，交付四类券的商户草稿、编辑、消费者视角预览、复制、删除和提交审核闭环。

- 券型固定为 `PACKAGE`（套餐券）、`CASH`（代金券）、`DISCOUNT`（折扣券）、`MULTI_USE`（次卡）。
- 涉及后端和商户小程序；平台审核、上下架和消费者新结构展示留在阶段 21。
- 现有消费者团购详情、下单、取消、Mock 支付和券包 Demo 必须继续工作；本阶段不得用商户建券重构破坏阶段 1 至 19。
- 本阶段不实现平台审核、真实支付、退款、核销、员工邀请、佣金或结算。
- 数据库继续采用获授权的 Demo 全量覆盖策略，不提供旧字段兼容、迁移脚本或双写。

## 进入条件

- 阶段 19 已实现，商户申请审核能创建 `ACTIVE`（营业中）门店并激活 `OWNER`（店主）账号。
- `MERCHANT`（商户端）认证域、`merchant:voucher:manage`（团购券管理）权限和门店停用门禁可用。
- `business_media_asset`（经营媒体）已支持 `VOUCHER_COVER`（券封面）与 `VOUCHER_DETAIL`（券详情图）用途枚举，但上传、绑定和读取尚未向建券开放。
- 当前 24 表快照、86 个运行时操作和两端自动化均已通过；本阶段完成后应为 25 表和 93 个运行时操作。

## 核心决策

### 审核与销售状态分离

- `review_status`（审核状态）固定为 `DRAFT`（草稿）、`PENDING`（审核中）、`APPROVED`（审核通过）、`REJECTED`（审核未通过）。
- `sale_status`（销售状态）固定为 `SCHEDULED`（待开售）、`ON_SALE`（销售中）、`OFF_SALE`（已下架）、`SOLD_OUT`（已售罄）、`ENDED`（已结束）。
- `DRAFT`、`PENDING`、`REJECTED` 时 `sale_status` 必须为空；只有 `APPROVED` 才允许非空销售状态。
- 阶段 20 只创建或迁移 `DRAFT -> PENDING`。阶段 21 负责审核决定和销售状态；为保持既有消费者 Demo，开发种子允许预置已审核且在售商品。
- `REJECTED` 商品第一次成功修改时回到 `DRAFT`，保留最近驳回原因、审核人和审核时间供商户核对；再次提交后由不可变操作审计保留历史决定。

### 草稿和版本

- 创建草稿只要求券型，券型创建后不可修改；切换券型必须新建草稿，防止遗留不适用字段。
- `DRAFT` 和 `REJECTED` 可读取和编辑，`PENDING` 与 `APPROVED` 在本阶段只读。
- 更新采用行锁加 `version`（乐观锁版本）条件更新；成功更新版本加 1，旧版本返回 409，不做客户端覆盖。
- 仅从未提交的 `DRAFT` 可物理删除；已提交或已有订单的商品不得删除。
- 同门店任意状态商品均可复制。复制生成独立 `DRAFT`，重置销量、审核、销售和幂等事实，完整复制结构化明细及私有图片对象。

### 金额、时间和结构化规则

- 金额全部为整数分，折扣使用基点；`8500` 表示 85 折，不接受浮点折扣。
- 销售期使用 Asia/Shanghai 语义的 ISO-8601 日期时间，开始必须早于结束。
- 有效期为 `FIXED_RANGE`（固定日期范围）或 `DAYS_AFTER_PURCHASE`（购买后若干天）二选一，不适用字段必须为空。
- 使用规则保存为七日结构化时段、排除日期、预约、叠加与退款布尔字段；数据库不保存自由文本 `rules`。
- 服务端统一规范化标题首尾空格、明细名称和单位；空字符串转为空值，集合去重后保持用户顺序。

## 数据库设计

### `voucher_product` 直接重构

字段固定为：

| 字段 | 类型与约束 | 语义 |
|---|---|---|
| `id` | bigint unsigned，主键自增 | 券商品 ID |
| `shop_id` | bigint unsigned，非空 | 权威所属门店 |
| `product_type` | varchar(16)，非空 | 四类券型 |
| `title` | varchar(120)，可空 | 草稿标题；提交时必填 |
| `sub_title` | varchar(255)，可空 | 副标题 |
| `cover_media_id` | bigint unsigned，可空 | 唯一封面媒体 |
| `detail_media_ids_json` | json，非空 | 0 至 9 张详情图的有序媒体 ID 数组；HTTP 输出转字符串 |
| `price_amount` | bigint unsigned，可空 | 售价 |
| `market_amount` | bigint unsigned，可空 | 门市价 |
| `face_value_amount` | bigint unsigned，可空 | 代金券抵扣额 |
| `minimum_spend_amount` | bigint unsigned，可空 | 代金券/折扣券最低消费 |
| `discount_rate_bps` | int unsigned，可空 | 折扣基点，100 至 9900 |
| `maximum_discount_amount` | bigint unsigned，可空 | 折扣券最高优惠 |
| `total_use_count` | int unsigned，可空 | 次卡总次数，2 至 100 |
| `total_stock` | int unsigned，非空默认 0 | 总库存 |
| `available_stock` | int unsigned，非空默认 0 | 可售库存 |
| `sold_count` | int unsigned，非空默认 0 | 已支付销量 |
| `purchase_limit` | int unsigned，非空默认 1 | 单用户限购 |
| `sale_begin_time` / `sale_end_time` | timestamp，可空 | 销售期 |
| `validity_type` | varchar(32)，可空 | 两种有效期模式 |
| `valid_begin_time` / `valid_end_time` | timestamp，可空 | 固定有效期 |
| `valid_days` | int unsigned，可空 | 购买后有效天数，1 至 365 |
| `usage_rules_json` | json，非空 | 七日可用时段结构 |
| `excluded_dates_json` | json，非空 | 不可用日期数组 |
| `reservation_required` | tinyint(1)，非空默认 0 | 是否预约 |
| `reservation_notice` | varchar(500)，可空 | 预约说明，仅预约时允许 |
| `stackable` | tinyint(1)，非空默认 0 | 是否可与店内优惠叠加 |
| `refund_anytime` | tinyint(1)，非空默认 0 | 是否支持随时退 |
| `refund_expired` | tinyint(1)，非空默认 0 | 是否支持过期退 |
| `review_status` | varchar(16)，非空默认 `DRAFT` | 审核状态 |
| `sale_status` | varchar(16)，可空 | 审核通过后的销售状态 |
| `rejection_reason` | varchar(500)，可空 | 最近驳回原因 |
| `submission_idempotency_key` | varchar(128)，可空 | 最近成功提交幂等键 |
| `submission_request_fingerprint` | char(64)，可空 | 规范化提交 SHA-256 指纹 |
| `submitted_at` | timestamp，可空 | 最近提交时间 |
| `review_decision` | varchar(16)，可空 | 阶段 21 的审核决定 |
| `review_idempotency_key` | varchar(128)，可空 | 阶段 21 的审核幂等键 |
| `review_request_fingerprint` | char(64)，可空 | 阶段 21 的审核指纹 |
| `reviewed_at` | timestamp，可空 | 最近审核时间 |
| `reviewer_admin_id` | bigint unsigned，可空 | 最近审核管理员 |
| `version` | int unsigned，非空默认 0 | 乐观锁版本 |
| `create_time` / `update_time` | timestamp，非空 | 创建与更新时间 |

索引固定为：

- `idx_voucher_product_shop_review(shop_id,review_status,update_time,id)`：商户草稿与审核列表。
- `idx_voucher_product_public(shop_id,review_status,sale_status,sale_begin_time,sale_end_time,id)`：消费者可见性。
- `idx_voucher_product_submission(submission_idempotency_key)`：提交重放定位；允许多个空值。

### `voucher_package_item` 新表

`voucher_package_item` 同时承载套餐项和次卡服务说明，字段固定为：

- `id`：bigint unsigned，自增主键。
- `product_id`：bigint unsigned，非空，逻辑关联 `voucher_product.id`。
- `name`：varchar(80)，非空，服务或商品名称。
- `quantity`：int unsigned，非空，1 至 999。
- `unit`：varchar(16)，非空，例如“份”“次”“人”。
- `unit_price_amount`：bigint unsigned，可空，单项门市价；为零或空时界面不展示划线价。
- `sort_order`：int unsigned，非空，从 0 连续递增。
- `create_time`、`update_time`：timestamp，非空。
- 唯一索引 `uk_voucher_package_item_product_sort(product_id,sort_order)`；普通索引 `idx_voucher_package_item_product(product_id,id)`。

`CASH` 和 `DISCOUNT` 不得存在明细；`PACKAGE` 与 `MULTI_USE` 提交时必须有 1 至 50 条明细。应用事务维护逻辑关联，不新增物理外键。

### 媒体归属

- 券封面必须恰好 1 张，详情图最多 9 张；所有 ID 必须唯一，封面不能同时出现在详情图。
- 草稿保存即把临时媒体绑定到 `VOUCHER_PRODUCT`（券商品）与商品 ID，避免 24 小时后丢失可恢复草稿。
- 更新按 ID 升序锁定媒体：新引用绑定、保留引用刷新排序、移除引用标记 `DELETED`（已删除）；对象删除在事务提交后执行，失败由现有清理任务重试。
- 同门店店主或店长可读取商品已绑定媒体；新上传临时媒体只能由上传账号首次绑定，跨门店、跨商品或用途错误均拒绝。
- 复制商品必须读取源私有对象并写入独立对象键和媒体记录；数据库回滚时补偿删除新对象，不共享 `object_key`。

### 快照与种子

- 本阶段新增 1 张表，完整快照由 24 表变为 25 表；目标 33 表总数不变。
- 既有商品 `3001`、`3002` 转换为 `APPROVED`（审核通过）且 `ON_SALE`（销售中），保持现有订单、库存、销量与消费者页面事实。
- 新增四种券型草稿及至少一个 `PENDING`（审核中）商品，供商户端恢复和阶段 21 审核使用；新增券图片种子必须与媒体元数据和对象初始化一致。
- 商品 3001 仍满足总库存 200、待支付占用 1、已支付 1、可售 198、销量 1；数据库测试结束后恢复相同纯种子。

## 请求模型与校验

### 创建草稿

`MerchantVoucherProductCreateRequest` 只包含必填 `productType`；创建后返回完整草稿，HTTP 状态为 201。

### 更新草稿

`MerchantVoucherProductUpdateRequest` 为完整快照，包含：

- 必填 `version`，以及可空 `title`、`subTitle`、`coverMediaId`、`priceAmount`、`marketAmount`、销售期和有效期字段。
- `detailMediaIds`、`usageRules`、`excludedDates` 和 `packageItems` 始终传数组，不传 `null`。
- `reservationRequired`、`stackable`、`refundAnytime`、`refundExpired` 始终传布尔值。
- 代金券字段 `faceValueAmount`、`minimumSpendAmount`；折扣券字段 `discountRateBps`、`minimumSpendAmount`、`maximumDiscountAmount`；次卡字段 `totalUseCount`。
- `totalStock` 与 `purchaseLimit` 可在未完成草稿中为 0；提交时必须满足完整规则。

草稿保存执行基础类型、长度、非负数、集合上限、时段格式和字段互斥校验；提交再执行完整性与业务交叉校验。

### 提交完整性

- 标题必填；售价 1 至 100000000 分，门市价不得低于售价。
- 总库存 1 至 1000000，限购 1 至 100 且不得大于总库存；草稿阶段销量必须为 0，提交时可售库存等于总库存。
- 销售开始早于结束；固定有效期开始早于结束且结束晚于销售开始，购买后有效天数为 1 至 365。
- 七个星期值唯一，至少一个可用日；每日 0 至 3 个不重叠 `HH:mm` 时段，至少一个可用日包含时段。
- 排除日期为唯一 ISO 日期；固定有效期下必须落在有效范围内。
- `reservationRequired=true` 时预约说明必填，关闭预约时说明必须为空。
- `PACKAGE`：1 至 50 条套餐项，其他类型专属字段为空。
- `CASH`：抵扣额大于 0、售价不高于抵扣额、最低消费不低于抵扣额，无明细和折扣字段。
- `DISCOUNT`：折扣基点 100 至 9900、最低消费大于 0、最高优惠大于 0，无明细、抵扣额和次数字段。
- `MULTI_USE`：总次数 2 至 100、1 至 50 条服务明细，无代金与折扣字段。

校验错误返回 400 和字段级错误；服务端不接受由客户端拼出的自由文本规则作为权威输入。

## HTTP 契约

所有接口使用 `MERCHANT`（商户端）Bearer Token、字符串 ID、真实 HTTP 状态和现有 `Result`/`PageResult`/`ErrorResult`。

| 方法与路径 | operationId | 成功 | 说明 |
|---|---|---:|---|
| `GET /v1/merchant/voucher-products` | `listMerchantVoucherProducts` | 200 | 按审核状态、券型、关键词分页 |
| `POST /v1/merchant/voucher-products` | `createMerchantVoucherProduct` | 201 | 创建指定券型空草稿 |
| `GET /v1/merchant/voucher-products/{productId}` | `getMerchantVoucherProduct` | 200 | 查询本门店商品与私有媒体摘要 |
| `PUT /v1/merchant/voucher-products/{productId}` | `updateMerchantVoucherProduct` | 200 | 完整快照保存，要求版本 |
| `DELETE /v1/merchant/voucher-products/{productId}` | `deleteMerchantVoucherProduct` | 204 | 仅删除未提交草稿 |
| `POST /v1/merchant/voucher-products/{productId}/copies` | `copyMerchantVoucherProduct` | 201 | 复制为独立草稿 |
| `POST /v1/merchant/voucher-products/{productId}/submission` | `submitMerchantVoucherProduct` | 200 | 提交审核，要求幂等键和版本 |

列表查询参数：`reviewStatus`、`productType`、`keyword`、`page >= 1`、`1 <= size <= 100`。空筛选不发送；关键词匹配标题，默认按更新时间和 ID 倒序。

提交请求只包含 `version`；`Idempotency-Key` 为 8 至 128 位 `[A-Za-z0-9._:-]`。同键同指纹返回原 `PENDING` 事实，不重复递增版本或写审计；同键异指纹返回 409。

本阶段新增 7 个操作，运行时唯一 `operationId` 由 86 增至 93。既有消费者接口路径和响应 Schema 在阶段 20 保持不变，规则文案只能由结构化事实派生。

## 权限与隔离

- `OWNER`（店主）与 `MANAGER`（店长）且账号和门店均为活动状态时可用全部 7 个接口。
- `VERIFIER`（核销员）返回 403 `MERCHANT_FORBIDDEN`（商户无权操作）；未激活、停用和门店停用沿用现有专用错误。
- 所有查询和命令先以当前账号 `shop_id` 限定数据；跨门店商品统一返回 404，避免泄露是否存在。
- 商户端菜单/按钮权限来自 `merchant:voucher:manage`，但前端隐藏不替代服务端角色和门店校验。

## 事务、审计与失败处理

- 创建、更新、复制、删除和提交都在事务中执行，并写 `actor_type=MERCHANT`（商户操作人）的事务感知审计。
- 审计动作固定为 `MERCHANT_VOUCHER_DRAFT_CREATED`（创建券草稿）、`MERCHANT_VOUCHER_DRAFT_UPDATED`（更新券草稿）、`MERCHANT_VOUCHER_DRAFT_COPIED`（复制券草稿）、`MERCHANT_VOUCHER_DRAFT_DELETED`（删除券草稿）、`MERCHANT_VOUCHER_SUBMITTED`（提交券审核）。
- 更新顺序为商品行锁、规范化校验、版本条件更新、明细同步、媒体同步、审计登记；任一步失败全部回滚。
- 复制先锁源商品与媒体，再创建目标商品、明细和独立对象；对象复制失败返回 503，已写新对象由回滚补偿清理。
- 删除先锁商品，确认 `DRAFT` 且无订单，再删除明细和商品、标记媒体；对象只在数据库提交后删除。
- 400：字段、金额、日期、时段、明细或类型专属规则错误。
- 403：角色无权限、账号或门店不可经营。
- 404：商品不存在或不属于本门店。
- 409：不可编辑、版本冲突、状态冲突或提交幂等冲突。
- 503：对象存储或依赖服务不可用；失败不得留下半绑定媒体或半复制商品。

新增错误码必须带中文释义：

- `VOUCHER_PRODUCT_NOT_EDITABLE`（团购券当前不可编辑）。
- `VOUCHER_PRODUCT_INCOMPLETE`（团购券资料不完整）。
- `VOUCHER_PRODUCT_VERSION_CONFLICT`（团购券版本冲突）。
- `VOUCHER_PRODUCT_STATE_CONFLICT`（团购券状态冲突）。
- `VOUCHER_PRODUCT_TYPE_CONFLICT`（团购券型字段冲突）。
- `VOUCHER_PRODUCT_IDEMPOTENCY_CONFLICT`（团购券提交幂等键冲突）。
- `VOUCHER_PRODUCT_HAS_ORDERS`（团购券已有订单不可删除）。

媒体错误继续复用阶段 18 的专用错误码。

## 商户小程序详细设计

### 页面与入口

- 新增 `/pages/vouchers/index`（团购券列表）、`/pages/vouchers/editor`（建券编辑）、`/pages/vouchers/preview`（消费者视角预览）。
- “我的”中的“团购券”进入列表；仅具备 `merchant:voucher:manage` 权限且状态为已激活时显示可用入口。
- 列表使用审核状态分段筛选、券型筛选和关键词；展示封面、标题、券型、售价、库存、更新时间、中文状态及适用动作。
- 空态提供“新建团购券”；加载失败保留重试，不把服务异常显示成空列表。

### 编辑流程

- 编辑页固定四步：券型与基础信息、价格库存与销售期、有效期与使用规则、图片与提交预览。
- 新建先用券型选择面板创建服务端空草稿；进入编辑后不允许切换券型。
- `PACKAGE` 显示套餐明细编辑器；`CASH` 显示抵扣额和最低消费；`DISCOUNT` 显示折扣、最低消费和最高优惠；`MULTI_USE` 显示总次数和服务明细。
- 七日规则按星期展示开关和最多三个时段；排除日期使用日期选择；预约、叠加、随时退和过期退使用开关。
- 封面 1 张、详情图最多 9 张，复用现有上传组件和带 Bearer 私有下载；页面不持久化对象 URL。
- 每步可保存草稿；保存成功以服务端响应整体替换基线，保存失败保留当前表单和已上传媒体并提供重试。

### 预览与命令

- 预览页沿用 Roamly 消费者团购详情的视觉层级，显示真实门店、标题、价格、结构化明细、有效期、时段、预约和退款规则，不复制竞品视觉。
- 未保存变更进入预览前先保存；预览返回编辑时保持当前服务端版本。
- 提交前展示完整性错误并定位到所属步骤；确认提交生成幂等键，网络失败重试复用，同一表单或版本变化后生成新键。
- 409 时刷新权威详情，同时把未提交表单保存在内存恢复区；用户选择继续编辑后显式重新应用，不静默覆盖新版本。
- 列表支持复制和删除。删除有二次确认；复制成功进入新草稿；命令期间禁用重复点击。
- `PENDING` 与 `APPROVED` 只读；`REJECTED` 顶部显示驳回原因并允许进入修订。

### 客户端状态

- 新增券 API、类型、格式化与 `voucher-draft` Store；所有 ID 为字符串，金额在表单中显示元但提交前严格转为整数分。
- Store 保存 `serverSnapshot`、`formDraft`、`dirty`、`saving`、`submissionKey` 和冲突恢复副本；退出登录时全部清空并释放私有媒体临时文件。
- 页面只消费适配后的领域对象，不直接读取 `Result` 包装；401、403、404、409、429、503 使用现有统一错误策略和本页明确反馈。

### 视觉与可访问性

- 沿用 `#ff5f57` 主色、`#8275ff` 辅色、白色内容面和紫灰文字；券型使用图标、标题与简短业务标签，不引入若依或竞品颜色。
- 表单使用单层内容面、固定底部操作区和清晰步骤标题；不嵌套卡片，不用超大营销标题。
- 320、375、390、430px 宽度下最长标题、金额、状态、明细和底部按钮不得重叠或造成页面横向滚动。

## 测试与验收

### 后端

- 四类券基础/完整交叉校验、无关字段拒绝、金额边界、销售/有效期、七日时段、排除日期和套餐项顺序。
- 店主/店长允许、核销员拒绝、三类 Token 隔离、跨店 404、账号和门店停用拒绝。
- 草稿创建、恢复、更新、驳回修订、版本冲突、只读状态、复制、删除和已有订单保护。
- 媒体首次绑定、同商品复用、移除清理、跨账号临时媒体、跨商品/跨店拒绝、复制对象补偿和存储 503。
- 提交完整性、同键重放、异指纹冲突、并发只成功一次、版本递增和事务审计。
- 现有消费者商品、下单、取消、支付、发券和券包回归保持通过。

### 数据库与运行时

- 真实重建确认 25 张业务表、新表唯一索引、JSON 结构、四类种子、媒体归属和旧字段删除。
- 真实 HTTP/OpenAPI 确认 93 个唯一 `operationId`、全部 `$ref`、Bearer、字符串 ID、201/204 与 400/401/403/404/409/500/503。
- 数据库集成结束后再次重建 25 表纯种子并清空 Redis DB 15。

### 商户小程序

- Vitest 覆盖四类表单映射、元/分转换、时段校验、草稿 Store、媒体、复制/删除、幂等键和 409 恢复。
- 微信开发者工具自动化覆盖列表空态/筛选、新建四类券、草稿恢复、消费者预览、提交、只读、驳回修订和无权限入口。
- 执行 Prettier、TypeScript、ESLint、Stylelint、Vitest、`npm run build:npm` 和微信开发者工具构建。
- 自动截图并检查常见小程序视口；Android/iOS 真机状态保持“未确认”，不阻塞阶段自动化完成判定。

## 完成判定

- 后端、25 表快照、93 个运行时操作、现有消费者回归、商户小程序自动化和视觉检查全部通过后，阶段状态才可改为“已实现”。
- 后端和商户小程序分别形成包含总结、明细和验证的 Conventional Commit；不得把阶段 21 的平台审核或上下架提前标记完成。
