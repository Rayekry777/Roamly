# Roamly 后端

Roamly 是基于 Java 21 和 Spring Boot 3.5.11 的本地生活点评与团购后端。当前版本采用 Maven 三模块结构；Controller 使用 `/v1` 版本路由，生产环境由 Nginx 提供对外 `/api` 前缀。

## 模块导航

- `ray-common`：统一响应、分页模型、业务异常、常量等无业务依赖的公共能力。
- `ray-pojo`：MyBatis Entity、请求 DTO 和响应 VO。
- `ray-server`：启动类、Controller、Service、Mapper、配置、异常处理，以及按职责拆分的缓存、转换、ID 生成和校验能力。

依赖方向固定为 `ray-server -> ray-common + ray-pojo`。服务端继续使用 `controller / service / service.impl / mapper / config / handler` 技术分层，暂不按业务域拆包。常量位于 `ray-common` 的 `constant` 包；`ray-server` 的 `utils` 只作为横向能力命名空间，类分别放入 `utils.cache / utils.converter / utils.generator / utils.validation` 二级职责包，不直接放在 `utils` 下。

## 本地环境

需要 Java 21、Maven 3.9+、MySQL 8.x 和 Redis 6.0+。Sa-Token 1.46.0 使用 RedisTemplate 保存会话，开发环境已按 Redis 7.4.10 验证配置兼容性。

复制 `.env.example` 为 `.env`，填写数据库、Redis 和上传目录所需参数。真实 `.env` 已被 Git 忽略。已有数据库上启动时必须设置：

```text
SPRING_SQL_INIT_MODE=never
```

默认 dev 配置会执行包含 `DROP TABLE` 的开发初始化脚本，只能用于明确允许重建的隔离开发库。

## IDEA 启动

1. 以 Maven 工程导入根 `pom.xml`，Project SDK 选择 Java 21。
2. 运行 `ray-server` 中的 `com.ray.RayRoamlyApplication`。
3. Working directory 设置为仓库根目录，使 `.env` 能被加载。
4. 对已有数据库增加环境变量 `SPRING_SQL_INIT_MODE=never`。

开发环境 Knife4j 入口为 `http://localhost:8081/doc.html`，OpenAPI 数据为 `http://localhost:8081/v3/api-docs`。Swagger UI 已禁用，prod Profile 会同时关闭 Knife4j 和 OpenAPI 数据端点。

## Nginx 路由

生产环境使用 `/api` 作为网关转发前缀，Nginx 转发时将其剥离：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8081/;
}
```

因此外部 `/api/v1/users/me` 会转发为后端 `/v1/users/me`。`proxy_pass` 末尾的 `/` 不得遗漏。客户端生产 `apiBaseUrl` 配置为 `https://example.com/api`，业务请求路径只写 `/v1/**`，避免出现 `/api/api/v1/**`。

## 验证命令

```text
mvn test
mvn -DskipTests compile
mvn -pl ray-server -am dependency:tree
```

本轮不执行 `package`、`install`、部署或 Docker 产物生成。详细设计与状态见 [后端开发与全栈设计契约](docs/BACKEND_DEVELOPMENT.md)，当前数据库结构见 [数据库结构文档](docs/DATABASE_SCHEMA.md)。
