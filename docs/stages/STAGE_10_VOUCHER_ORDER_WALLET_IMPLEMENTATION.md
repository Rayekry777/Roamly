# 阶段 10：团购商品、订单与券包

状态：已实现（Demo 范围）
更新时间：2026-09-04

已实现商户团购列表、`VoucherProductDetailVO(product, shop)` 商品详情、待支付下单、限购与库存条件扣减、取消返库、订单列表、`VoucherOrderDetailVO(order, product, shop)` 详情、支付确认幂等发券、券包列表/详情和过期刷新。

订单状态统一为 `PENDING_PAYMENT`（待支付）、`PAID`（已支付）、`CANCELED`（已取消）、`REFUNDING`（退款中）、`REFUNDED`（已退款）。真实微信支付回调仍是生产扩展；Demo 范围内的 Mock 支付、退款和核销已由后续阶段闭环，内部支付确认服务继续作为支付供应商适配边界。
