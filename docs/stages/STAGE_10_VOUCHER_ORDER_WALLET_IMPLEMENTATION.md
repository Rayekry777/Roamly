# 阶段 10：团购商品、订单与券包实现记录

## 状态

开发中。已完成代码、OpenAPI 模型注册和开发 SQL 快照；真实支付、核销、退款和生产数据迁移尚未开放。

## 开工前冻结的契约

### HTTP 接口与 operationId

| 方法 | 路径 | operationId | 鉴权 | 成功响应 |
|---|---|---|---|---|
| GET | `/v1/shops/{shopId}/voucher-products` | `listShopVoucherProducts` | 公开 | 200 `Result<List<VoucherProductVO>>` |
| GET | `/v1/voucher-products/{productId}` | `getVoucherProduct` | 公开 | 200 `Result<VoucherProductDetailVO>` |
| POST | `/v1/voucher-products/{productId}/orders` | `createVoucherProductOrder` | 登录 | 201 `Result<VoucherOrderVO>` |
| GET | `/v1/users/me/orders` | `listMyVoucherOrders` | 登录 | 200 `Result<PageResult<VoucherOrderVO>>` |
| GET | `/v1/users/me/orders/{orderId}` | `getMyVoucherOrder` | 登录 | 200 `Result<VoucherOrderDetailVO>` |
| DELETE | `/v1/users/me/orders/{orderId}` | `cancelMyVoucherOrder` | 登录 | 204 |
| GET | `/v1/users/me/vouchers` | `listMyVouchers` | 登录 | 200 `Result<PageResult<UserVoucherVO>>` |
| GET | `/v1/users/me/vouchers/{userVoucherId}` | `getMyVoucher` | 登录 | 200 `Result<UserVoucherVO>` |

所有请求使用 `VoucherOrderCreateDTO`，数量固定为 1；Java 类不使用 `Request` 后缀。所有 ID 在 JSON 中为字符串，金额以分为单位的整数返回。

### 状态、错误和事务

- 商品状态：`DRAFT / ON_SALE / SOLD_OUT / OFF_SALE`；销售类型：`NORMAL / SECKILL`。
- 订单状态：数据库兼容旧数字编码，接口输出 `PENDING_PAYMENT / PAID / CANCELED / REFUNDING / REFUNDED`。
- 券状态：`UNUSED / USED / EXPIRED / REFUNDED`。
- 下单在事务内校验商品、按条件扣减库存并写入 `PENDING_PAYMENT`；不提供伪支付接口，不自动变更为已支付或发券。
- 取消仅允许待支付订单，条件更新成功后同事务返还库存。
- 支付成功后续由内部事件实现：订单更新和 `user_voucher` 按订单唯一约束幂等写入；券有效期由商品固定区间或支付时间加有效天数计算。
- 400：参数或状态枚举错误；401：未登录；404：商品、订单或券不存在；409：库存不足、超限购或订单状态冲突。

## 数据库快照

本项目不使用版本迁移；结构直接维护在 `ray-server/src/main/resources/schema-init.sql`，开发样例维护在 `seed-dev.sql`。阶段 10 新增 `voucher_product`、`user_voucher`，并在现有 `voucher_order` 保留旧字段的基础上增加商品快照、金额、数量和索引，以兼容已实现的旧秒杀服务。禁止在保留数据的环境执行包含 `DROP TABLE` 的脚本。

## 已实现范围

- 商品公开列表和详情：完成 Mapper、Service、Controller、Bearer 路由公开声明和 Schema 注册。
- 普通/秒杀统一创建待支付订单，数据库条件扣库存，订单列表、详情、取消和券包查询完成。
- DTO、VO 均补齐 `@Schema`；Controller 使用正常 Swagger import 和稳定 operationId。
- 小程序对应契约由并行任务维护，本阶段不覆盖其文件。

## 待完成验收

- 在隔离数据库执行 SQL 并核对表结构、索引和样例商品。
- 接入内部支付成功事件、发券幂等和过期状态刷新。
- 补充 Controller/Service 单元测试、OpenAPI 实际端点测试和小程序真机联调。
- 完成后将状态从“开发中”改为“已实现”。
