# 阶段 29：T+1 Mock 结算与 XLSX 导出

```yaml
designVersion: 1
designStatus: 已冻结
implementationStatus: 未实现
dependsOn: 阶段 28 已实现
affectedEnds: 后端、商户小程序、管理 Web
```

## 目标

实现每日 T+1 Mock 结算、失败重试、已结算退款的后续调整和管理数据同步 XLSX 导出。

## 进入条件与涉及端

- 进入条件为阶段 28 已实现，不可变账本、佣金快照、冲回和商户财务摘要已经通过金额守恒验收。
- 涉及后端、商户小程序和管理 Web；消费者端不展示结算批次或导出入口。

## 状态机、数据与权限

- 结算批次使用 `PROCESSING`（处理中）、`SUCCEEDED`（结算成功）、`FAILED`（结算失败）；重试原批次，不复制已成功事实。
- 新增 `settlement_batch` 与 `settlement_item`，Fesod 同步导出不新增任务表；结构、批次唯一约束和负向调整以数据库契约为准。
- `PLATFORM_ADMIN`（平台超级管理员）与 `FINANCE`（财务管理员）可查看、重试和按资源权限导出；商户只读所属门店结算。

## 后端

- 新增 `settlement_batch` 与 `settlement_item`；批次以门店和结算日唯一，明细以批次和账本分录唯一。
- SnailJob 每日 Asia/Shanghai 02:00 汇总前一自然日可结算分录；重复执行返回同一批次。
- Mock 批次从 `PROCESSING`（处理中）进入 `SUCCEEDED`（结算成功）或 `FAILED`（结算失败）；财务重试原批次，不创建重复批次。
- 已结算后的退款和撤销不修改历史批次，在下一可结算日追加负向调整。
- Fesod 同步导出复用列表筛选与权限，字符串 ID 按文本写入，金额同时提供分值和格式化列。

## 接口

- 管理端 `GET /v1/admin/settlements`、`GET /v1/admin/settlements/{id}`、`POST .../{id}/retry`。
- 商户端 `GET /v1/merchant/settlements`、`GET /v1/merchant/settlements/{id}`。
- 管理端商户申请、券、订单、退款、核销、账本、结算和审计集合均提供 `POST .../export`。
- 导出成功返回 XLSX，失败返回 JSON `ErrorResult`；本轮不创建异步导出任务表。

## 客户端

- 管理 Web 展示批次、佣金、净额、调整、失败原因和重试，并正确处理二进制/JSON 两类下载响应。
- 商户端只读展示批次列表和明细，不提供提现或重试。

## 失败处理

- 任务重放、并发批次和重复重试必须命中同一批次；失败保留原因和可重试状态，不部分标记成功。
- XLSX 生成失败返回 JSON 错误结构，不留下空文件；公式注入输入转义，超出同步限制时明确拒绝。
- 越权导出、跨门店结算和已成功批次重试分别返回 403、404 或 409。

## 验收

- 批次并发、重复任务、失败重试、跨日边界、负向调整和金额守恒测试通过。
- 导出权限、筛选一致性、大 ID、中文状态、公式注入防护和错误响应测试通过。
- SnailJob、管理 Web、商户端与真实数据库/OpenAPI 通过。
