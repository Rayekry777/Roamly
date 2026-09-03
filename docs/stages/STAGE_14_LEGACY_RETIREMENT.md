# 阶段 14：Demo 直接重构与旧能力退役

## 状态

已实现（2026-09-03）。本阶段依据 Demo 可直接重建、测试数据可重新生成的决定执行，不提供数据迁移、旧 API 兼容或回退兼容层。

## 退役范围

| 旧能力 | 已移除内容 | 当前替代 |
|---|---|---|
| Blog、Blog 点赞与旧评论 | Entity、DTO、VO、Controller、Service、Mapper、`blog`、`blog_comments` | `ContentPost`、`PostComment`、`/v1/posts/**`、`/v1/comments/**` |
| Blog 图片上传 | `UploadController`、`/v1/blog-images` | `MediaAssetController`、`/v1/media/images`；静态 `/blogs/**` 保留为媒体存储资源路径 |
| 旧优惠券与秒杀 | Entity、DTO、VO、Controller、Service、Mapper、Lua、`voucher`、`seckill_voucher` | `VoucherProduct`、`VoucherOrder`、`UserVoucher` 及其团购接口 |
| 旧消费认证 | 基于旧 `voucher_order.voucher_id` 的跨表查询 | 仅由已核销 `user_voucher` 决定消费标识 |

## 数据库与开发样例

- `voucher_order` 不再包含 `voucher_id`，只保存商品、商户、金额和状态快照。
- `schema-init.sql` 的业务表数量由 23 收敛为 19。
- `seed-dev.sql` 直接创建当前 Post、评论和团购商品样例；不再插入旧表后转换。
- 该策略只适用于可清空 Demo 数据库。对需要保留实际用户数据的环境，必须另行设计迁移方案，不能直接执行本快照。

## HTTP 与验证

- 已移除：`/v1/blogs/**`、`/v1/blog-images`、`/v1/vouchers`、`/v1/seckill-vouchers/**`、`/v1/shops/{shopId}/vouchers`。
- OpenAPI、Sa-Token 公开路由和运行时契约测试只保留当前模型的接口。
- 本阶段完成后应执行 `mvn test`、`mvn -DskipTests compile` 与 `git diff --check`；真实数据库初始化和小程序真机联调仍需在隔离开发环境执行。
