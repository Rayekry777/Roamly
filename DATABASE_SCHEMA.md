# 数据库结构摘要

当前结构来源：`src/main/resources/schema-init.sql`；开发数据来源：`src/main/resources/seed-dev.sql`。两者由 `application-dev.yml` 按先结构、后数据的顺序初始化。原始合并脚本 `src/main/resources/db/dp.sql` 仅作为历史备份保留。

| 表 | 用途 | 主键 | 关键约束/索引 |
|---|---|---|---|
| `tb_user` | 用户账号 | `id` | `phone` 唯一索引 |
| `tb_user_info` | 用户扩展资料 | `user_id` | 与用户逻辑一对一 |
| `tb_shop_type` | 商户分类 | `id` | 无实际外键 |
| `tb_shop` | 商户信息与地理坐标 | `id` | `type_id` 普通索引 |
| `tb_blog` | 探店笔记 | `id` | `shop_id`、`user_id` 为逻辑关联 |
| `tb_blog_comments` | 笔记评论/回复 | `id` | `blog_id`、`user_id` 为逻辑关联 |
| `tb_follow` | 用户关注关系 | `id` | `user_id`、`follow_user_id` 为逻辑关联 |
| `tb_voucher` | 商户优惠券 | `id` | `shop_id` 为逻辑关联 |
| `tb_seckill_voucher` | 秒杀券库存与时间 | `voucher_id` | 与优惠券逻辑一对一 |
| `tb_voucher_order` | 优惠券订单 | `id` | `user_id`、`voucher_id` 为逻辑关联 |

说明：脚本中的 `DROP TABLE` 仅用于本地初始化，生产环境不得重复执行；实际外键约束目前未声明，关联完整性由应用维护。
`tb_seckill_voucher.begin_time` 与 `end_time` 使用 `timestamp NULL DEFAULT NULL`，避免 MySQL 严格模式下的零日期默认值错误；业务层应在时间为空时按未配置生效/失效时间处理。
