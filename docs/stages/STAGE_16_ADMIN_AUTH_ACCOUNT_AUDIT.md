# 阶段 16：管理员认证、账号与事务审计

```yaml
designVersion: 1
designStatus: 已冻结
implementationStatus: 开发中
dependsOn: 阶段 15 已实现
affectedEnds: 后端、管理 Web
```

## 目标

完成若依兼容基础版本、独立管理员认证、固定角色账号生命周期、限流、敏感字段投影和事务感知操作审计，并交付管理 Web 登录与账号管理。

## 进入条件与涉及端

- 进入条件为阶段 15 已实现，管理 Web 工程、Roamly 视觉令牌与测试基座可运行，后端当前管理源码改动可追溯。
- 涉及后端与管理 Web；消费者和商户小程序的路由、存储与 Token 不得改变。

## 状态机、数据与权限

- 管理员账号只在 `ACTIVE`（已启用）与 `DISABLED`（已停用）间迁移；新建或重置密码后保持账号状态，但必须先完成强制改密。
- 数据只涉及当前快照中的 `admin_user` 与 `operation_audit_log`；字段与索引以数据库契约为准，本阶段不得导入 `sys_*` 表。
- `PLATFORM_ADMIN`（平台超级管理员）管理账号，其他固定角色只能使用各自业务权限；后端固定权限码目录是唯一授权真源。

## 后端

- 对齐 Spring Boot 3.5.15、Springdoc 2.8.17、MyBatis-Plus 3.5.16、Hutool 5.8.43，保留 Java 21、Sa-Token 1.46.0、Jackson、Knife4j 和 Redisson 3.52.0。
- 管理域使用 `ADMIN`（管理端）Sa-Token；角色固定为 `PLATFORM_ADMIN`（平台超级管理员）、`MERCHANT_REVIEWER`（商户审核员）、`FINANCE`（财务管理员）。
- Redis 按标准化用户名和来源地址限流，连续 5 次失败锁定 15 分钟；账号不存在和密码错误返回相同响应。
- 审计只保存白名单摘要，在事务提交后记录成功；业务回滚记录失败，不得提前留下成功日志。
- 最后一个有效平台超级管理员使用数据库锁或等价串行保护，禁止自停用和并发绕过。

## 接口

- `POST /v1/admin/auth/login`、`GET /v1/admin/auth/me`、`PUT /v1/admin/auth/password`、`POST /v1/admin/auth/logout`。
- `GET/POST /v1/admin/users`、`GET/PUT /v1/admin/users/{id}`。
- `POST /v1/admin/users/{id}/activation`、`/disablement`、`/password-reset`；状态命令要求版本字段，不提供删除接口。
- 401、403、404、409、429、503 使用统一 `ErrorResult`，全部角色和状态提供中文释义。

## 管理 Web

- 完成 Roamly 主题、登录、强制改密、静态菜单、路由守卫、权限指令、标签页、账号列表和创建/编辑/启停/重置弹窗。
- 请求层只识别真实 HTTP 状态和字符串 `OK`（成功），Token 使用独立存储键。
- 管理员账号列表支持筛选、分页、加载、空态、失败重试和乐观锁冲突刷新。

## 失败处理

- 登录失败不泄露账号是否存在；连续失败返回 429 与剩余锁定信息，依赖服务不可用返回 503。
- 必须改密的会话访问其他资源返回稳定错误；账号版本冲突、最后平台管理员保护和自停用分别返回 409。
- 审计写入失败不得把已回滚业务记录为成功；敏感字段、密码和 Token 不进入日志或审计详情。

## 验收

- 三类 Token 互相拒绝；强制改密、会话失效、锁定恢复和权限矩阵测试通过。
- 数据库覆盖账号创建、启停、重置、并发保护与审计落库，业务表仍为当前 21 张。
- 真实 `/v3/api-docs`、Knife4j、管理员 HTTP 和管理 Web Vitest/Playwright 通过。
- 全量 Maven 测试、编译和依赖树通过后才能标记“已实现”。
