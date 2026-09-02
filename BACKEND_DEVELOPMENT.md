# 后端开发与设计契约

```yaml
version: 2
updatedAt: 2026-09-02
scope: Roamly 三模块后端、Sa-Token 会话与 API v1
reviewStatus: completed
implementationStatus: 已实现
```

## 当前工程事实

- 聚合模块：`ray-common`、`ray-pojo`、`ray-server`。
- 依赖方向：`ray-server -> ray-common + ray-pojo`，公共模块不依赖服务端模块。
- 技术栈：Java 21、Spring Boot 3.5.11、MyBatis-Plus 3.5.12、Sa-Token 1.46.0、Knife4j 5.2.1、Springdoc 2.8.9、RedisTemplate、Redisson 和 MySQL。
- 服务端主体保持 `controller / service / service.impl / mapper / config / handler` 技术分层；`utils` 仅作为横向能力命名空间，内部按职责拆为 `utils.cache / utils.converter / utils.generator / utils.validation`，本阶段不按业务域分包。
- Redis Key、TTL 和系统常量位于 `ray-common` 的 `com.ray.constant`，不属于服务端工具包。
- 资源位置：YAML、SQL、Lua 和 Mapper XML 均位于 `ray-server/src/main/resources`。
- HTTP 契约：OpenAPI 是接口真源，Controller 业务路由只保留 `/v1`；生产环境由 Nginx 将对外 `/api/v1` 剥离 `/api` 后转发为 `/v1`，旧接口没有兼容层。
- 客户端：同级 `Roamly-miniapp` 已同步 Bearer、字符串 ID、统一响应和 `/v1` 业务路径；体验版与正式版 `apiBaseUrl` 统一包含 `/api`。

## 模块职责

| 模块 | 职责 | 禁止依赖 |
|---|---|---|
| `ray-common` | 成功/错误响应、分页模型、业务异常 | `ray-pojo`、`ray-server` |
| `ray-pojo` | `entity / dto / vo`，Entity 保持 MyBatis 可变模型，DTO/简单 VO 使用 record | `ray-server` |
| `ray-server` | Web、业务、持久化、鉴权、配置、缓存、上传和运行时资源 | 无反向依赖限制 |

Controller 只处理 HTTP 契约、字符串 ID 转换与状态码，不直接执行 MyBatis 查询或文件写入，也不接收或返回 Entity。业务规则、事务与业务异常由 Service 负责。

## 公共常量与服务端横向能力包

| 所属模块 | 包 | 职责 | 当前类型 |
|---|---|---|---|
| `ray-common` | `com.ray.constant` | Redis Key、TTL 与系统常量 | `RedisConstants`、`SystemConstants` |
| `ray-server` | `com.ray.utils.cache` | Redis 缓存访问与逻辑过期数据 | `CacheClient`、`RedisData` |
| `ray-server` | `com.ray.utils.converter` | 字符串 ID 转换及 Entity 到 VO 映射 | `IdUtils`、`ViewMapper` |
| `ray-server` | `com.ray.utils.generator` | 基于 Redis 的分布式 ID 生成 | `RedisIdWorker` |
| `ray-server` | `com.ray.utils.validation` | 正则模式与输入格式校验 | `RegexPatterns`、`RegexUtils` |

`utils` 不直接存放类，只作为横向能力的父命名空间；新增工具必须进入能表达单一职责的二级包。跨模块共享且无业务依赖的常量放入 `ray-common`。数据库 Mapper 继续只放在 `mapper`，接口视图转换不得混入其中。

## 源码格式约定

- Java 源码统一使用 4 个空格缩进，不使用制表符，单行原则上不超过 120 个字符。
- 类型和方法上的注解一行一个；字段、构造器和方法之间保留一个空行，不把多个声明或语句压在同一行。
- import 一行一个且不使用通配符。Controller 直接导入 Swagger `ApiResponse` 和 `ApiResponses`；项目响应模型统一使用 `Result` 后缀，避免与依赖类型重名。
- 长参数列表、链式调用和复杂注解按语义换行，格式调整不得改变业务行为或 HTTP 契约。

## Sa-Token 会话契约

- Token 请求头：`Authorization: Bearer <opaque-token>`；OpenAPI 明确声明 HTTP Bearer 且 `bearerFormat: opaque`，不是 JWT。
- 会话持久化：`sa-token-redis-template:1.46.0`，不使用 Sa-Token Redisson DAO；现有 Redisson 仅用于分布式锁。
- Token 样式：UUID；总有效期 30 天；连续 7 天无访问失效；自动续签。
- 并发策略：允许同账号多设备登录，`is-share=false`，每次登录创建独立 Token。
- Token 来源：只读取 Header，关闭 Cookie 和请求体读取。
- 注销：`logout-range=TOKEN`，只注销当前请求的 Token。
- 业务访问：Service 通过 `CurrentUserProvider` 获取当前用户 ID，避免扩散框架静态 API。
- 短信验证码：继续使用 `login:code:*` Redis 键，Sa-Token 不管理验证码。
- 升级边界：旧自研 Token 不兼容且立即失效；旧 `login:token:*` 键不批量清理，等待自然过期。
- Redis 版本：RedisTemplate 插件要求 Redis 6.0+；目标部署环境上线前仍需确认版本。

公开接口按 HTTP 方法和路径精确放行，避免公开商户 GET 时误开放同路径 POST/PUT。其余后端 `/v1/**` 默认要求登录。当前数据库没有角色模型，因此商户和优惠券写接口仅校验登录，不声明虚构的角色权限或 403。

## API v1 状态

| 能力 | 路径范围 | 状态 |
|---|---|---|
| 短信验证码、登录、当前 Token 注销 | `/v1/auth/**` | 已实现 |
| 用户、资料、签到 | `/v1/users/**` | 已实现 |
| 关注与共同关注 | `/v1/users/**/following**` | 已实现 |
| 商户和分类 | `/v1/shops/**`、`/v1/shop-types` | 已实现 |
| 笔记、点赞与关注流 | `/v1/blogs/**`、`/v1/feeds/following` | 已实现 |
| 评论写入 | 无 HTTP 路由 | 未实现 |
| 优惠券与秒杀订单 | `/v1/vouchers`、`/v1/seckill-vouchers/**` | 已实现 |
| 图片上传与删除 | `/v1/blog-images` | 已实现 |
| 静态图片 | `/blogs/**` | 已实现 |

统一成功结构为 `Result<T>{code,message,data}`，错误结构为 `ErrorResult{code,message,fieldErrors}`。页码分页使用 `PageResult<T>`，关注流使用 `CursorPageResult<T>`。所有业务 ID 在 Java 内部为 `Long`，JSON、OpenAPI 和小程序均为字符串；发布无关联商户的笔记使用字符串 `"0"` 作为现有非空字段的兼容哨兵值。

分页约束为 `page >= 1`、`1 <= size <= 100`，默认 `page=1,size=10`。创建资源返回 201，普通查询/修改返回 200；注销、DELETE 和签到等无响应体操作返回 204。请求 DTO 使用 Jakarta Validation；Service 通过业务异常表达 400、404、409、413 和 500。

完整旧接口映射见 `API_V1_MIGRATION.md`。

## 网关路径边界

- Controller、Sa-Token 拦截器和 OpenAPI `paths` 统一使用 `/v1/**`。
- OpenAPI `servers.url` 为 `/api`，组合后的公开契约仍为 `/api/v1/**`。
- 生产 Nginx 使用 `location /api/` 与带尾斜杠的 `proxy_pass http://backend/;`，转发时剥离 `/api`。
- 小程序体验版和正式版 `apiBaseUrl` 包含 `/api`，各业务请求只写 `/v1/**`；本地直连 Spring Boot 时基础地址不包含 `/api`。

## 配置与运行安全

- 公共配置：`ray-server/src/main/resources/application.yml`。
- 环境差异：`application-dev.yml`、`application-test.yml`、`application-prod.yml`。
- 本地变量：根目录 `.env`，模板为 `.env.example`；密码不得提交到 YAML 或 Java 源码。
- RedisTemplate 与 Redisson 使用同一 `spring.data.redis.*` 主机、端口、密码和 database 配置。
- 测试 Redis 使用 `TEST_REDIS_DATABASE` 指定的独立 database，禁止清空业务 Redis。
- prod 关闭 Knife4j、OpenAPI 和 Swagger UI。
- 本地上传保存到 `ray.upload.image-dir`，限制 10MB，校验扩展名、Content-Type、文件签名和路径边界。

## 数据库边界

本轮没有修改数据库表、索引、初始化数据或业务 Redis 键结构，也没有引入 Flyway/Liquibase。结构和种子脚本只移动到 `ray-server/src/main/resources`。dev 的 `schema-init.sql` 含 `DROP TABLE`，最小启动和已有数据库联调必须设置 `spring.sql.init.mode=never`。

## 验证记录

| 日期 | 范围 | 结果 |
|---|---|---|
| 2026-09-02 | Maven 3.9.11、Java 21.0.11、Spring Boot 3.5.11 三模块 | Reactor `mvn clean test` 通过：22 项、0 失败，其中 10 项外部环境测试默认跳过；`mvn -DskipTests compile` 通过 |
| 2026-09-02 | Sa-Token、Springdoc、Redis/MyBatis 依赖 | 依赖树确认 Sa-Token 1.46.0、Springdoc 2.8.9、Spring Data Redis 3.5.9、Lettuce 6.6.0.RELEASE、MyBatis-Plus 3.5.12 |
| 2026-09-02 | 鉴权、OpenAPI 与最小启动 | 使用 test Profile、Redis database 15 和 `spring.sql.init.mode=never` 实际启动；5 项运行时测试通过，覆盖 32 个 v1 方法、全部 `$ref`、唯一 operationId、Knife4j、Swagger UI 关闭、Bearer 边界、独立 Token、当前 Token 注销和 RedisTemplate DAO |
| 2026-09-02 | 字符串 ID、公开路由、分页校验和图片边界 | 路由、ID、统一 400、空文件、超限、伪造类型、路径越界、保存和删除测试已通过 |
| 2026-09-02 | 微信小程序协议迁移 | `npm run verify` 通过，6 个测试文件、17 项测试 |
| 2026-09-02 | 类名与 OpenAPI Schema 统一 | 响应模型统一为 `Result`、`ErrorResult`、`PageResult`、`CursorPageResult`；Swagger 响应注解改为普通导入；真实 OpenAPI/Sa-Token 测试 5 项通过 |
| 2026-09-02 | Nginx `/api` 与后端 `/v1` 路由分层 | Controller、Sa-Token 和 OpenAPI paths 切换为 `/v1`，OpenAPI server 为 `/api`；真实运行测试 5 项通过，小程序 6 个测试文件、18 项测试通过 |
| 2026-09-02 | 公共常量与服务端横向能力包整理 | 常量迁移至 `ray-common` 的 `com.ray.constant`；`ray-server` 的工具类和对应测试归入 `utils.cache / utils.converter / utils.generator / utils.validation`，`utils` 根包不直接存放类；干净构建确认旧包 class 已清除，仅保留 `com.ray.utils.cache.CacheClient`，避免重复 Bean；Reactor 测试共 22 项、0 失败、10 项外部环境测试跳过，真实启动测试 5 项通过，编译通过 |

运行时验收未执行短信登录和会写入业务表的接口；数据库初始化已明确关闭。需要外部服务的运行时测试由 `RUN_INTEGRATION_TESTS=true` 显式启用，常规 `mvn test` 不访问开发数据库或业务 Redis。

## 当前风险与后续项

- 评论表存在，但评论 HTTP 能力保持“未实现”。
- `tb_follow` 缺少数据库唯一约束，关注去重仍由应用层负责。
- 秒杀的 Redis Stream 消费者是单进程固定消费者名，生产多实例前应规划实例唯一 consumer name 和失败消息治理。
- 本地文件上传不具备多实例共享能力，生产扩容前应迁移对象存储。
- 数据库仍使用可重建式开发脚本，后续结构演进应引入有序迁移。
