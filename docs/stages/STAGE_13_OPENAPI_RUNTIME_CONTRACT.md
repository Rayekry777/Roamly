# 阶段 13：运行时 OpenAPI 契约验收完善

状态：开发中
更新时间：2026-09-03

本阶段只完善隔离运行环境中的 OpenAPI/Sa-Token 验收用例，不改变业务 HTTP 路径、DTO、VO、SQL 或小程序行为。

## 1. 验收边界

`OpenApiAndAuthRuntimeTest` 在 `RUN_INTEGRATION_TESTS=true` 时以 `test` Profile 启动服务，并强制 `spring.sql.init.mode=never`。它连接独立 MySQL/Redis，不能使用开发库或触发现有含 `DROP TABLE` 的初始化脚本。

本次将如下已经交付的路由加入“实际 OpenAPI 路由集合必须完全相等”的校验：

- 商户关联探店动态、点评 CRUD。
- 商户团购商品、团购商品详情与创建订单。
- 当前用户订单列表、订单详情、取消订单、券包列表与券详情。

同时校验这些接口的关键 404/409 业务响应组件，以及商户详情坐标参数和点评、商品、订单、券包 Schema 的存在。

## 2. 验收规则

- OpenAPI Bearer 仍为 `http/bearer/opaque`，对外 Server 仍为 `/api`。
- 每个实际路由必须提供唯一 operationId、400 和 500 响应；错误结构继续引用 `ErrorResult`。
- 运行时测试不代替 Controller/Service 单元测试，也不改变公开读取和登录接口的安全声明。
- 发现运行时路由集合或 Schema 不一致时，优先修正 Controller/OpenAPI 契约；不得通过放宽断言掩盖缺失接口。

## 3. 完成条件

已完成：运行时路由清单和关键断言已覆盖当前已实现的团购、订单、券包、点评及商户关联动态接口。

待隔离环境完成：设置 `RUN_INTEGRATION_TESTS=true` 和独立连接信息后执行运行时测试，确认 `/doc.html`、`/v3/api-docs`、Sa-Token RedisTemplate、全部路由、Schema 引用与安全声明真实可用。
