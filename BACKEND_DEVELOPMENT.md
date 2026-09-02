# 后端开发与设计契约

```yaml
version: 1
updatedAt: 2026-09-02
scope: Roamly 本地生活点评与团购单体服务
reviewStatus: completed
implementationStatus: 开发中
```

## 当前工程事实

- 模块：单体 Spring Boot 应用（`com.ray`）。
- 技术栈：Spring Boot 3.5.11、Java 21、MyBatis-Plus、MySQL、Redis、Redisson、Hutool。
- 认证：基于 Redis 的短信登录与 Token 拦截器。
- 数据库：MySQL，结构脚本为 `src/main/resources/schema-init.sql`，开发数据脚本为 `src/main/resources/seed-dev.sql`。
- 外部依赖：Redis 单节点；连接参数由环境变量注入。
- 图片：上传文件保存到 `ray.upload.image-dir`，`/blogs/**` 由后端映射为静态资源；前端缺失图片统一回退到本地 SVG 资源。
- 客户端：当前重点支持同级 `Roamly-miniapp`；`Roamly-web` 暂时保留但不要求并行更新。小程序开发说明由小程序工程自行维护。

## 接口状态

| 能力 | 路径范围 | 状态 | 证据 |
|---|---|---|---|
| 用户登录、验证码、登出、签到 | `/user/**` | 已实现 | `UserController`、`IUserService` |
| 商户与分类查询 | `/shop/**`、`/shop-type/**` | 已实现 | 对应 Controller/Service |
| 探店笔记与评论 | `/blog/**`、`/blog-comments/**` | 开发中 | 评论写接口仍待补齐 |
| 优惠券与秒杀订单 | `/voucher/**`、`/voucher-order/**` | 已实现 | 对应 Controller/Service |
| 图片上传与删除 | `/upload/**` | 已实现 | 登录校验、10MB 限制、格式签名、路径越界和启动目录检查均已加入 |
| 微信小程序客户端适配 | 同级 `Roamly-miniapp` | 已实现 | 主包四 Tab、三组分包、统一请求/认证/上传和体验版说明已完成 |

## 客户端访问矩阵

| 访问级别 | 能力与路径 |
|---|---|
| 公开 | `POST /user/code`、`POST /user/login`、`GET /shop/*`、`GET /shop/of/type`、`GET /shop/of/name`、`GET /shop-type/list`、`GET /voucher/list/**`、`GET /blog/hot`、`GET /blog/{id}`、`GET /blog/of/user`、`GET /blog/likes/**`、`GET /user/{id}`、`GET /user/info/{id}`、`GET /blogs/**` |
| 登录后 | 用户登出、当前用户、签到；关注流、点赞、关注；发布笔记、我的笔记；`/upload/**`；`/voucher-order/**`；商户和优惠券写接口 |

小程序继续在 `authorization` 请求头中传递短信登录接口返回的 Token。401 时客户端清理本地 Token、记录当前页面并跳转登录，成功后回到原页面。上传接口不允许匿名访问。

## 图片上传策略

- `POST /upload/blog` 字段名为 `file`，单张最大 10MB，仅允许 JPEG、PNG、WebP。
- 后端同时校验扩展名、Content-Type 和文件签名；生成随机分层路径并校验目标路径不能越过上传根目录。
- 应用启动时创建并检查 `ray.upload.image-dir` 可写；目录不可用时启动失败，避免运行期静默丢图。
- `/blogs/**` 使用 7 天公共缓存。删除接口仅用于清理登录用户在发布流程中未使用的上传文件。
- 上传目录仍为本地磁盘；生产运维应纳入每日增量备份，并对所在磁盘使用率设置 70% 预警、85% 严重告警。
- 本阶段不迁移对象存储，不修改数据库结构。

OpenAPI 文档入口为 `/doc.html`，原始数据入口为 `/v3/api-docs`；Swagger UI 已关闭。

## 配置与敏感信息

- 公共配置：`src/main/resources/application.yml`。
- 环境差异：`src/main/resources/application-dev.yml`、`application-test.yml`、`application-prod.yml`。
- 环境变量模板：`.env.example`；真实 `.env` 不提交。
- 数据库、Redis 密码不得写入 YAML 或 Java 源码。
- MySQL `caching_sha2_password` 本地开发连接通过 `DB_ALLOW_PUBLIC_KEY_RETRIEVAL=true` 显式允许公钥获取；公共配置默认关闭，生产环境应优先使用 TLS，不继承开发宽松设置。

## 验证记录

| 日期 | 范围 | 结果 |
|---|---|---|
| 2026-09-01 | Spring Boot 3 / Java 21 依赖升级 | `mvn -DskipTests test`、依赖树均通过 |
| 2026-09-01 | API 文档、参数校验、登出和上传安全优化 | `mvn -DskipTests test-compile`、`mvn -Dtest=NormalTest test` 均通过 |
| 2026-09-01 | 开发数据库脚本拆分 | `schema-init.sql` 含 10 张表结构，`seed-dev.sql` 含 33 条初始化数据，Maven 编译通过 |
| 2026-09-01 | MySQL 严格日期兼容修复 | 秒杀券生效/失效时间改为可空合法默认值；编译通过，启动已执行至 Redis 连接阶段 |
| 2026-09-01 | 秒杀订单 Stream 消费修复 | 自动初始化 `stream.orders` 消费组，统一确认消息使用的流名，并抑制消费组尚未创建时的重复异常日志；Maven 编译通过 |
| 2026-09-01 | 微信小程序客户端与后端适配 | 小程序 `npm run verify` 通过（4 个测试文件、7 个测试）；后端 `UploadControllerTest` 与 `NormalTest` 共 3 项通过，`mvn -DskipTests compile` 与相关依赖树检查通过 |
| 2026-09-02 | MySQL 公钥获取配置修复 | `mvn -DskipTests compile` 通过；使用 `spring.sql.init.mode=never` 与随机端口完成最小启动，应用成功连接 MySQL 并启动，未执行初始化脚本 |

## 当前风险

- `schema-init.sql` 和 `seed-dev.sql` 目前仍是开发初始化脚本，未纳入 Flyway/Liquibase 迁移管理；`dp.sql` 仅作为历史备份。
- 应用启动需要提前配置 `DB_PASSWORD` 和 `REDIS_PASSWORD` 环境变量。
