# 阶段 37：契约、模块和数据库基础

```yaml
stage: 37
status: 已实现
updatedAt: 2026-09-12
databaseMode: 直接维护 schema-init.sql，不引入 Flyway
currentBusinessTableCount: 43
stageBusinessTableCount: 46
```

## 目标

冻结订单、售后、退款、客服和结算状态，建立退款逐券明细、退款执行尝试和结算执行尝试的数据库基础。WebSocket/SSE 只做刷新通知，不能改变业务状态。

## 状态契约

- 订单：`PENDING_PAYMENT`、`PAID`、`CANCELED`、`COMPLETED`。
- 售后：`NONE`、`APPLYING`、`UNDER_REVIEW`、`APPROVED`、`REJECTED`、`REFUNDING`、`PARTIALLY_REFUNDED`、`REFUNDED`、`REFUND_FAILED`、`CLOSED`。
- 退款审核：`PENDING_REVIEW`、`AUTO_APPROVED`、`MANUAL_APPROVED`、`REJECTED`。
- 退款执行：`WAITING_EXECUTION`、`PROCESSING`、`SUCCESS`、`PARTIAL_SUCCESS`、`FAILED`、`RETRY_WAITING`、`MANUAL_REQUIRED`。
- 客服：`OPEN`、`CLAIMED`、`WAITING_CUSTOMER`、`WAITING_MERCHANT`、`WAITING_INTERNAL`、`RESOLVED`、`CLOSED`。

## 数据库变更

- `voucher_refund_item`：一条退款明细对应一张券，保存退款金额、服务费、平台补贴和核销收入冲回快照。
- `voucher_refund_attempt`：记录 Mock 退款每次执行、租约、重试、渠道流水和失败原因。
- `settlement_attempt`：记录 Mock 结算每次执行、租约、重试和失败原因。
- 旧 `voucher_refund.voucher_ids` 暂时保留用于开发快照兼容，新业务不得继续写入；阶段 39 完成数据切换后再清理。

## 事务和事件边界

- 审核事务只决定是否允许执行，不直接模拟渠道退款。
- 执行任务使用数据库原子领取和租约，渠道调用在独立事务边界执行。
- 业务事件仅用于审计、通知和统计，不能取代主表状态。
- 阶段 37 不自动改动已有数据库，已有开发库必须在授权后执行审查过的 SQL。

## 验收记录

- [x] 状态契约同步到 `BACKEND_DEVELOPMENT.md`。
- [x] `DATABASE_SCHEMA.md` 与 `schema-init.sql` 表数量和字段一致。
- [x] 静态结构校验确认新建数据库可创建 46 张业务表。
- [x] `mvn -pl ray-server -am -DskipTests compile` 通过。
- [x] 阶段验收后使用 Conventional Commit 提交。

## 阶段交付记录

- 实际完成：增加退款逐券明细、退款执行尝试、结算执行尝试表及队列索引；冻结订单、售后、退款审核、退款执行和客服状态；同步后端契约、数据库契约和路线图。
- 未完成：退款执行服务、结算执行服务、客服权限和三端页面属于后续阶段，不在本阶段提前标记完成。
- 风险：当前未对授权开发数据库执行清库或重建；已有数据库需要在阶段 39/42 前按审查后的 SQL 完成数据切换。
- 下一阶段前置条件：阶段 38 使用新增结算尝试表实现真实 Mock 结算重试。
