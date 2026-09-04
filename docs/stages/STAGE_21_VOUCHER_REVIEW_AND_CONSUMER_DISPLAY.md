# 阶段 21：券审核、销售状态与消费者展示

```yaml
designVersion: 1
designStatus: 已冻结
implementationStatus: 未实现
dependsOn: 阶段 20 已实现
affectedEnds: 后端、消费者小程序、商户小程序、管理 Web
```

## 目标

完成平台券审核、定时销售状态和消费者四类券展示，使商户提交的券经过审核后才可公开。

## 进入条件与涉及端

- 进入条件为阶段 20 已实现，四类券草稿、结构化校验、媒体和提交状态已经稳定。
- 涉及后端、消费者小程序、商户小程序和管理 Web，是本路线图首个三客户端共同验收阶段。

## 状态机、数据与权限

- 审核状态从 `PENDING`（审核中）进入 `APPROVED`（审核通过）或 `REJECTED`（审核未通过）；销售状态由销售期、库存、门店和人工下架共同决定。
- 本阶段更新 `voucher_product` 的审核与销售事实并追加审计，不新增影子商品或客户端专用状态字段。
- `PLATFORM_ADMIN`（平台超级管理员）与 `MERCHANT_REVIEWER`（商户审核员）执行审核；商户仅管理所属商品，消费者仅访问公开可售投影。

## 后端

- 审核从 `PENDING`（审核中）转为 `APPROVED`（审核通过）或 `REJECTED`（审核未通过）；驳回原因必填。
- 审核通过后根据销售时间计算 `SCHEDULED`（待开售）或 `ON_SALE`（销售中），并可进入 `OFF_SALE`（已下架）、`SOLD_OUT`（已售罄）、`ENDED`（已结束）。
- 消费者列表仅返回门店营业、审核通过、销售有效且库存可售的商品；详情返回 `VoucherProductDetailVO(product,shop)`。
- 已上架关键规则变更必须先下架并重新审核；已售订单后续使用支付快照。

## 接口

- `GET /v1/admin/voucher-reviews`、`GET /v1/admin/voucher-reviews/{id}`。
- `POST .../{id}/approval`、`POST .../{id}/rejection`，要求 `Idempotency-Key`。
- 商户 `POST /v1/merchant/voucher-products/{id}/off-sale`。
- 消费者沿用 `/v1/shops/{shopId}/voucher-products` 与 `/v1/voucher-products/{productId}`，响应直接切换为四类统一结构。

## 客户端

- 管理 Web 结构化展示券规则、商户资料和消费者视角预览，不展示原始 JSON。
- 商户端展示审核结果、驳回原因和销售状态，并提供下架确认。
- 消费者端按券型展示关键权益、价格、有效期、库存、限购和退款规则，不生成不存在的促销信息。

## 失败处理

- 并发审核、重复决定、已下架或门店停用等状态冲突返回稳定 409，三端都以重新查询结果为准。
- 无审核权限、跨门店写操作和未公开商品访问分别保留 403 或 404 语义，不能由客户端放宽。
- 定时切换失败不生成相反销售状态；查询端实时校验销售期并等待任务重试。

## 验收

- 并发审核、驳回重提、定时边界、门店停用、库存为零和下架后可见性测试通过。
- 三客户端覆盖四类规则、状态中文标签、空态和 409 刷新。
- 运行时 OpenAPI 联合结构、枚举、错误响应和安全声明通过。
