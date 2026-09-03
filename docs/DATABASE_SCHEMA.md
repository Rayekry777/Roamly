# 数据库结构文档

结构真源为 [schema-init.sql](../ray-server/src/main/resources/schema-init.sql)，开发样例真源为 [seed-dev.sql](../ray-server/src/main/resources/seed-dev.sql)。当前为可清空的 Demo 快照：执行结构脚本会删除开发库中的现有表，随后以种子脚本重新生成样例数据。

```yaml
updatedAt: 2026-09-03
schemaMode: 直接维护初始化快照
businessTableCount: 19
database: MySQL / InnoDB / utf8mb4
```

## 范围与约束

- 不使用 Flyway、Liquibase 或版本化迁移 SQL；结构和种子数据只维护在上述两个 SQL 文件。
- 当前快照已直接退役 `blog`、`blog_comments`、`voucher`、`seckill_voucher`；不保存旧数据兼容字段或转换脚本。
- 不声明数据库物理外键。跨表关系为应用层维护的逻辑关联，Service 在写入时负责校验与事务一致性。
- 所有金额以分存储；业务 ID 在 Java 内部使用 `Long`，HTTP JSON 对外序列化为字符串。

## 表目录

| 表 | 用途 | 隔离范围 | 关键约束/索引 |
|---|---|---|---|
| `city` | 城市字典 | 平台级 | `code` 唯一；启用城市排序 |
| `content_section` | 官方内容分区 | 平台级 | `code` 唯一；`ROAM_DAILY` 为默认分区 |
| `section_follow` | 用户关注分区 | 用户级 | `user_id,section_id` 唯一 |
| `user` | 用户账号 | 平台级 | `phone` 唯一 |
| `user_info` | 用户扩展资料 | 用户级 | `user_id` 主键 |
| `follow` | 用户关注关系 | 用户级 | `user_id,follow_user_id` 唯一 |
| `shop_type` | 商户分类 | 平台级 | 主键 |
| `shop` | 商户与坐标 | 平台级 | 城市、分类、状态组合查询索引 |
| `media_asset` | 临时/已绑定媒体 | 用户级 | `storage_path` 唯一；状态与过期时间索引 |
| `post` | 统一社区动态 | 城市/用户级 | 分区、作者、商户和城市信息流索引 |
| `post_media` | 动态媒体顺序 | 动态级 | `post_id,sort` 与 `media_asset_id` 唯一 |
| `post_like` | 动态点赞事实 | 用户/动态级 | `post_id,user_id` 唯一 |
| `post_comment` | 根评论和追加回复 | 动态级 | 按动态根评论、根讨论、父评论查询 |
| `post_comment_like` | 评论点赞事实 | 用户/评论级 | `comment_id,user_id` 唯一 |
| `shop_review` | 独立商户点评 | 商户/用户级 | `shop_id,user_id` 唯一 |
| `shop_review_media` | 点评媒体顺序 | 点评级 | `review_id,sort` 与 `media_asset_id` 唯一 |
| `voucher_product` | 团购商品 | 商户级 | 商户状态、销售时间索引 |
| `voucher_order` | 团购订单 | 用户级 | 用户状态时间、商品用户索引 |
| `user_voucher` | 用户券实例 | 用户级 | 券码与订单各自唯一 |

## 核心表字段摘要

### 社区与媒体

| 表 | 关键字段 | 业务规则 |
|---|---|---|
| `content_section` | `code,name,allow_shop_visit,status,sort` | 普通动态由服务端按 `ROAM_DAILY` 绑定；探店动态必须使用允许探店的启用分区。 |
| `media_asset` | `owner_user_id,storage_path,mime_type,file_size,width,height,status,bound_type,bound_id,expire_time` | `TEMPORARY` 资源有过期时间；绑定后不可通过临时媒体删除接口移除。 |
| `post` | `user_id,section_id,shop_visit,shop_id,city_code,title,content,liked_count,comment_count,status` | 普通动态 `shop_id` 为空；探店动态关联启用商户，城市取商户城市。 |
| `post_media` | `post_id,media_asset_id,sort` | 单条动态最多 9 个媒体，应用层保证排序连续与资源归属。 |
| `post_like` | `post_id,user_id,create_time` | 关系表是点赞事实；`post.liked_count` 为冗余聚合值。 |
| `post_comment` | `post_id,user_id,root_id,parent_id,reply_to_user_id,content,liked_count,reply_count,author_replied,status` | 根评论三类关联字段为空；回复必须属于同一动态根讨论。 |
| `post_comment_like` | `comment_id,user_id,create_time` | 关系表是评论点赞事实；聚合值可据此校正。 |

### 本地生活与点评

| 表 | 关键字段 | 业务规则 |
|---|---|---|
| `shop` | `type_id,city_code,images,address,x,y,avg_price,sold,comments,score,open_hours,status` | `score` 使用整数放大值；经纬度支持距离排序。 |
| `shop_review` | `shop_id,user_id,verified_user_voucher_id,score,content,status` | 用户每个商户至多一条点评；消费标识只能由服务端根据已核销用户券关联。 |
| `shop_review_media` | `review_id,media_asset_id,sort` | 只允许绑定当前用户的临时媒体，最多 9 张。 |

### 团购交易

| 表 | 关键字段 | 业务规则 |
|---|---|---|
| `voucher_product` | `shop_id,title,pay_price,original_price,deduction_value,sale_type,total_stock,available_stock,sold_count,purchase_limit,validity_type,status,version` | 商品是唯一团购商品模型；`sale_type` 支持 `NORMAL/SECKILL`，不拆旧秒杀表。 |
| `voucher_order` | `id,user_id,product_id,shop_id,product_title,unit_price,quantity,total_amount,pay_amount,pay_type,status` | 仅为团购订单，不再含 `voucher_id`；商品和金额字段为下单快照。 |
| `user_voucher` | `user_id,order_id,product_id,shop_id,voucher_code,status,valid_begin_time,expire_time` | 支付确认按 `order_id` 幂等发券；券码全局唯一。 |

## 主要索引

| 表 | 索引 | 列 |
|---|---|---|
| `post` | `idx_post_section_status_time` | `section_id,status,create_time,id` |
| `post` | `idx_post_user_status_time` | `user_id,status,create_time,id` |
| `post` | `idx_post_shop_status_time` | `shop_id,status,create_time,id` |
| `post` | `idx_post_city_status_time` | `city_code,status,create_time,id` |
| `post_comment` | `idx_comment_post_root_time` | `post_id,root_id,status,create_time,id` |
| `post_comment` | `idx_comment_root_status_time` | `root_id,status,create_time,id` |
| `shop_review` | `idx_review_shop_status_time` | `shop_id,status,create_time,id` |
| `voucher_product` | `idx_voucher_product_shop_status` | `shop_id,status,id` |
| `voucher_order` | `idx_order_user_status_time` | `user_id,status,create_time,id` |
| `user_voucher` | `idx_user_voucher_user_status_expire` | `user_id,status,expire_time,id` |

## 开发数据

种子脚本依次生成杭州城市、官方分区、商户分类、用户及资料、商户、Post、PostComment 和团购商品。所有样例直接面向当前表，不读取或转换旧 Blog、旧优惠券和旧秒杀数据。

`schema-init.sql` 包含 `DROP TABLE`，仅限明确允许丢弃数据的本地 Demo 库；运行数据库或生产数据必须先单独完成备份与执行方案评审。
