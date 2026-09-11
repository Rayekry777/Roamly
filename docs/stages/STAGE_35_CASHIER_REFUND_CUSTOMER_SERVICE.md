# 阶段 35：今日收银、核销收入与退款执行

```yaml
status: Mock 闭环已完成，真实渠道待后续
updatedAt: 2026-09-08
scope: 商家今日团购收银、核销详情收入拆解、服务费快照、异步退款状态
```

## 已落地

- 顾客实付 = 商品售价 - 商家补贴 - 平台优惠；金额统一使用分，最后一张券承接除不尽的余数。
- 核销时冻结商品售价、补贴、平台优惠、顾客实付、服务费基数/费率/金额和预计收入；线下额外消费不进入平台账本。
- 商家接口：`/v1/merchant/finance/today`、`/v1/merchant/finance/service-fee-policy`、核销详情、收入明细和备注接口。
- 资金账本使用 `SERVICE_FEE_RECOGNIZED/REVERSED/REFUNDED` 与 `REFUND_REVENUE_REVERSED`，T+1 结算按账本事件生成。
- 退款审批与渠道执行分离：审批只写 `decision_status=APPROVED`、`execution_status=NOT_STARTED`，后台扫描先进入 `PROCESSING`，再落 `SUCCEEDED/FAILED`；成功后冲回冻结款及已核销收入。
- 商家小程序新增“本店今日收银总计”“今日数据”“核销明细五个筛选页签”和核销详情收入拆解。
- Mock 渠道支持 `SUCCESS`、`FAIL_ONCE`、`ALWAYS_FAIL`、`DELAYED`，通过 `ray.refund.mock.outcome` 配置；失败可通过原有管理端重试接口重新进入执行队列。
- 过期未使用且商品允许过期退款的券按 `AUTO-EXPIRED:{voucherId}` 幂等键自动生成退款单，默认 Asia/Shanghai 每日扫描。
- 客服后端已闭环：消费者/商户创建与查询工单，管理端队列、认领、公开回复、内部备注、状态变更；公开消息与内部备注在服务层隔离，关闭后 7 天内消费者回复可重新打开。
- 管理 Web 已增加客服工单页面和菜单入口，支持认领、公开回复、内部备注。

## 后续

- 接入真实支付渠道回调（当前明确不接入），并补充客服附件上传、消费者端客服页面和更细的自动化状态机测试。
