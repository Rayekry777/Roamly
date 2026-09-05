# Roamly 后端｜技术栈与解决方案

Roamly 后端是面向消费者小程序、商户小程序和管理 Web 的本地生活点评与团购交易服务。文档重点说明技术选型和关键问题的解决方式；业务页面和接口清单以各端契约文档为准。

系统采用模块化单体架构：三端通过统一的 `/v1` REST 契约访问服务端，交易事实集中在 MySQL，Redis 负责会话、缓存和跨实例事件。商户入驻、商品审核、下单支付、发券退款、核销、佣金账本、结算和审计均在同一套状态约束下闭环。

项目参考 `RuoYi-Vue-Plus v5.6.2` 的成熟能力进行取舍，但不是若依套壳：不引入若依父 POM、完整 BOM、系统表或通用后台模型，而是围绕 Roamly 的三端身份、门店数据范围和交易状态机重新实现。

## 技术栈

| 领域 | 技术与版本 | 用途 |
|---|---|---|
| 基础框架 | Java 21、Spring Boot 3.5.15、Maven | 应用运行、依赖管理与三模块构建 |
| 持久化 | MySQL 8.x、MyBatis-Plus 3.5.16 | 业务数据、条件更新、分页与事务事实 |
| 认证授权 | Sa-Token 1.46.0、BCrypt | 消费者、商户、管理员三套独立登录域和权限校验 |
| 缓存与协调 | Redis 6.0+、Redisson 3.52.0 | Token、缓存、限流、事件分发、分布式锁与幂等协调 |
| 接口文档 | Knife4j 5.2.1、Springdoc 2.8.17 | OpenAPI 契约、在线调试与模型校验 |
| 参数校验 | Jakarta Validation | DTO 约束、稳定错误码与中文校验消息 |
| 对象存储 | AWS SDK S3 2.28.22 | 生产 S3 兼容存储；开发和测试使用本地存储适配器 |
| 实时通信 | Spring WebSocket、SSE、Redis Pub/Sub | 商户状态刷新、管理资源变更通知与多实例事件分发 |
| 后台任务 | Spring Scheduling | 关单、券过期、上下架、媒体清理、退款重试和 T+1 结算 |
| 文件导出 | 自研 OOXML Writer | 生成受权限控制的同步 XLSX 导出 |
| 工具库 | Hutool 5.8.43 | 通用 Java 工具能力 |

## 技术解决方案总览

```text
消费者小程序 ─┐
商户小程序   ─┼─ HTTPS /v1 ─► Spring Boot 业务服务 ─► MySQL（交易事实）
管理 Web     ─┘                       │              └► S3/本地媒体存储
                                      └─► Redis（会话、缓存、锁、幂等、事件）
```

- **统一契约**：Controller 只暴露 DTO/VO，响应统一为 `Result`/`ErrorResult`；分页同时支持页码和游标，第三方 SDK 类型不进入公共模型。
- **事实与通知分离**：订单、支付、退款、核销、账本和结算以数据库状态为准，WebSocket/SSE/Redis Pub/Sub 只发送资源变更通知，客户端收到后回查 REST。
- **可替换基础设施**：对象存储、定时任务、XLSX 导出和分布式锁均通过适配边界接入，开发环境可使用本地实现，生产配置不完整时显式失败。

## 核心解决方案

### 三端身份与权限隔离

- Sa-Token 分别维护 `CONSUMER`、`MERCHANT`、`ADMIN` 三套登录逻辑、Redis 键空间和路由拦截，Token 不能跨端复用。
- 管理端采用平台管理员、商户审核员、财务管理员三种固定角色；商户端采用店主、店长、核销员三种固定角色。
- 权限码和门店数据范围由服务端强制执行，客户端菜单隐藏不能代替接口鉴权。
- 管理员密码使用 BCrypt，并实现登录失败锁定、首次改密、会话失效以及最后一个平台管理员保护。

### 交易一致性与防重复

- 金额、优惠和库存均由服务端计算，客户端只提交购买意图。
- 下单使用 Redisson 原生 `RLock` 按“用户 + 商品”隔离竞争，库存通过数据库条件更新保证最终正确性。
- 审核、下单、支付、退款、核销、撤销和结算重试使用 `Idempotency-Key`；请求指纹、唯一索引和状态条件更新共同防止重复执行。
- 支付确认按订单唯一事实增加销量并按购买数量发券；关单、支付和退款竞态由数据库状态机决定唯一结果。
- 佣金与资金账本采用只追加模型，已结算退款进入后续负向调整，不覆盖历史金额或历史批次。

### 安全与审计

- 操作审计覆盖登录、账号治理、商户审核、商品审核、退款、核销、佣金和结算，只保存白名单摘要。
- 联系方式、证照、结算资料和消费者信息按权限使用显式响应投影脱敏，日志不记录密码、Token 或完整敏感字段。
- 动态券码不暴露裸券 ID；券码通过 HMAC 索引定位，核销预览不改变券状态。
- Jakarta Validation 约束与 OpenAPI 模型保持一致，并返回中文校验消息和稳定业务错误码。

### 实时刷新与多实例分发

- 商户端通过 WebSocket 接收核销、订单等资源失效事件，并按门店范围隔离会话。
- 管理端通过一次性短期票据建立 SSE 连接，按权限接收审核、退款和结算资源变更通知。
- Redis 负责跨实例事件分发；实时消息只通知“资源发生变化”，客户端随后回查 REST 接口，消息本身不作为资金或履约事实。

### 可替换的基础设施边界

- 对象存储通过自有端口隔离：dev/test 使用本地文件适配器，prod 使用 AWS SDK S3 适配器；生产配置不完整时直接启动失败，不静默回退本地目录。
- 定时任务当前使用 Spring Scheduling 调用幂等任务服务，可在生产阶段替换为 SnailJob 执行器，不改变业务 Service。
- XLSX 当前由自研 OOXML Writer 同步生成，可替换为 Fesod 适配器，不改变权限、字段和审计边界。
- 分布式锁当前直接使用 Redisson `RLock`，保留 Lock4j 适配空间，但数据库约束始终是最终一致性保障。

## 若依扩展取舍

当前已经实际引入或实现：

- Sa-Token：三端认证、权限与会话隔离。
- Redisson：分布式锁、接口限流和幂等协调。
- MyBatis-Plus、Hutool、Knife4j、Springdoc、AWS SDK S3。
- Redis Token/缓存/事件分发、WebSocket、SSE。
- 操作审计、权限感知脱敏、`Idempotency-Key` 防重和 Validation 中文校验。

当前采用等价实现、未直接引入对应扩展：

| 若依扩展 | Roamly 当前方案 | 原因 |
|---|---|---|
| Lock4j | Redisson 原生 `RLock` | 直接完成当前并发协调，数据库继续负责最终正确性，并保留后续适配边界 |
| SnailJob | Spring Scheduling + 幂等任务服务 | 满足当前 Demo 调度规模，任务允许重复投递且结果幂等 |
| Fesod | 自研 OOXML Writer | 满足同步 XLSX 导出，保持权限和字段白名单可控 |

动态 RBAC、多租户、部门岗位、通用字典、代码生成、动态数据源、社交登录、WarmFlow、Spring Boot Admin 和 SkyWalking 尚未引入。完整版本依据、采用矩阵和接入门禁见[若依扩展技术决策](docs/architecture/RUOYI_EXTENSION_COMPATIBILITY.md)。

## 工程结构

```text
Roamly
├─ ray-common   统一响应、分页、错误码、权限码和公共类型
├─ ray-pojo     DTO、VO、Entity 与业务枚举
└─ ray-server   Controller、Service、Mapper、配置、任务和基础设施适配器
```

依赖方向固定为 `ray-server -> ray-common + ray-pojo`。服务端保持 `controller / service / service.impl / mapper / config / handler` 分层，第三方 SDK 类型不会进入公共 DTO 或 OpenAPI 契约。

## Demo 数据与测试账号

dev 快照包含 33 张非空业务表，覆盖社区互动、四类券、订单、支付、退款、员工、核销、账本、结算和审计状态。

| 端 | 推荐账号 | 开发凭据 |
|---|---|---|
| 消费者小程序 | `13456789011` | Mock 验证码 `123456` |
| 商户小程序 | `13900000001` | Mock 验证码 `123456` |
| 管理 Web | `admin` | 密码 `Roamly123` |

账号只用于可重建的 dev 数据库，禁止复制到生产。附加角色账号、数据数量和代表性业务链路见[数据库结构文档](DATABASE_SCHEMA.md#开发测试账号)。

## 本地启动

环境要求：Java 21、Maven 3.9+、MySQL 8.x、Redis 6.0+；开发环境已使用 Redis 7.4.10 验证。

1. 复制 `.env.example` 为 `.env`，填写数据库、Redis 和本地上传目录配置；真实 `.env` 已被 Git 忽略。
2. 以 Maven 工程导入根 `pom.xml`，Project SDK 选择 Java 21。
3. 运行 `ray-server` 中的 `com.ray.RayRoamlyApplication`，Working directory 设置为仓库根目录。

默认 dev 配置会执行包含 `DROP TABLE` 的完整初始化脚本，只能连接明确允许重建的隔离开发库。连接已有数据库时必须设置：

```text
SPRING_SQL_INIT_MODE=never
```

开发环境接口入口：

- Knife4j：`http://localhost:8081/doc.html`
- OpenAPI：`http://localhost:8081/v3/api-docs`
- Swagger UI：已禁用
- prod Profile：同时关闭 Knife4j 和 OpenAPI 数据端点

## 生产路由

生产环境由 Nginx 提供 `/api` 前缀，并在转发时剥离：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8081/;
}
```

外部 `/api/v1/users/me` 会转发为后端 `/v1/users/me`；`proxy_pass` 末尾的 `/` 不能省略。客户端 `apiBaseUrl` 配置为 `https://example.com/api`，业务请求路径只写 `/v1/**`，避免形成 `/api/api/v1/**`。

## 验证与文档

```text
mvn test
mvn -DskipTests compile
mvn -pl ray-server -am dependency:tree
```

最近一次默认测试共 159 项，0 失败、0 错误、22 项按环境开关跳过；真实数据库闭环测试 9 项全部通过，并在结束后恢复纯种子状态。

- [后端开发契约](BACKEND_DEVELOPMENT.md)
- [数据库结构与测试账号](DATABASE_SCHEMA.md)
- [若依扩展技术决策](docs/architecture/RUOYI_EXTENSION_COMPATIBILITY.md)
- [四端交付路线图](docs/roadmap/FOUR_END_DELIVERY_ROADMAP.md)
- [阶段 15 至 30 设计](docs/stages/STAGE_15_FOUR_END_CONTRACT_AND_FOUNDATION.md)
- [管理 Web](../Roamly-admin-web/README.md)
- [商户小程序](../Roamly-merchant-miniapp/README.md)
- [消费者小程序](../Roamly-miniapp/README.md)

当前开发阶段不执行 `package`、`install`、部署或 Docker 产物生成。
