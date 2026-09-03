# 阶段 9：商户点评后端实现记录

## 开工冻结（2026-09-03）

- Request 模型统一为 `ShopReviewCreateDTO`、`ShopReviewUpdateDTO`；不再使用 `Request` 命名。
- `ShopReviewVO` 返回字符串 ID、作者摘要、1～5 分、正文、点评媒体、消费认证标识、编辑权限、状态和审计时间；媒体使用 `ReviewMediaVO.url`。
- operationId：`listShopReviews`、`createShopReview`、`updateMyShopReview`、`deleteMyShopReview`。
- HTTP 契约：列表 `GET /v1/shops/{shopId}/reviews`；创建 `POST` 返回 201；编辑 `PUT /me` 返回 200；删除 `DELETE /me` 返回 204。
- 列表仅返回 `NORMAL` 点评，分页 `page>=1`、`1<=size<=100`，排序为 `LATEST` 或 `HIGHEST_SCORE`。
- 创建和编辑正文最多 2000 字、媒体最多 9 张且不可重复；媒体必须属于当前用户、处于临时状态且未被占用。
- 同一用户和商户由 `uk_review_shop_user` 唯一约束保证一条点评；重复创建返回 `REVIEW_ALREADY_EXISTS(409)`。
- 消费认证暂时通过旧 `voucher_order.status=3` 与 `voucher.shop_id` 过渡查询；客户端不能提交认证字段，阶段 10 迁移到 `user_voucher`。
- 创建、编辑、删除在事务内更新点评、媒体关系和商户评分/数量聚合；媒体物理文件在提交后清理。

## 实现范围

- 新增 `ShopReview`、`ShopReviewMedia`、DTO、VO、Mapper、Service、Controller。
- 媒体服务新增点评绑定、替换和删除能力。
- `schema-init.sql` 新增 `shop_review`、`shop_review_media`；`seed-dev.sql` 保持无虚构点评样例。
- OpenAPI 注册点评 Schema，并为点评路径补充 404/409 错误响应与 Bearer 声明。
- Sa-Token 路由允许匿名读取点评列表，创建、编辑、删除必须登录。

## 验收记录

状态：开发中。

已完成：新增点评 Controller/Service/Mapper 测试；Reactor `mvn test` 通过 62 项（10 项外部环境测试跳过），`mvn -DskipTests compile` 通过，`git diff --check` 通过。

待完成：真实数据库重建后的接口联调、OpenAPI 端点检查及小程序真机验收。数据库脚本包含 `DROP TABLE`，禁止在需保留数据的环境执行。
