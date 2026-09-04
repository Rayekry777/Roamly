# 阶段 22：确认订单、计价与库存锁

```yaml
designVersion: 2
designStatus: 已冻结
implementationStatus: 已实现
dependsOn: 阶段 21 已实现
affectedEnds: 后端、消费者小程序
```

## 目标

交付确认订单、服务端计价、多数量购买、限购汇总和库存并发控制；本阶段只创建待支付订单，不处理支付。

## 进入条件与涉及端

- 进入条件为阶段 21 已实现，消费者只能看到审核通过且实时可售的四类券，价格和规则结构已经冻结。
- 涉及后端与消费者小程序；商户和管理 Web 不新增交易写入口。

## 状态机、数据与权限

- 下单只创建 `PENDING_PAYMENT`（待支付）订单；支付、关单和退款状态迁移不在本阶段发生。
- 重构 `voucher_order` 的数量、计价、幂等与过期时间事实，并条件扣减 `voucher_product` 库存；字段以数据库契约为准。
- 仅 `CONSUMER`（消费者端）当前用户可确认和创建自己的订单；服务端按用户、商品和门店事实校验限购与可售性。

## 后端

- 确认接口重新读取可售商品并返回单价、数量上下限、门市总价、优惠、实付和服务端时间；客户端价格不参与计算。
- 下单数量范围为 `1..min(purchaseLimit,99,availableStock)`，限购汇总当前用户同商品全部未取消订单。
- Lock4j 2.2.7 以用户加商品构造锁键，等待失败返回 409 `ORDER_REQUEST_BUSY`（订单请求繁忙），Redis 不可用返回 503 `ORDER_COORDINATION_UNAVAILABLE`（订单协调服务不可用）。
- 创建订单固化商品、门店、规则、退款和佣金快照，条件扣减库存；数据库条件更新与事务是最终事实。

## 接口

- `POST /v1/voucher-products/{productId}/order-confirmations` 返回确认快照，不占库存。
- `POST /v1/voucher-products/{productId}/orders` 请求包含数量并要求 `Idempotency-Key`。
- 返回订单 ID、订单号、金额、创建时间和 `paymentExpireTime`，状态为 `PENDING_PAYMENT`（待支付）；订单使用用户级 `idempotencyKey`（幂等键）和请求指纹唯一约束。

## 消费者小程序

- 商品详情进入独立确认订单页，展示真实商户、商品、数量步进器、金额明细和固定底部提交栏。
- 库存或价格变化时展示服务端新事实并要求用户再次确认；重复提交期间禁用按钮。
- 提交成功进入订单详情，失败保留选择数量和重试入口。

## 失败处理

- 价格、库存或限购变化返回最新服务端事实并要求重新确认；锁等待失败返回 409，协调服务不可用返回 503。
- 重复幂等键返回同一订单，键被不同请求复用时拒绝；数据库事务失败不留下库存扣减或半成品订单。
- 未登录返回 401，商品不可公开或不存在返回 404，禁止根据客户端传入金额下单。

## 实现记录

- `voucher_order.status` 已重构为字符串状态，新增 `payment_expire_time`、`idempotency_key`、`request_fingerprint` 及用户级唯一索引；开发种子同步使用字符串状态。
- `POST /v1/voucher-products/{productId}/order-confirmations` 返回服务端单价、数量上下限、总价、优惠、实付、库存、服务端时间和支付过期参考时间。
- `POST /v1/voucher-products/{productId}/orders` 要求 8 至 128 位 `Idempotency-Key`；重复请求返回同一订单，复用键但请求指纹不同返回 `ORDER_IDEMPOTENCY_CONFLICT`（订单幂等键冲突）。
- 消费者端增加数量步进、确认页和提交中锁定；客户端金额仅用于展示，创建订单始终重新读取服务端商品事实。

## 验收

- 并发下单、超限、库存不足、价格变化、锁冲突、Redis 不可用和幂等重放测试通过。
- 库存只扣减一次，创建失败完整回滚，字符串大 ID 不丢精度。
- 消费者确认页、异常刷新和真实 HTTP/OpenAPI 通过。
