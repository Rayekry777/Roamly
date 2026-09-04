# 阶段 23：订单关单、Mock 支付与多份发券

```yaml
designVersion: 1
designStatus: 已冻结
implementationStatus: 未实现
dependsOn: 阶段 22 已实现
affectedEnds: 后端、消费者小程序
```

## 目标

完成 15 分钟自动关单、消费者主动 Mock 支付、支付幂等和按数量逐份发券。

## 进入条件与涉及端

- 进入条件为阶段 22 已实现，待支付订单、服务端计价、库存扣减和幂等下单已通过并发验收。
- 涉及后端与消费者小程序；商户和管理 Web 在本阶段不处理支付命令。

## 状态机、数据与权限

- 订单从 `PENDING_PAYMENT`（待支付）进入 `PAID`（已支付）或 `CANCELED`（已取消）；支付从 `PENDING`（待支付）进入 `SUCCEEDED`（支付成功）、`FAILED`（支付失败）或 `CLOSED`（已关闭）。
- 新增 `payment_transaction`，按订单数量生成多行 `user_voucher`，并更新订单、库存和销量；字段、组合唯一约束与快照以数据库契约为准。
- 只有订单所属消费者可支付和查看逐份券；SnailJob 使用受控内部身份执行关单，不能绕过业务条件更新。

## 后端

- `paymentExpireTime` 与创建时间由同一 `Clock` 生成并固定相差 15 分钟；查询和支付执行惰性关单。
- SnailJob 分页扫描过期 `PENDING_PAYMENT`（待支付）订单；条件更新取得唯一关单权并只返还一次库存。
- 支付网关模式为 `MOCK`（模拟）、`DISABLED`（禁用），预留 `WECHAT`（微信支付）；prod 未配置真实支付返回 503。
- 支付成功原子写入 `payment_transaction`、更新订单、增加销量并按数量生成 `user_voucher`；`order_id,sequence_no` 唯一。
- 单券实付按整数分摊，无法整除的分差按顺序分配，合计严格等于订单实付。

## 接口

- `POST /v1/users/me/orders/{orderId}/payments` 要求 `Idempotency-Key`。
- 订单详情返回 `serverTime`、`paymentExpireTime`、支付状态、履约摘要和逐份券摘要。
- 过期支付返回 `ORDER_PAYMENT_EXPIRED`（订单支付已超时），重复成功请求返回相同事实。

## 消费者小程序

- 订单详情按服务端时间显示倒计时；归零只触发刷新，不在本地修改状态。
- dev/test 显示“模拟支付”；处理中禁止重复点击，成功刷新订单并进入券包。
- 订单列表和详情展示 `PAID`（已支付）或 `CANCELED`（已取消）等中文状态以及多份券摘要。

## 失败处理

- 倒计时归零、任务关单和支付竞争时，只允许一个数据库状态迁移成功；失败方回查并返回权威结果。
- Mock 支付失败保留待支付或明确失败事实，不发券、不增销量；支付服务禁用返回 503。
- 任务可重复投递，任何重放都不得二次返库、二次记支付、二次增销量或重复发券。

## 验收

- 可注入时钟覆盖 14:59、15:00、重复扫描、支付/关单竞态和只返库一次。
- Mock 成功、失败、延迟、重放、销量和逐份发券数据库测试通过。
- SnailJob 真实执行器、惰性关单、消费者倒计时和运行时 OpenAPI 通过。
