# Roamly 后端开发与全栈设计契约

```yaml
version: 4
updatedAt: 2026-09-04
scope: Roamly Demo 城市、社区、本地生活与团购交易
reviewStatus: accepted
implementationStatus: 已实现（Demo 范围）
deviceAcceptanceStatus: 未确认
```

## 文档定位

本文档是后端范围、架构、接口状态、数据边界和验收记录的唯一维护入口。数据库事实以根目录 [DATABASE_SCHEMA.md](./DATABASE_SCHEMA.md) 为准，HTTP 事实以运行时 `/v3/api-docs` 为准，小程序事实以 [MINIAPP_DEVELOPMENT.md](../Roamly-miniapp/docs/MINIAPP_DEVELOPMENT.md) 为准。

阶段记录位于 [docs/stages](./docs/stages)。阶段状态均按 Demo 范围闭环为“已实现”；Android/iOS 真机验收单独记为“未确认”，不影响 Demo 阶段状态。

## 工程基线

- Java 21、Spring Boot 3.5.11、MyBatis-Plus 3.5.12、Sa-Token 1.46.0、Knife4j 5.2.1、Springdoc 2.8.9、MySQL、Redis。
- Maven 模块为 `ray-common`、`ray-pojo`、`ray-server`，依赖方向为 `ray-server -> ray-common + ray-pojo`。
- Java 内部业务 ID 使用 `Long`，HTTP、OpenAPI 和小程序统一使用字符串 ID。
- 统一响应为 `Result`、`PageResult`、`CursorPageResult`；错误响应为 `ErrorResult`。
- 私有接口使用 `Authorization: Bearer <token>`；公开接口允许匿名，但携带非法 Token 时返回 401。
- Knife4j 入口为 `/doc.html`，OpenAPI 为 `/v3/api-docs`，Swagger UI 禁用。

## 已实现范围

| 领域 | 已实现能力 | 状态 |
|---|---|---|
| 认证 | 模拟短信验证码、登录、当前 Token 注销、多端独立会话 | 已实现 |
| 城市与分区 | 城市、官方分区、分区详情、用户分区关注 | 已实现 |
| 媒体 | 图片校验、临时存储、归属校验、动态/点评事务绑定、过期清理 | 已实现 |
| 社区动态 | 普通动态、探店动态、修改、删除、点赞、作者列表 | 已实现 |
| 信息流 | 推荐流、关注流、分区最新/热门流、商户关联动态 | 已实现 |
| 评论 | 根评论、追加回复、分页、点赞、删除占位、热门摘要 | 已实现 |
| 本地生活 | 商户分类、城市/分类/关键词筛选、评分/热度/距离排序、商户详情 | 已实现 |
| 点评 | 列表、创建、修改、删除、唯一点评、媒体、评分聚合 | 已实现 |
| 团购 | 商品列表/详情、下单、取消返库、订单详情、支付确认幂等发券 | 已实现 |
| 券包 | 用户隔离列表/详情、未使用券、过期刷新 | 已实现 |
| 旧链路 | Blog、旧上传、旧优惠券、旧秒杀 API/代码/表退役 | 已废弃 |

商户写管理不属于用户端产品契约。无角色授权的 `POST /v1/shops`、`PUT /v1/shops/{shopId}` 已删除；商户后台与角色权限仍是非目标。

## 短信配置

唯一配置入口为 `ray.auth.sms`：

| Profile | mode | 行为 |
|---|---|---|
| `dev` | `MOCK` | 使用 `SMS_MOCK_CODE`，缺省为 `123456`，响应 204，不记录验证码明文 |
| `test` | `MOCK` | 使用 `SMS_MOCK_CODE`，缺省为 `123456`，Redis DB 15 |
| `prod` | `DISABLED` | 发送接口返回 503 `SMS_SERVICE_UNAVAILABLE` |

真实短信供应商是生产扩展项。生产环境不得通过默认值启用模拟验证码。

## HTTP 契约

运行时 OpenAPI 共 54 个唯一 `operationId`，覆盖以下路径族：

- `/v1/auth/**`、`/v1/cities`、`/v1/sections/**`
- `/v1/media/images/**`、`/v1/posts/**`、`/v1/comments/**`、`/v1/feeds/**`
- `/v1/users/**`、`/v1/shop-types`、`/v1/shops/**`
- `/v1/voucher-products/**`、`/v1/users/me/orders/**`、`/v1/users/me/vouchers/**`

团购商品详情返回 `VoucherProductDetailVO(product, shop)`；订单详情返回 `VoucherOrderDetailVO(order, product, shop)`。订单状态固定为 `PENDING_PAYMENT | PAID | CANCELED | REFUNDING | REFUNDED`。

全局 OpenAPI 声明 400 与 500，受保护接口声明 401，业务路由按真实行为补充 403、404、409、413，短信发送补充 503。所有 `$ref` 必须指向已注册 Schema。

## 数据与一致性

- 当前开发库使用 19 张表的可重建快照，结构和数据真源分别为 `schema-init.sql`、`seed-dev.sql`。
- 快照不使用 Flyway/Liquibase，不保留旧表、旧字段兼容或转换脚本。
- 关系表是点赞/关注事实；Post、评论、商户评分和销量字段是可由事实重算的聚合值。
- 下单条件扣减库存，取消只允许待支付订单并返库；支付确认按订单唯一约束幂等发券并累计销量。
- 用户订单和券包查询始终附带当前用户条件；券包查询前刷新已过期未使用券。
- `schema-init.sql` 含 `DROP TABLE`，仅允许在明确授权的 Demo 开发库执行，禁止用于生产数据库。

## 阶段状态

| 阶段 | 范围 | 状态 |
|---:|---|---|
| 1-2 | 城市、分区、关注、临时媒体 | 已实现 |
| 3-4 | 统一动态、媒体绑定、点赞 | 已实现 |
| 5 | 小程序导航、首页、发布器 | 已实现 |
| 6 | 推荐、关注、分区信息流 | 已实现 |
| 7-8 | 评论契约与全栈实现 | 已实现 |
| 9 | 商户点评 | 已实现 |
| 10 | 团购订单与券包 | 已实现 |
| 11 | 商户筛选与排序 | 已实现 |
| 12 | 商户详情与位置距离 | 已实现 |
| 13 | 运行时 OpenAPI | 已实现 |
| 14 | Demo 直接重构与旧链路退役 | 已实现 |

## 明确非目标

以下能力保持“未实现”，不纳入本轮闭环：搜索、通知、举报、审核、真实支付回调、退款、核销、商户后台、角色权限、生产短信供应商、多实例文件存储与多实例 Redis Stream 消费治理。

## 验收标准

- 默认 `mvn test` 不重建数据库；数据库闭环仅在 `RUN_DATABASE_INTEGRATION_TESTS=true` 时运行，并使用 Redis DB 15。
- 真实数据库测试重建授权开发库，覆盖 19 表/索引/旧表退役、聚合一致性、认证、分区关注、动态、评论、三类信息流、点评、库存返还、支付幂等、券过期和用户隔离。
- `RUN_INTEGRATION_TESTS=true` 验证真实 Knife4j、OpenAPI、Bearer、错误响应、全部 `$ref` 和 54 个操作。
- 小程序执行 `npm run build:npm` 与 `npm run verify`，覆盖包装详情、`CANCELED`、字符串大 ID 和页面契约。
- 不执行 `package`、`install`、`deploy`，不自动提交或推送。

## 验证记录

| 日期 | 范围 | 结果 |
|---|---|---|
| 2026-09-04 | 小程序契约 | `npm run build:npm` 成功构建 2 个依赖；`npm run verify`：33 个测试文件、120 项测试通过 |
| 2026-09-04 | 后端默认测试 | `mvn test`：83 项测试中 69 项通过、14 项按外部环境条件跳过，0 失败、0 错误 |
| 2026-09-04 | 数据库闭环 | `DatabaseBusinessClosureIntegrationTest`：3 项通过；授权开发库重建为 19 张业务表，无旧表，结束后恢复纯种子数据并清空 Redis DB 15 |
| 2026-09-04 | 运行时 HTTP/OpenAPI | `OpenApiAndAuthRuntimeTest`：6 项通过；`/doc.html` 200、Swagger UI 404、`/v3/api-docs` 含 54 个唯一操作，模拟短信/登录/当前用户/注销链路通过，商户写路由不存在 |
| 2026-09-04 | 编译与依赖 | `mvn -DskipTests compile` 与依赖树检查通过；实际解析 Spring Boot 3.5.11、Spring 6.2.16、MyBatis-Plus 3.5.12、Springdoc 2.8.9、Knife4j 5.2.1、Sa-Token 1.46.0、Redisson 3.52.0 |
| 2026-09-04 | 真机验收 | Android/iOS 未确认 |
