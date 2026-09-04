# 阶段 13：运行时 OpenAPI 契约

状态：已实现（Demo 范围）
更新时间：2026-09-04

真实运行时契约固定为 54 个唯一 `operationId`。测试校验 `/doc.html` 可用、`/v3/api-docs` 可用、Swagger UI 禁用、Bearer 安全声明、公开接口安全覆盖、全部 `$ref`、关键 Schema 以及 400/401/403/404/409/413/500/503 响应。

运行时测试由 `RUN_INTEGRATION_TESTS=true` 显式开启，使用 test Profile、Redis DB 15，并禁止 SQL 自动初始化。
