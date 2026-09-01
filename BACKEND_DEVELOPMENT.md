# 后端开发与设计契约

```yaml
version: 1
updatedAt: 2026-09-01
scope: Roamly 本地生活点评与团购单体服务
reviewStatus: pending
implementationStatus: 开发中
```

## 当前工程事实

- 模块：单体 Spring Boot 应用（`com.ray`）。
- 技术栈：Spring Boot 3.5.11、Java 21、MyBatis-Plus、MySQL、Redis、Redisson、Hutool。
- 认证：基于 Redis 的短信登录与 Token 拦截器。
- 数据库：MySQL，结构脚本为 `src/main/resources/schema-init.sql`，开发数据脚本为 `src/main/resources/seed-dev.sql`。
- 外部依赖：Redis 单节点；连接参数由环境变量注入。

## 接口状态

| 能力 | 路径范围 | 状态 | 证据 |
|---|---|---|---|
| 用户登录、验证码、登出、签到 | `/user/**` | 已实现 | `UserController`、`IUserService` |
| 商户与分类查询 | `/shop/**`、`/shop-type/**` | 已实现 | 对应 Controller/Service |
| 探店笔记与评论 | `/blog/**`、`/blog-comments/**` | 开发中 | 评论写接口仍待补齐 |
| 优惠券与秒杀订单 | `/voucher/**`、`/voucher-order/**` | 已实现 | 对应 Controller/Service |
| 图片上传与删除 | `/upload/**` | 已实现 | 路径规范化与越界校验已加入 |

OpenAPI 文档入口为 `/doc.html`，原始数据入口为 `/v3/api-docs`；Swagger UI 已关闭。

## 配置与敏感信息

- 公共配置：`src/main/resources/application.yml`。
- 环境差异：`src/main/resources/application-dev.yml`、`application-test.yml`、`application-prod.yml`。
- 环境变量模板：`.env.example`；真实 `.env` 不提交。
- 数据库、Redis 密码不得写入 YAML 或 Java 源码。

## 验证记录

| 日期 | 范围 | 结果 |
|---|---|---|
| 2026-09-01 | Spring Boot 3 / Java 21 依赖升级 | `mvn -DskipTests test`、依赖树均通过 |
| 2026-09-01 | API 文档、参数校验、登出和上传安全优化 | `mvn -DskipTests test-compile`、`mvn -Dtest=NormalTest test` 均通过 |
| 2026-09-01 | 开发数据库脚本拆分 | `schema-init.sql` 含 10 张表结构，`seed-dev.sql` 含 33 条初始化数据，Maven 编译通过 |
| 2026-09-01 | MySQL 严格日期兼容修复 | 秒杀券生效/失效时间改为可空合法默认值；编译通过，启动已执行至 Redis 连接阶段 |
| 2026-09-01 | 秒杀订单 Stream 消费修复 | 自动初始化 `stream.orders` 消费组，统一确认消息使用的流名，并抑制消费组尚未创建时的重复异常日志；Maven 编译通过 |

## 当前风险

- `schema-init.sql` 和 `seed-dev.sql` 目前仍是开发初始化脚本，未纳入 Flyway/Liquibase 迁移管理；`dp.sql` 仅作为历史备份。
- 应用启动需要提前配置 `DB_PASSWORD` 和 `REDIS_PASSWORD` 环境变量。
