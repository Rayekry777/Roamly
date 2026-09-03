# 阶段 6：推荐、关注与分区信息流实现记录

```yaml
updatedAt: 2026-09-02
status: 开发中
sourceImplemented: true
runtimeDatabaseVerified: false
```

## 1. 阶段范围

本阶段冻结并实现统一动态的三个读取入口：

- 城市推荐流：`GET /v1/feeds/recommended`。
- 当前用户关注流：`GET /v1/feeds/following`。
- 分区动态流：`GET /v1/sections/{sectionId}/posts`。

本阶段不实现评论、搜索、个性化推荐模型或 Redis 信息流回源。`PostCardVO.highlightComment` 继续返回 `null`，直到评论阶段建立正常可见根评论、排序和缓存失效规则。

## 2. HTTP 契约

### 2.1 通用分页参数

| 参数 | 类型 | 必填 | 默认值 | 约束 | 说明 |
|---|---|---:|---:|---|---|
| `cursor` | `long` | 否 | 无 | 大于等于 0 | 首次不传，后续原样回传 `nextCursor` |
| `offset` | `int` | 否 | `0` | 0～1000 | 同一排序值内已消费的记录数 |
| `size` | `int` | 否 | `10` | 1～20 | 每次返回数量 |

游标是服务端生成的不透明排序值。客户端不得将其解释为时间、热度或业务 ID，也不得自行运算。首次请求没有 `cursor` 时，`offset` 必须为 0；否则返回 `400 INVALID_ARGUMENT`。

响应统一为 `Result<CursorPageResult<PostCardVO>>`：

```text
data.items       当前批次动态，最多 size 条
data.nextCursor  下一页排序游标；空结果为 0
data.nextOffset  nextCursor 对应排序值内累计偏移
data.hasMore     本次多取一条后判断是否还有下一页
```

### 2.2 推荐流

```http
GET /v1/feeds/recommended?cityCode=330100&cursor={cursor}&offset={offset}&size=10
```

- 鉴权：公开读取，可携带 Bearer Token 获取 `likedByMe` 和 `followingAuthor`。
- `cityCode` 必填，去除首尾空白后最长 16 个字符，必须对应启用城市。
- 排序：固定热度分值降序，再按 `create_time DESC, id DESC` 稳定排序。
- 主要错误：参数校验 400、无效可选 Token 401、城市不存在或停用 404、服务异常 500。

### 2.3 关注流

```http
GET /v1/feeds/following?cursor={cursor}&offset={offset}&size=10
Authorization: Bearer <opaque-token>
```

- 鉴权：必须登录。
- 候选：`follow.user_id` 为当前用户且 `follow_user_id` 等于动态作者。
- 排序：`create_time DESC, id DESC`。
- 主要错误：参数校验 400、未登录或 Token 无效 401、服务异常 500。

### 2.4 分区流

```http
GET /v1/sections/{sectionId}/posts?sort=LATEST&cityCode=330100&cursor={cursor}&offset={offset}&size=10
```

- 鉴权：公开读取，可携带 Bearer Token 获取个性化状态。
- `sectionId` 使用字符串承载，服务端转换为 `Long`。
- `sort` 默认为 `LATEST`，只允许 `LATEST` 或 `HOT`。
- `ROAM_DAILY` 分区必须提交启用的 `cityCode`，避免不同城市的日常内容混流。
- 其他分区的 `cityCode` 可选；提交后同样必须对应启用城市，并按城市过滤。
- `LATEST` 使用发布时间游标；`HOT` 使用固定热度分值游标。
- 主要错误：参数或 ID 无效 400、无效可选 Token 401、分区或城市不存在/停用 404、服务异常 500。

## 3. 排序与游标

### 3.1 固定热度分值

推荐流和分区热门流使用可从数据库事实字段复算的固定公式：

```text
hotScore = floor(unixTimestamp(createTime) / 3600)
         + likedCount * 1000
         + commentCount * 2000
```

SQL 与 Java 必须使用相同权重和小时粒度。该公式是阶段 6 的确定性基线，不代表最终个性化推荐算法；后续若调整权重或衰减方式，必须同步修改 SQL、Java、测试和本文档，并评估翻页中的排序漂移。

### 3.2 同值偏移

查询条件包含当前边界值（`score <= cursor` 或 `create_time <= cursorTime`），再用 `offset` 跳过已经返回的同值记录。每页多查询一条判断 `hasMore`，只将前 `size` 条组装为响应。

下一页偏移计算规则：

```text
本页末项排序值 != 请求 cursor
→ nextOffset = 本页中等于末项排序值的数量

本页末项排序值 == 请求 cursor
→ nextOffset = requestOffset + 本页中等于末项排序值的数量
```

这使同一秒发布或热度分相同的动态不会仅因排序值相同而被跳过。动态在翻页期间新增、删除或计数变化仍可能造成自然的信息流漂移，客户端继续按动态 ID 去重。

### 3.3 时间边界

- 数据库字段为 `timestamp`，时间游标对外使用 epoch millisecond。
- 服务端按 `Asia/Shanghai` 将 `LocalDateTime` 与 epoch millisecond 相互转换。
- 热度 SQL 使用 `UNIX_TIMESTAMP(create_time)`，避免把格式化时间暴露给客户端。

## 4. 数据访问与索引

三个列表只查询状态正常的 `post`，列表返回后批量装配作者、分区、媒体、点赞状态和作者关注状态，避免逐条 HTTP 请求。评论尚未实现，因此不读取热门评论。

关注流以 MySQL 事实关系查询为准：

```text
follow(user_id, follow_user_id)
→ EXISTS 匹配 post.user_id
→ post(status, create_time, id) 排序
```

`schema-init.sql` 中 `follow` 增加：

- 唯一索引 `uk_follow_user_target(user_id, follow_user_id)`，保证关注关系唯一。
- 普通索引 `idx_follow_target_user(follow_user_id, user_id)`，支持发布后查找粉丝并投递时间线。

`post` 继续使用阶段 3 已建立的城市、分区和用户时间索引。热度公式是计算排序，首版会在城市或分区候选集上计算；数据量增长后再依据慢查询证据选择物化热度或 Redis 排名，不提前增加冗余字段。

## 5. Redis 与一致性

- `post`、`follow` 是关注流读取的事实来源。
- 发布成功后的 `feed:following:{userId}` ZSET 投递继续保留，Redis 失败不回滚已经提交的动态。
- 阶段 6 不从该 ZSET 读取，避免缓存缺失、历史数据未回填或短暂投递失败造成漏动态。
- 推荐和分区热门流当前直接从数据库固定公式计算，不依赖 `post:hot:*` 或 `section:hot:*`。
- Redis 时间线和热门集合的读取切换必须在后续阶段先补齐回填、幂等、丢失补偿和数据库降级策略。

## 6. 鉴权与信息组装

- 推荐流和分区流加入 Sa-Token 可选认证白名单：没有 Token 时匿名浏览；提交错误前缀、无效或过期 Token 时返回 401，不静默降级。
- 关注流使用标准 `BearerAuth`，由路由拦截器要求登录。
- Service 只通过 `CurrentUserProvider` 获取当前用户，不直接散落 `StpUtil` 调用。
- 匿名响应的 `likedByMe=false`、`followingAuthor=false`。
- 登录响应批量查询本页点赞事实和关注关系，避免逐动态查询。
- 本阶段不记录 Token、手机号或完整响应等敏感信息。

## 7. 小程序衔接

阶段 5 已建立推荐、关注和分区流的独立列表、游标、偏移与滚动位置。联调时遵循：

- 推荐流始终携带当前 `cityCode`。
- “漫游日常”分区流始终携带当前 `cityCode`；其他分区按用户筛选决定是否携带。
- `cursor` 为空时不发送 `cursor`，且 `offset=0`。
- 后续请求只透传服务端返回的 `nextCursor`、`nextOffset`。
- 下拉刷新清空游标和偏移；上拉加载按字符串动态 ID 去重。
- `hasMore=false` 后停止加载。
- `highlightComment=null` 时不渲染热门评论占位。
- 401 按现有会话策略清理登录态，但不删除发布草稿。

## 8. 验证记录

2026-09-02 已完成：

- 定向信息流测试 13 项通过，覆盖推荐流多取一条、推荐与分区热度游标、分区最新时间游标、同时间偏移累计、漫游日常城市必填、首次请求禁止单独 offset、Controller 参数透传和可选认证边界。
- Reactor `mvn test` 通过：51 项，0 失败，10 项外部环境测试默认跳过。
- 使用本地隔离配置并强制 `spring.sql.init.mode=never` 执行 `OpenApiAndAuthRuntimeTest`：5 项全部通过。
- 真实 OpenAPI 已包含三个信息流接口，operationId 唯一、Schema 引用可解析、Bearer 声明与可选认证一致，推荐流和分区流的 `size=21` 均触发统一 400 校验响应。
- `mvn -DskipTests compile`、Swagger 完整限定名/通配符 import/控制台输出静态扫描、Markdown 链接检查和 `git diff --check` 均已通过。
- 补充 Spring MVC `HandlerMethodValidationException` 统一处理，确认分区流方法参数越界不再误报 500。

## 9. 未完成项与阶段状态

源码、鉴权、OpenAPI 和自动测试已经实现，但以下事项尚未完成：

- 当前运行数据库未确认按最新 `schema-init.sql` 快照重建，不能把新增 `follow` 索引视为已应用事实。
- 未对真实 `post` 数据执行三个查询的结果集、执行计划和性能验收。
- 评论表和热门评论选择尚未实现，`highlightComment` 固定为空。
- 小程序与真实信息流数据的联调及 Android、iOS 真机验收未完成。

因此阶段 6 总状态保持“开发中”；待开发库快照、真实数据查询和客户端联调验收完成后再改为“已实现”。
