# 阶段 39：退款核心重构

```yaml
stage: 39
status: 已实现
updatedAt: 2026-09-12
gateway: MOCK
databaseMode: 直接维护 schema-init.sql
```

## 目标

把退款申请、审核、执行和账本拆开，以逐券明细支持部分退款，并使用 MySQL 条件更新与租约保证多实例只能有一个执行者。

## 数据与状态

- `voucher_refund` 保存申请和聚合状态，不再保存 CSV 券集合。
- `voucher_refund_item` 一行对应一张券，保存顾客实付、平台补贴、服务费、退款和核销冲回快照。
- `voucher_refund_attempt` 保存渠道幂等键、租约、执行次数、失败原因和完成时间。
- 订单交易状态保持 `PAID`；售后状态使用 `voucher_order.after_sale_status`。
- 未核销退款可自动审核；商户或管理员发起的已核销退款进入人工审核。

## 执行边界

- 申请服务校验订单、券、归属、商品退款规则和重复申请。
- 审核通过后只创建执行尝试，不在审核事务内调用渠道。
- Coordinator 扫描可运行任务；LeaseService 使用条件更新在短事务中竞争并提交 30 秒租约。
- Worker 在数据库事务外调用 Mock Gateway；ResultService 在新的短事务中锁定执行尝试、校验租约所有者并提交逐券、订单、支付和账本结果。
- Mock Gateway 支持成功、首次失败、持续失败和延迟，任务保存场景快照，重试使用同一渠道幂等键。
- 完成后按实际已退款券数判断部分退款或全额退款，并追加幂等资金账本。
- WebSocket/SSE 只发布资源更新通知，不参与状态转换。

## 接口与权限

- 消费者只能申请和读取本人订单下的券，已核销券不能由消费者直接退款。
- 商户只能读取和申请当前门店订单退款，已核销退款进入人工审核，不能审批或执行。
- 管理端使用 `admin:refund:manage` 审批、拒绝和创建人工重试；审核主记录通过 `FOR UPDATE` 串行化，重复审批不会创建第二个活动任务。
- 消费者、商户和管理端分别新增受各自登录域保护的退款时间线接口；详情返回逐券金额、履约、执行和冲回快照。

## 恢复规则

- 一个任务只能由条件更新成功的租约所有者调用渠道。
- 结果事务用 `FOR UPDATE` 锁定执行尝试并再次校验 `lease_owner`，过期 Worker 不能覆盖新 Worker。
- 渠道成功而本地事务失败时，领取事实保持 `PROCESSING`；租约超时后以同一渠道幂等键重放并重新提交本地结果。
- `FAIL_ONCE` 和 `DELAYED` 在第二次执行成功；`ALWAYS_FAIL` 第三次失败进入 `MANUAL_REQUIRED`。
- 审核拒绝会按逐券快照把已核销券恢复为 `USED`、未核销券恢复为 `UNUSED`，并按订单其余已退款券重新计算售后聚合状态。

## 数据库校正

- `voucher_order` 新增独立 `after_sale_status` 与用户售后索引；交易状态不再写 `REFUNDING/REFUNDED`。
- `voucher_refund` 删除 `voucher_ids`，增加处理人、审核人、审核说明、版本和工作台索引；审核幂等键改为全局唯一。
- `voucher_refund_item` 是退款券集合和金额快照真源；`voucher_id` 仅保留第一张券兼容投影。
- `seed-dev.sql` 已同步五类退款主状态、逐券明细、成功/延迟/人工处理执行尝试和独立订单售后状态。
- 未对未知开发数据库执行清库、重建或自动改表；已有库手工 SQL 顺序记录在 `DATABASE_SCHEMA.md`。

## 验收记录

- [x] CSV 券集合从 DDL、实体和业务查询移除。
- [x] 任务租约领取失败时不调用 Gateway。
- [x] `FAIL_ONCE` 和 `DELAYED` 第二次执行成功。
- [x] `ALWAYS_FAIL` 达到三次后进入人工处理。
- [x] 渠道调用与本地结果事务分离，本地提交失败可由租约超时恢复。
- [x] 已核销退款进入人工审核并保留核销财务快照，拒绝后恢复 `USED`。
- [x] 多券申请逐券建明细，单券退款可形成订单部分退款。
- [x] 自动过期退款同时覆盖 `UNUSED/EXPIRED`。
- [x] 后端完整测试通过。
- [x] 管理 Web 构建、商户小程序完整验证和消费者小程序静态检查通过；已记录两个非退款基线测试问题。

## 阶段交付记录

```text
阶段：39
目标：完成逐券退款申请、独立审核、可恢复 Mock 执行、部分退款、账本和时间线闭环。
实际完成：退款主表/逐券明细/执行尝试投入业务使用；审核和执行分离；短事务租约、事务外渠道调用和独立结果事务完成；三端退款时间线接口完成。
未完成：真实 MySQL/Redis 运行时、Testcontainers 双实例和实际 /v3/api-docs 验证统一留到阶段 42；真实支付和退款渠道不在范围内。
源码变更：新增退款 Gateway、Coordinator、LeaseService、Worker、ResultService、任务快照和逐券/执行实体及 Mapper；订单售后状态与交易状态分离。
数据库变更：保持 46 张业务表；删除 voucher_ids；voucher_order 新增 after_sale_status；voucher_refund 增加审核处理字段、版本和工作台索引；种子状态同步。
接口变更：退款详情增加审核、执行、逐券和处理人信息；消费者、商户、管理端各新增一个时间线 GET 接口；预期 operationId 由 185 增至 188。
权限变更：消费者按 user_id 隔离；商户按 shop_id 隔离且不能审批；管理端审批、拒绝和重试继续要求 admin:refund:manage。
前端变更：本阶段没有修改三端源码；阶段 41 使用新增时间线和逐券字段完成工作台体验。
日志和注释：增加渠道成功、重试、人工处理、本地提交失败恢复日志；补齐新增 Service、Gateway、Mapper 和公开方法 JavaDoc。
文档变更：同步 BACKEND_DEVELOPMENT.md、DATABASE_SCHEMA.md、本阶段文档、阶段 37 清理记录和四端路线图。
测试命令：mvn test；退款专项 Maven 测试；管理 Web pnpm verify/pnpm build；商户 npm run verify；消费者 npm run verify/npm run type-check。
测试结果：后端 236 项，214 通过、22 项按既有环境开关跳过；退款专项 14/14；商户 73/73；消费者 156/157，唯一失败是工作区局域网 IP 与旧固定期望不一致；管理端路由用例单独 4/4，全套中该用例出现 5 秒超时。
构建结果：后端编译通过；管理 Web 生产构建通过；商户和消费者类型、Lint、样式检查通过。
风险：未重建未知数据库；运行时 OpenAPI、真实数据库租约并发和应用重启恢复尚未验证；消费者 IP 改动属于用户工作区，不纳入本阶段提交。
下一阶段前置条件：阶段 40 只能关联退款只读上下文，客服不能绕过 admin:refund:manage 审批退款。
Git 提交：使用本阶段 `feat(refund): 重构退款审核执行与账本` 主提交完整追溯。
```
