# 阶段 7：Threads 式评论、删除语义与排序契约

```yaml
stage: 7
updatedAt: 2026-09-02
status: 已实现
implementationStatus: 未实现
schemaSnapshotStatus: 已实现
runtimeDatabaseInitialized: 未实现
```

## 1. 阶段目标与边界

本阶段冻结动态根评论、追加式回复、评论点赞、删除占位、热门排序和首页热门评论摘要的最终契约，并直接更新 `schema-init.sql` 与 `seed-dev.sql`。本阶段不实现评论 Controller、Service、Mapper、Entity、小程序评论界面或真实 OpenAPI 路径，也不执行包含 `DROP TABLE` 的数据库初始化脚本。

阶段产物：

- 冻结 `CommentCreateDTO`、`CommentVO`、`CommentThreadVO` 和现有 `HighlightCommentVO` 字段。
- 冻结 7 个评论 HTTP 接口、operationId、鉴权和错误响应。
- 新增 `post_comment`、`post_comment_like` 最终 SQL 快照。
- 冻结根评论与直接回复关系、计数语义、删除占位和审核隐藏规则。
- 冻结热门评论分值、首页展示门槛、Redis 缓存和失效规则。
- 在 `seed-dev.sql` 中加入可解析旧评论的转换逻辑，不伪造缺失点赞用户关系。

第一阶段评论只支持文字，不支持评论图片、富文本、表情包、置顶、举报或管理员审核接口。

## 2. API 冻结

以下接口是阶段 8 的实现契约，当前状态均为“未实现”。Controller 内部路径使用 `/v1`，生产外部路径由 Nginx 增加 `/api`。

| 方法 | 路径 | 鉴权 | operationId | DTO/查询 | 响应 | 成功状态 |
|---|---|---|---|---|---|---:|
| GET | `/v1/posts/{postId}/comments` | 可选 | `listPostComments` | `sort,cursor,offset,size` | `Result<CursorPageResult<CommentThreadVO>>` | 200 |
| POST | `/v1/posts/{postId}/comments` | 登录 | `createPostComment` | `CommentCreateDTO` | `Result<CommentVO>` | 201 |
| GET | `/v1/comments/{commentId}/replies` | 可选 | `listCommentReplies` | `cursor,offset,size` | `Result<CursorPageResult<CommentVO>>` | 200 |
| POST | `/v1/comments/{commentId}/replies` | 登录 | `createCommentReply` | `CommentCreateDTO` | `Result<CommentVO>` | 201 |
| DELETE | `/v1/comments/{commentId}` | 作者 | `deleteComment` | 无 | 无 | 204 |
| PUT | `/v1/comments/{commentId}/like` | 登录 | `likeComment` | 无 | 无 | 204 |
| DELETE | `/v1/comments/{commentId}/like` | 登录 | `unlikeComment` | 无 | 无 | 204 |

公开读取使用可选 Bearer Token：匿名可以读取；携带无效、过期或前缀错误的 Token 返回 401，不能静默降级为匿名。写操作必须使用 `Authorization: Bearer <opaque-token>`。

### 2.1 路径与查询参数

所有 `postId`、`commentId` 在 JSON 和 OpenAPI 中声明为字符串，格式为 `^[1-9]\d*$`，服务端内部转换为 `Long`。

根评论列表参数：

| 参数 | 类型 | 必填 | 默认 | 约束 | 说明 |
|---|---|---:|---:|---|---|
| `sort` | string | 否 | `HOT` | `HOT/LATEST` | 根评论排序 |
| `cursor` | integer(int64) | 否 | 无 | 大于等于 0 | 服务端不透明排序游标 |
| `offset` | integer | 否 | `0` | 0～1000 | 同排序值累计偏移 |
| `size` | integer | 否 | `10` | 1～20 | 每个条目同时携带最多两条预览回复 |

回复列表参数：

| 参数 | 类型 | 必填 | 默认 | 约束 | 说明 |
|---|---|---:|---:|---|---|
| `cursor` | integer(int64) | 否 | 无 | 大于等于 0 | 创建时间升序游标 |
| `offset` | integer | 否 | `0` | 0～1000 | 同创建时间累计偏移 |
| `size` | integer | 否 | `20` | 1～50 | 追加加载回复数量 |

首次请求没有 `cursor` 时 `offset` 必须为 0。客户端只能原样透传 `nextCursor` 和 `nextOffset`，不得解析或自行计算游标。

## 3. 最终 DTO 与 OpenAPI Schema

### 3.1 `CommentCreateDTO`

```text
Schema name: CommentCreateDTO
type: object
required: [content]
additionalProperties: false
```

| 字段 | JSON 类型 | 必填 | Validation / Schema | 示例 |
|---|---|---:|---|---|
| `content` | string | 是 | `@NotBlank`，去除首尾空白后 1～1000 个字符 | `环境很好，下次还会再来。` |

根评论与回复复用同一 DTO。客户端不能提交 `postId`、`rootId`、`parentId`、`replyToUserId`、作者、计数、状态或审计时间；回复关系只由路径中的直接目标评论派生。

### 3.2 `CommentVO`

```text
Schema name: CommentVO
type: object
required: [id, rootId, author, deleted, postAuthor, likedCount, likedByMe, deletable, createdTime]
```

| 字段 | JSON 类型 | 可空 | 说明 |
|---|---|---:|---|
| `id` | string | 否 | 评论 ID |
| `rootId` | string | 否 | 所属讨论根 ID；根评论返回自身 ID |
| `author` | `UserVO` | 否 | 评论作者摘要；账号不存在时返回已注销用户占位 |
| `replyToUser` | `UserVO` | 是 | 被回复用户摘要；根评论为空 |
| `content` | string | 是 | 正常评论正文；删除占位为空 |
| `deleted` | boolean | 否 | 是否为根评论删除占位 |
| `postAuthor` | boolean | 否 | 评论作者是否为动态作者，用于显示“作者”标识 |
| `likedCount` | integer | 否 | 正常评论点赞数，最小 0；删除占位返回 0 |
| `likedByMe` | boolean | 否 | 当前用户是否点赞；匿名和删除占位为 false |
| `deletable` | boolean | 否 | 当前登录用户是否可以删除该正常评论 |
| `createdTime` | string(date-time) | 否 | 原始创建时间 |

审核隐藏的评论不返回普通用户。只有被删除的根评论在仍存在有效回复时返回占位；被删除回复不返回列表。

### 3.3 `CommentThreadVO`

```text
Schema name: CommentThreadVO
type: object
required: [root, previewReplies, replyCount, hasMoreReplies, nextReplyCursor, nextReplyOffset]
```

| 字段 | JSON 类型 | 说明 |
|---|---|---|
| `root` | `CommentVO` | 正常根评论或删除占位 |
| `previewReplies` | `CommentVO[]` | 按创建时间升序的前两条有效回复 |
| `replyCount` | integer | 当前有效回复总数，最小 0 |
| `hasMoreReplies` | boolean | 是否还有未展示的有效回复 |
| `nextReplyCursor` | integer(int64) | 预览末项时间游标；无预览为 0 |
| `nextReplyOffset` | integer | 预览末项同时间偏移；无预览为 0 |

阶段 8 必须批量查询一页根评论的预览回复，禁止对每个根评论逐条访问数据库。

### 3.4 `HighlightCommentVO`

沿用已经发布的 Schema：

| 字段 | JSON 类型 | 说明 |
|---|---|---|
| `id` | string | 热门根评论 ID |
| `author` | `UserVO` | 评论作者摘要 |
| `contentPreview` | string | 最多 120 个 Unicode code point 的正文摘要 |
| `likedCount` | integer | 点赞数，最小 0 |
| `replyCount` | integer | 有效回复数，最小 0 |

只返回正常根评论，删除占位和审核隐藏评论不能成为首页热门摘要。

## 4. 错误契约

| HTTP | code | 场景 |
|---:|---|---|
| 400 | `VALIDATION_FAILED` | DTO 或分页参数违反 Jakarta Validation |
| 400 | `INVALID_ID` | 路径 ID 不是正整数字符串 |
| 400 | `INVALID_ARGUMENT` | 首次请求单独提交 offset 等跨参数错误 |
| 401 | `UNAUTHORIZED` | 写操作未登录，或可选认证请求携带无效 Token |
| 403 | `FORBIDDEN` | 当前用户不是评论作者却尝试删除 |
| 404 | `POST_NOT_FOUND` | 动态不存在、隐藏或已删除 |
| 404 | `COMMENT_NOT_FOUND` | 评论不存在、审核隐藏，或不属于可见动态 |
| 409 | `COMMENT_STATUS_CONFLICT` | 回复目标或所属根评论已删除，或并发期间状态变化 |
| 500 | `INTERNAL_ERROR` | 未预期服务端异常 |

重复删除同一条由当前用户删除的评论返回 204；删除其他用户的评论始终返回 403。点赞和取消点赞均为幂等操作，重复 PUT/DELETE 返回 204。

## 5. 数据库冻结

### 5.1 数字枚举

| 领域 | 数字 | Java 语义 | 对外行为 |
|---|---:|---|---|
| 评论状态 | 0 | `NORMAL` | 正常展示和互动 |
| 评论状态 | 1 | `DELETED` | 作者删除；正文清空 |
| 评论状态 | 2 | `HIDDEN` | 审核隐藏；普通用户不可见 |
| 作者参与 | 0 | `false` | 根评论下无有效作者回复 |
| 作者参与 | 1 | `true` | 根评论下存在有效作者回复 |

### 5.2 `post_comment`

| 字段 | MySQL 类型 | Null | 默认 | 说明 |
|---|---|---|---|---|
| `id` | bigint unsigned | 否 | auto_increment | 主键 |
| `post_id` | bigint unsigned | 否 | 无 | 动态逻辑关联 |
| `user_id` | bigint unsigned | 否 | 无 | 评论作者逻辑关联 |
| `root_id` | bigint unsigned | 是 | null | 所属根评论；根评论为空 |
| `parent_id` | bigint unsigned | 是 | null | 直接回复目标；根评论为空 |
| `reply_to_user_id` | bigint unsigned | 是 | null | 被回复用户；根评论为空 |
| `content` | varchar(1000) | 是 | null | 正常状态必须非空；删除时清空 |
| `liked_count` | int unsigned | 否 | 0 | 可校正点赞计数 |
| `reply_count` | int unsigned | 否 | 0 | 根评论有效回复计数；回复固定为 0 |
| `author_replied` | tinyint unsigned | 否 | 0 | 根评论作者参与标识；回复固定为 0 |
| `status` | tinyint unsigned | 否 | 0 | 0 正常、1 删除、2 隐藏 |
| `create_time` | timestamp | 否 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | timestamp | 否 | CURRENT_TIMESTAMP/on update | 更新时间 |

索引：

- `PRIMARY(id)`。
- `idx_comment_post_root_time(post_id,root_id,status,create_time,id)`：根评论候选与稳定时间排序。
- `idx_comment_root_status_time(root_id,status,create_time,id)`：按根讨论正序追加回复。
- `idx_comment_parent_status(parent_id,status,id)`：校验直接子回复和删除关系。

应用必须维护以下跨字段约束，不增加数据库 `CHECK`：

```text
根评论
root_id = null
parent_id = null
reply_to_user_id = null

回复
root_id != null
parent_id != null
reply_to_user_id != null
root_id 指向同一 post_id 的根评论
parent_id 指向同一 post_id、同一 root_id 讨论内的正常评论
reply_to_user_id 等于 parent_id 对应评论的 user_id
```

### 5.3 `post_comment_like`

| 字段 | MySQL 类型 | Null | 默认 | 说明 |
|---|---|---|---|---|
| `id` | bigint unsigned | 否 | auto_increment | 主键 |
| `comment_id` | bigint unsigned | 否 | 无 | 评论逻辑关联 |
| `user_id` | bigint unsigned | 否 | 无 | 点赞用户逻辑关联 |
| `create_time` | timestamp | 否 | CURRENT_TIMESTAMP | 点赞时间 |

索引：主键；唯一索引 `uk_comment_like_comment_user(comment_id,user_id)`；用户记录索引 `idx_comment_like_user_time(user_id,create_time,id)`。

点赞关系是事实真源，`post_comment.liked_count` 是可重算冗余值。数据库仍不声明物理外键。

## 6. 计数与删除语义

### 6.1 计数定义

- `post.comment_count`：该动态状态为 `NORMAL` 的根评论和回复总数，不包含删除占位与审核隐藏记录。
- 根评论 `reply_count`：所属讨论中状态为 `NORMAL` 的全部回复数量，不只统计直接回复。
- 回复记录的 `reply_count` 固定为 0。
- `liked_count`：对应评论的有效点赞关系数量；评论删除或隐藏后对外返回 0，但事实关系在物理清理前保留。
- 计数更新使用非负条件更新；事实关系与冗余计数不一致时以事实表重算修复。

### 6.2 作者删除

- 删除是逻辑删除：`status=DELETED`、`content=null`，不修改作者和关系字段。
- 删除正常根评论时将动态 `comment_count - 1`。若根评论仍有有效回复，根评论列表返回“该评论已删除”占位并继续展示回复；若没有有效回复，则整个讨论不返回。
- 删除正常回复时将动态 `comment_count - 1`，并将所属根评论 `reply_count - 1`；被删除回复不展示，其他回复仍按时间追加并通过 `replyToUser` 保留被回复用户信息。
- 删除动态作者的回复后，如果该讨论已不存在其他正常作者回复，重新计算根评论 `author_replied=0`。
- 根评论已删除后，所属讨论冻结，不允许新增回复；已有有效回复只读保留。
- 已删除评论由同一作者再次删除返回 204，不重复修改计数或缓存。

### 6.3 审核隐藏

阶段 7 只冻结状态语义，不提供审核接口：

- 隐藏根评论时，普通用户不可见整个讨论，且不能成为热门评论。
- 隐藏回复时仅该回复不可见，根评论和其他正常回复仍可见。
- 状态由 `NORMAL` 变为 `HIDDEN` 时必须同步扣减动态评论数、根回复数并重算作者参与标识；恢复时执行反向操作。

## 7. 排序与热门评论

### 7.1 根评论最新排序

`LATEST` 按 `create_time DESC, id DESC`。游标为创建时间 epoch millisecond，同一时间通过 `nextOffset` 续页。

### 7.2 根评论热门排序

`HOT` 使用可由数据库字段复算的固定分值：

```text
commentHotScore = floor(unixTimestamp(createTime) / 3600)
                + likedCount * 1000
                + replyCount * 2000
                + authorReplied * 3000
```

候选仅限 `status=NORMAL` 的根评论。按 `commentHotScore DESC, create_time DESC, id DESC` 排序；游标为分值，同分通过 `nextOffset` 续页。点赞、回复或状态在翻页期间变化可能产生自然排序漂移，客户端继续按字符串评论 ID 去重。

### 7.3 回复排序

回复固定按 `create_time ASC, id ASC`，不提供热门排序。根评论列表预览最早两条正常回复，完整回复列表从预览返回的时间游标继续追加；同一创建时间使用偏移避免遗漏。

### 7.4 首页热门评论门槛

首页每条动态最多返回一个 `HighlightCommentVO`。候选必须是正常根评论，并至少满足一项：

- `liked_count >= 3`；
- `reply_count >= 2`；
- `author_replied = 1`。

合格候选使用相同 `commentHotScore` 取第一名；没有合格候选时返回 `null`，不显示占位。用户屏蔽关系尚未进入当前数据模型，阶段 8 必须通过可扩展的可见性过滤入口组装结果，后续增加屏蔽事实后不得绕过该入口。

## 8. 事务与并发

### 8.1 创建根评论

同一数据库事务：校验并锁定正常动态、写入根评论、将动态 `comment_count + 1`。提交后失效热门评论缓存。

### 8.2 创建回复

统一锁顺序为动态 → 根评论 → 直接目标评论：

1. 动态必须正常可见。
2. 根评论和直接目标必须属于同一动态且状态为 `NORMAL`。
3. 服务端派生 `root_id`、`parent_id`、`reply_to_user_id`。
4. 写入回复，将根评论 `reply_count + 1`，将动态 `comment_count + 1`。
5. 回复作者是动态作者时，将根评论 `author_replied=1`。
6. 事务提交后失效热门评论缓存。

任何条件更新数不符合预期时抛出 `COMMENT_STATUS_CONFLICT` 并回滚整个事务。

### 8.3 点赞幂等

- PUT 先写唯一点赞事实，只有实际新增时 `liked_count + 1`。
- DELETE 先删除当前用户事实，只有实际删除时 `liked_count - 1`。
- 只有状态为 `NORMAL` 的评论可以新增或取消点赞。
- 数据库事务提交后再失效热门评论缓存；缓存失败不回滚事实关系。

## 9. Redis 缓存

热门评论缓存键：

```text
post:highlight-comment:{postId}
```

- 类型：STRING，保存热门根评论 ID；无合格评论使用短 TTL 空值标记。
- 正常命中 TTL：10 分钟；空值 TTL：1 分钟，并加入小范围随机抖动。
- 首页信息流批量获取缓存，未命中的动态批量回源计算，禁止逐动态数据库查询。
- 新增根评论、回复、评论点赞/取消、评论删除、评论隐藏/恢复均在数据库事务提交后删除对应缓存键。
- 删除缓存失败记录告警并依赖短 TTL 自愈，不回滚已经提交的评论事实。
- 缓存只保存候选 ID，返回前仍需校验评论与动态可见状态，防止并发删除后短暂展示失效内容。

## 10. 旧评论转换

`seed-dev.sql` 使用逻辑关系转换可解析的 `blog_comments`：

| 旧字段 | 新字段 | 规则 |
|---|---|---|
| `id` | `id` | 保留原 ID |
| `blog_id` | `post_id` | 依赖阶段 3 保留的 Post ID |
| `user_id` | `user_id` | 原值 |
| `parent_id=0` | 根关系 | `root_id/parent_id/reply_to_user_id` 均为空 |
| `parent_id>0` | `root_id` | 旧 `parent_id` 指向根评论 |
| `answer_id` | `parent_id` | 非 0 时作为直接目标；否则回退根评论 |
| 目标评论作者 | `reply_to_user_id` | 由直接目标派生，不信任外部值 |
| `content` | `content` | 原值；超过 1000 字的存量数据必须在重建前进入异常清单 |
| `liked` | `liked_count` | null 转 0；仅保留聚合数，不伪造点赞用户 |
| `status` | `status` | null/0 映射 NORMAL；1/2 映射 HIDDEN；隐藏根评论下的回复一并映射 HIDDEN |
| 审计时间 | 同名字段 | 原值 |

只有根评论存在、直接目标存在且属于同一旧 Blog 的回复才转换；孤儿回复必须在真实数据重建前导出核对，不伪造父关系。转换后仅对确有旧评论事实的 Post 按正常记录数校正 `comment_count`，避免空开发样例覆盖历史聚合值。

旧数据没有评论点赞用户集合时 `post_comment_like` 保持为空。阶段 8 或旧接口退役前如果能从可靠 Redis 数据恢复，必须核对用户、评论和计数后再写事实表。

## 11. 阶段验收

- 两张评论表字段、默认值和索引与本文一致，`schema-init.sql` 业务表数量为 19。
- `CommentCreateDTO`、`CommentVO`、`CommentThreadVO` 和 `HighlightCommentVO` 字段完整且业务 ID 均为字符串。
- 7 个接口的路径、operationId、鉴权、状态码和错误码无歧义。
- 根、直接父级、被回复用户关系可以表达任意深度回复，展示始终只缩进一级。
- 删除根评论在有回复时保留占位，没有回复时隐藏；删除回复不破坏其余追加顺序。
- 热门公式、展示门槛、同分偏移和缓存失效事件全部固定。
- 旧评论转换不接收跨 Blog 目标、不伪造孤儿关系或点赞用户。
- `DATABASE_SCHEMA.md` 与 `BACKEND_DEVELOPMENT.md` 同步；不创建数据库版本目录。
- 不执行 `schema-init.sql`，运行数据库初始化状态保持“未实现”。

阶段 8 开工前必须据此生成最终 Java DTO、VO、Entity、Mapper、Service、Controller、OpenAPI 注解和自动测试；如果实现中需要修改本契约，必须先更新本文件并说明原因。

## 12. 实际验证记录

2026-09-02 完成以下验证：

- Reactor `mvn test`：51 项测试通过、0 失败；10 项依赖外部环境的测试按默认配置跳过。
- 设置 `RUN_INTEGRATION_TESTS=true` 且强制 `spring.sql.init.mode=never` 后，5 项真实 OpenAPI/Sa-Token 运行测试通过；未执行数据库初始化。
- Reactor `mvn -DskipTests compile`：三模块编译成功。
- 实际 OpenAPI 已生成 `PostCreateDTO`、`PostUpdateDTO` Schema，旧 `PostCreateRequest`、`PostUpdateRequest` Schema 不再存在。
- 静态扫描未发现 Swagger 响应注解完整限定名、通配符 import、`System.out`、`System.err`、`printStackTrace()` 或以 `Request` 结尾的业务模型。
- `schema-init.sql` 静态统计为 19 张业务表；仅检查 DDL 与旧评论转换 SQL，未执行含 `DROP TABLE` 的脚本。
- Markdown 相对链接检查通过，`git diff --check` 通过。
