# Roamly 若依后端扩展技术决策

```yaml
decisionVersion: 3
decisionStatus: 已冻结
frozenAt: 2026-09-04
implementationStatus: 已实现
extensionImplementationStatus: 开发中
updatedAt: 2026-09-05
referenceBackend: RuoYi-Vue-Plus v5.6.2
referenceCommit: 8136a0191a2258c0e1b36a8146a1c5ebc070c139
```

阶段 16 至 30 所采用的若依兼容能力已在 Demo 范围完成；`extensionImplementationStatus`（扩展实现状态）仅表示生产短信、WarmFlow、Spring Boot Admin、SkyWalking 等后续或部署扩展仍按各自接入计划推进，不影响当前阶段验收。

## 文档职责

本文只记录后端对 `RuoYi-Vue-Plus v5.6.2` 的版本事实、能力取舍、模块边界和接入门禁。管理 Web 对 `plus-ui` 的裁剪由其自身契约管理；本文不定义任何客户端页面、路由或视觉。

## 版本事实

冻结时核对的上游提交为 `8136a0191a2258c0e1b36a8146a1c5ebc070c139`，包含 Spring Boot 3.5.15、Springdoc 2.8.17、MyBatis-Plus 3.5.16、Hutool 5.8.43、Redisson 3.52.0、Lock4j 2.2.7、SnailJob 1.10.0、AWS SDK S3 2.28.22、sms4j 3.3.5、WarmFlow 1.8.5 和 Spring Boot Admin 3.5.8。

Roamly 阶段 16 对齐 Spring Boot、Springdoc、MyBatis-Plus 和 Hutool 的上游兼容版本，保留 Java 21、Sa-Token 1.46.0、Jackson、Knife4j 和 Redisson 3.52.0。不得导入若依父 POM、完整 BOM 或用传递依赖静默覆盖 Roamly 版本。

## 采用矩阵

| 若依能力 | Roamly 后端用途 | 决策 | 阶段 |
|---|---|---|---:|
| Jackson 封装 | 统一 JSON、日期、枚举和缓存序列化 | 沿用 Spring Jackson，吸收配置思路，不引入 Fastjson | 16 |
| Sa-Token 封装 | 三类 Token、权限与会话隔离 | 按 Roamly 重写，禁止单一登录模型和租户假设 | 16-17 |
| 接口限流 | 短信、管理员登录、核销预览与高风险命令 | 参考 Redisson 限流与 SpEL 键，返回 429 | 16、26 |
| 敏感数据 | 营业执照、联系人、结算资料和消费者摘要 | 参考权限感知脱敏，使用显式响应投影，避免隐藏序列化副作用 | 18-19、26-29 |
| 操作日志 | 登录、账号、审核、退款、核销、佣金和结算 | 参考注解与事件，但只保存白名单摘要并在事务结果确定后落库 | 16-29 |
| 分布式幂等 | 审核、下单、支付、退款、核销和结算重试 | 参考防重键，不复制若依请求体 MD5 规则；使用显式 `Idempotency-Key` | 19-29 |
| Lock4j | 下单、核销、退款和结算并发协调 | 引入 Lock4j 2.2.7，底层 Redisson；数据库仍负责最终正确性 | 22、24、26、29 |
| SnailJob | 关单、过期、上下架、清理、退款重试和 T+1 结算 | 引入独立执行器和管理服务，任务处理器必须幂等 | 23-29 |
| OSS/S3 | 用户媒体、经营资质、券图片和导出文件 | 建立自有存储端口，dev/test 本地，prod 使用 S3 兼容实现 | 18 |
| sms4j | 消费者与商户验证码 | 保留 Mock/禁用模式，生产适配时不改变认证接口 | 生产扩展 |
| Apache Fesod | 管理列表同步 XLSX 导出 | 引入受权限控制的同步导出，不新增业务任务表 | 29 |
| WebSocket | 商户多设备核销状态刷新 | 按 `MERCHANT`（商户端）Token 重写并使用 Redis 分发 | 27 |
| SSE | 管理审核、退款与结算资源刷新 | 使用一次性短期票据建立管理事件流 | 27 |
| Validation 国际化 | 所有公开 DTO 与命令 | 保留稳定错误码，默认简体中文消息，约束与 OpenAPI 一致 | 持续 |
| WarmFlow | 未来多级审核与异常审批 | 只保留适配边界，不进入当前单级状态机 | 后续扩展 |
| Spring Boot Admin | 服务健康、指标和日志 | 独立运维进程，不使用业务管理员或业务数据库 | 部署扩展 |
| SkyWalking | HTTP、Redis、任务与存储链路 | 通过 Java Agent 接入，traceId 可进入日志和审计 | 部署扩展 |

## 明确拒绝

- 不引入 `sys_user`、`sys_role`、`sys_menu`、动态 RBAC、多租户、部门岗位、通用字典和代码生成。
- 不引入动态数据源、社交登录、请求加密和通用 XSS 内容改写；HTTPS、DTO 校验、权限和输出编码各自负责安全边界。
- 不使用通用翻译注解在序列化期间查询数据库；名称和中文标签由显式查询与装配器返回，避免隐藏 N+1。
- 不把 Spring Boot Admin、SnailJob 管理页面或 WarmFlow 设计器嵌入业务管理 Web。

## 模块边界

- 业务 Service 依赖自有端口，不直接依赖 S3、sms4j、SnailJob、WebSocket、SSE 或 WarmFlow SDK。
- 可选实现位于独立 `ray-integration-*` 模块；SnailJob 执行器位于 `ray-job`，监控独立部署。
- 第三方异常统一转换为 Roamly 字符串错误码和真实 HTTP 状态，第三方类型不得出现在 DTO、VO 或 OpenAPI。
- 未启用模块不得进入默认依赖树；prod 不允许使用 Mock 短信、Mock 支付或默认对象存储凭据。

## 一致性边界

- Redis 防重和分布式锁只能降低竞争，数据库唯一约束、条件更新和事务仍是订单、库存、券、退款和账本事实来源。
- WebSocket、SSE、短信和邮件只发送通知；客户端收到资源 ID 后回查接口，不以消息内容直接改写资金或履约状态。
- SnailJob 可重复投递；任务以业务资源 ID 和动作类型构造幂等键，并保留查询/支付惰性关单。
- 操作审计区分成功与失败，不记录密码、Token、完整证件、结算账号或完整请求体。

## 接入门禁

每个能力启用前必须先冻结对应阶段，核对官方兼容矩阵和 Maven 依赖树，补齐配置分层、关闭模式、失败降级、权限、数据隔离、数据库影响、OpenAPI、单元测试、真实中间件测试和默认启动验证。只加入依赖不得标记为“已实现”。
