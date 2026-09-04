# 阶段 13：运行时 OpenAPI 契约

状态：已实现（Demo 范围）
更新时间：2026-09-04

阶段 13 的运行时基线为 54 个唯一 `operationId`。后续阶段在不破坏既有 `/v1` 语义的前提下继续增加管理、商户和交易能力；当前最终运行时以阶段 30 验收为准，共 138 个唯一 `operationId`。测试持续校验 `/doc.html` 可用、`/v3/api-docs` 可用、Swagger UI 禁用、Bearer 安全声明、公开接口安全覆盖、全部 `$ref`、关键 Schema 以及 400/401/403/404/409/413/500/503 响应。

运行时测试由 `RUN_INTEGRATION_TESTS=true` 显式开启，使用 test Profile、Redis DB 15，并禁止 SQL 自动初始化。
