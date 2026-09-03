# 阶段 8：Threads 式评论后端实现记录

```yaml
stage: 8
updatedAt: 2026-09-03
status: 开发中
contractStatus: 已实现
backendStatus: 已实现
miniappStatus: 已实现
runtimeDatabaseInitialized: 未实现
```

## 1. 本阶段范围

本阶段按阶段 7 冻结的契约实现评论后端并完成小程序消费契约对齐，不修改业务数据库以外的结构来源，不执行 `schema-init.sql`。Java DTO/VO、Entity、Mapper、Service、Controller、Sa-Token 路由边界、热门评论批量查询和 Controller 契约测试已完成；小程序已通过 Service 将正式 `CommentVO/CommentThreadVO` 适配为页面模型。真机验收和运行数据库重建尚未完成。

评论统一使用 `PostComment` 和 `PostCommentLike`，保留根评论、直接父评论和被回复用户关系。Controller 内部路径为 `/v1`，生产由 Nginx 添加 `/api`。

## 2. 已冻结的最终接口

| 方法 | 路径 | operationId | 请求/响应 | 当前状态 |
|---|---|---|---|---|
| GET | `/v1/posts/{postId}/comments` | `listPostComments` | `sort,cursor,offset,size -> Result<CursorPageResult<CommentThreadVO>>` | 已实现 |
| POST | `/v1/posts/{postId}/comments` | `createPostComment` | `CommentCreateDTO -> 201 Result<CommentVO>` | 已实现 |
| GET | `/v1/comments/{commentId}/replies` | `listCommentReplies` | `cursor,offset,size -> Result<CursorPageResult<CommentVO>>` | 已实现 |
| POST | `/v1/comments/{commentId}/replies` | `createCommentReply` | `CommentCreateDTO -> 201 Result<CommentVO>` | 已实现 |
| DELETE | `/v1/comments/{commentId}` | `deleteComment` | 无 -> 204 | 已实现 |
| PUT | `/v1/comments/{commentId}/like` | `likeComment` | 无 -> 204 | 已实现 |
| DELETE | `/v1/comments/{commentId}/like` | `unlikeComment` | 无 -> 204 | 已实现 |

读取接口允许匿名或可选 Bearer Token；写操作必须登录。错误码沿用阶段 7：`VALIDATION_FAILED`、`INVALID_ID`、`INVALID_ARGUMENT`、`UNAUTHORIZED`、`FORBIDDEN`、`POST_NOT_FOUND`、`COMMENT_NOT_FOUND`、`COMMENT_STATUS_CONFLICT`、`INTERNAL_ERROR`。

## 3. DTO 与 VO

- `CommentCreateDTO.content`：`@NotBlank`、最大 1000 字，服务端 trim 后保存；客户端不能提交关系、作者、计数或状态字段。
- `CommentVO`：字符串 `id/rootId`、作者、被回复用户、正文、删除占位、动态作者标识、点赞数、当前用户点赞状态、可删除状态和创建时间。
- `CommentThreadVO`：根评论、最多两条预览回复、有效回复总数、是否还有回复和继续加载游标。
- `HighlightCommentVO`：沿用既有首页摘要 Schema；`PostService` 通过 `PostCommentService.findHighlights` 批量组装，避免逐动态请求评论。

OpenAPI 已注册 `CommentCreateDTO`、`CommentVO`、`CommentThreadVO` 和既有 `HighlightCommentVO`，Controller 使用正常 import 的 `ApiResponse`、`ApiResponses`、`Schema`。

## 4. 事务、计数与缓存

- 创建根评论：锁定正常动态，插入评论并增加 `post.comment_count`。
- 创建回复：按动态 → 根评论 → 直接目标顺序校验和锁定，派生 `rootId`、`parentId`、`replyToUserId`，同步根评论回复数、动态评论数和作者参与标识。
- 删除：逻辑删除并清空正文；删除回复同步扣减根评论和动态计数，根评论保留有效回复时由查询返回占位。
- 点赞：唯一事实表 `post_comment_like` 幂等写入/删除后再调整冗余计数。
- 热门评论缓存键为 `post:highlight-comment:{postId}`，事务提交后失效；缓存失败只告警，不回滚已提交事实。
- Post 信息流由批量 `findHighlights` 组装首页摘要，匿名状态和个性化点赞状态均在同一上下文批量计算。

## 5. SQL 与运行边界

`schema-init.sql` 已包含 `post_comment`、`post_comment_like` 两张表，`seed-dev.sql` 已包含旧评论可解析转换。当前快照共 19 张业务表；本阶段未执行包含 `DROP TABLE` 的初始化脚本，运行数据库仍标记为未实现。

## 6. 验收与剩余工作

已完成：

- 三模块 `mvn -DskipTests compile` 通过。
- `mvn test` 通过：57 项测试通过、0 失败，10 项外部环境测试按默认配置跳过。
- 设置 `RUN_INTEGRATION_TESTS=true` 且 `spring.sql.init.mode=never` 后，5 项真实 OpenAPI/Sa-Token 运行测试通过。
- 评论路径、DTO 校验、字符串 ID、204/201 状态和 OpenAPI Schema 已覆盖。
- GET 评论和回复已纳入可选鉴权白名单，写操作继续由 Sa-Token 保护。
- Swagger 完整限定注解、通配符 import、控制台日志、`Request` 业务模型静态扫描通过；Markdown 链接、`git diff --check` 和 19 张表静态统计通过。
- 小程序 API 层已使用 `CommentCreateDTO`、`CommentResponse`、`CommentThreadResponse`，Service 统一转换 `deleted`、`postAuthor`、`deletable`、`previewReplies` 和缺失的 `postId`。
- 小程序 `npm run verify` 通过：32 个测试文件、114 项测试；类型检查、ESLint、Stylelint 和 Vitest 均通过。

待完成：

- 在隔离开发库重建快照并核对旧评论转换数量，不能在现有生产或需保留数据的库执行初始化。
- 完成 Android/iOS 真机、隔离开发库和全栈联调验收后，才能将阶段总状态改为“已实现”。
