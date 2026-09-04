# Roamly 数据库结构契约

```yaml
updatedAt: 2026-09-04
schemaMode: Demo 可重建快照
businessTableCount: 19
database: MySQL / InnoDB / utf8mb4
runtimeVerification: 已通过
```

结构真源为 [schema-init.sql](./ray-server/src/main/resources/schema-init.sql)，开发样例真源为 [seed-dev.sql](./ray-server/src/main/resources/seed-dev.sql)。两者只服务于已授权可清空的 Demo 开发库。

## 规则

- 不使用 Flyway、Liquibase、版本化迁移或历史表；结构变化直接更新完整快照。
- 不声明物理外键，跨表关系由 Service 在事务内校验和维护。
- 金额以分存储；Java 内部 ID 为 `Long`，HTTP 业务 ID 为字符串。
- 已退役 `blog`、`blog_comments`、`voucher`、`seckill_voucher`，不保留兼容表或转换脚本。
- `schema-init.sql` 包含 `DROP TABLE`，不得用于非 Demo 数据库或生产环境。

## 表目录

| 表 | 用途 | 隔离范围 | 关键约束 |
|---|---|---|---|
| `city` | 城市字典 | 平台级 | `code` 唯一 |
| `content_section` | 官方内容分区 | 平台级 | `code` 唯一 |
| `section_follow` | 用户关注分区 | 用户级 | `user_id,section_id` 唯一 |
| `media_asset` | 临时/已绑定媒体 | 用户级 | 存储路径唯一；归属与状态索引 |
| `post` | 统一动态 | 城市/用户级 | 分区、作者、商户、城市流索引 |
| `post_media` | 动态媒体顺序 | 动态级 | 动态顺序与媒体各自唯一 |
| `post_like` | 动态点赞事实 | 用户/动态级 | `post_id,user_id` 唯一 |
| `post_comment` | 根评论与回复 | 动态级 | 根讨论、父评论、状态索引 |
| `post_comment_like` | 评论点赞事实 | 用户/评论级 | `comment_id,user_id` 唯一 |
| `follow` | 用户关注关系 | 用户级 | `user_id,follow_user_id` 唯一 |
| `shop` | 商户、坐标与聚合 | 平台级 | 城市、分类、状态组合索引 |
| `shop_type` | 商户分类 | 平台级 | 主键 |
| `shop_review` | 独立点评 | 商户/用户级 | `shop_id,user_id` 唯一 |
| `shop_review_media` | 点评媒体顺序 | 点评级 | 点评顺序与媒体各自唯一 |
| `user` | 用户账号 | 平台级 | 手机号唯一 |
| `user_info` | 用户资料 | 用户级 | `user_id` 主键 |
| `voucher_product` | 团购商品 | 商户级 | 商户状态与销售期索引 |
| `voucher_order` | 团购订单 | 用户级 | 用户状态时间、商品用户索引 |
| `user_voucher` | 用户券实例 | 用户级 | 券码唯一、订单唯一 |

## 业务约束

- `media_asset.status` 使用 0 临时、1 已绑定、2 已删除；绑定类型 1 动态、2 点评。
- 普通动态自动进入 `ROAM_DAILY`；探店动态必须关联允许探店的启用分区和同城启用商户。
- `post_like`、`post_comment_like`、`section_follow`、`follow` 是关系事实，冗余计数必须与关系行数一致。
- 评论删除清空正文；有有效回复的根评论保留删除占位。
- 用户对同一商户最多一条点评；`shop.comments` 和 `shop.score` 由正常点评重算。
- 商品库存满足总库存、有效占用与可售库存之间的一致性；`sold_count` 只在支付确认成功后累计。
- 订单状态码映射为 1 待支付、2 已支付、4 已取消、5 退款中、6 已退款；对外名称使用 `CANCELED`。
- `user_voucher.order_id` 唯一保证支付确认幂等；状态为 `UNUSED | USED | EXPIRED | REFUNDED`。

## 开发种子

种子包含：1 个城市、5 个官方分区、3 个商户分类、3 个用户及资料、3 个商户、分区关注和用户关注、已绑定动态/点评媒体、3 条动态及其点赞、根评论/回复及点赞、3 条点评、2 个团购商品、待支付/已支付/已取消订单，以及与已支付订单一一对应的未使用券。

种子聚合可由 SQL 事实复核：

- `post.liked_count = count(post_like)`；评论点赞同理。
- `shop.comments = 正常点评数`；`shop.score = round(avg(review.score) * 10)`。
- 商品 3001 的 200 份库存中，一份由待支付订单占用、一份已支付，`available_stock=198`、`sold_count=1`。
- 已支付订单 6002 只对应用户券 7001；已取消订单不占库存、不发券。

## 集成验收

`DatabaseBusinessClosureIntegrationTest` 仅在 `RUN_DATABASE_INTEGRATION_TESTS=true` 时运行。它在开始前重建当前快照，使用 Redis DB 15，完成真实 HTTP/Service/SQL 场景后再次重建种子并清空测试 Redis，确保日常开发环境回到纯种子状态。

2026-09-04 已在 `.env` 当前指向且获授权的开发库执行最终验收：

- 完整执行 `schema-init.sql` 与 `seed-dev.sql`，确认恰好 19 张业务表，关键唯一索引存在，旧表和 `tb_*` 表均不存在。
- `DatabaseBusinessClosureIntegrationTest` 3 项全部通过，覆盖媒体绑定、动态、信息流、评论、点评聚合、订单扣减/返库、支付确认幂等发券、券过期刷新及用户隔离。
- 测试结束后再次重建快照并恢复纯种子数据，Redis DB 15 已清空，不保留测试期间生成的业务数据或登录状态。
