# 阶段 3：统一动态、动态媒体、动态点赞与 Blog 转换契约

```yaml
stage: 3
updatedAt: 2026-09-02
status: 已实现
implementationStatus: 已实现
schemaSnapshotStatus: 已实现
runtimeDatabaseInitialized: 未实现
```

## 1. 阶段目标与边界

本阶段冻结统一动态、动态媒体、动态点赞的接口边界、字段、索引、事务和旧 Blog 开发数据转换规则，并直接更新 `schema-init.sql` 与 `seed-dev.sql`。本阶段不实现 Post Controller、Service、Mapper、Entity、OpenAPI 运行契约或小程序页面，也不执行包含 `DROP TABLE` 的数据库初始化脚本。

阶段产物：

- 新增 `post`、`post_media`、`post_like` 三张表的最终快照。
- 将 `seed-dev.sql` 中现有 Blog 样例转换到 `post`，保留原 ID、计数和审计时间。
- 明确普通动态、探店动态、媒体绑定、点赞幂等及作者权限规则。
- 明确旧图片和旧 Redis 点赞关系的延后转换边界，不伪造缺失数据。

数据库继续采用完整快照方式维护，不创建 Flyway、Liquibase、编号 SQL、迁移目录或回退脚本。

## 2. API 冻结

以下接口是阶段 4 的实现契约。Controller、Service、持久化模型、权限、OpenAPI 和自动测试均已实现；由于运行数据库尚未按 17 表快照重建，阶段 4 整体仍为“开发中”。

| 方法 | 路径 | 鉴权 | operationId | 请求/响应 | 成功状态 |
|---|---|---|---|---|---:|
| POST | `/v1/posts` | 登录 | `createPost` | `PostCreateDTO -> Result<IdVO>` | 201 |
| GET | `/v1/posts/{postId}` | 可选 | `getPost` | `Result<PostDetailVO>` | 200 |
| PUT | `/v1/posts/{postId}` | 作者 | `updatePost` | `PostUpdateDTO -> Result<PostDetailVO>` | 200 |
| DELETE | `/v1/posts/{postId}` | 作者 | `deletePost` | 无 | 204 |
| GET | `/v1/users/me/posts` | 登录 | `listMyPosts` | `page,size -> Result<PageResult<PostCardVO>>` | 200 |
| GET | `/v1/users/{userId}/posts` | 公开 | `listUserPosts` | `page,size -> Result<PageResult<PostCardVO>>` | 200 |
| PUT | `/v1/posts/{postId}/like` | 登录 | `likePost` | 无 | 204 |
| DELETE | `/v1/posts/{postId}/like` | 登录 | `unlikePost` | 无 | 204 |
| GET | `/v1/posts/{postId}/likes` | 公开 | `listPostLikes` | `page,size -> Result<PageResult<UserVO>>` | 200 |

### 2.1 `PostCreateDTO`

| 字段 | JSON 类型 | 必填 | 约束 |
|---|---|---|---|
| `title` | string | 否 | 去除首尾空白；空白转为 `null`；最多 120 字 |
| `content` | string | 是 | 去除首尾空白后 1～5000 字 |
| `mediaIds` | string[] | 否 | 默认空数组；最多 9 个正整数 ID；不得重复 |
| `shopVisit` | boolean | 是 | 探店开关 |
| `sectionId` | string | 条件必填 | 仅探店时必填，必须是启用且允许探店的分区 |
| `shopId` | string | 条件必填 | 仅探店时必填，必须是启用商户 |

跨字段规则：

```text
shopVisit=false
→ sectionId、shopId 必须都不提交
→ 服务端按 code=ROAM_DAILY 查询分区
→ cityCode 取当前用户资料；未设置时使用当前已启用的首发城市 330100

shopVisit=true
→ sectionId、shopId 必须同时提交
→ 分区必须 status=ENABLED 且 allowShopVisit=true
→ 商户必须 status=ENABLED
→ cityCode 始终取商户 cityCode，不接受客户端提交
```

`PostUpdateDTO` 与创建字段一致，采用完整替换语义；更新后仍必须满足相同跨字段约束。客户端不能提交作者、城市、计数、状态和审计时间。

### 2.2 响应模型边界

`PostCardVO` 至少包含：动态 ID、作者摘要、分区摘要、标题、正文摘要、媒体列表、探店标识、发布时间、点赞数、评论数、当前用户点赞状态、当前用户关注作者状态和可空热门评论摘要。

`PostDetailVO` 在卡片模型上增加完整正文、完整媒体、可空商户摘要、`editable`、`deletable` 和默认评论排序。只有 `shopVisit=true` 时返回商户摘要。

所有业务 ID 在 JSON 与 OpenAPI 中保持字符串。匿名读取时 `likedByMe=false`、`followingAuthor=false`；携带无效 Bearer Token 时仍返回 401，不降级为匿名。

### 2.3 错误契约

| HTTP | code | 场景 |
|---:|---|---|
| 400 | `INVALID_ID` | 路径或媒体 ID 不是正整数字符串 |
| 400 | `INVALID_ARGUMENT` | 请求字段或普通/探店跨字段规则不满足 |
| 400 | `SECTION_NOT_ALLOWED_FOR_SHOP_VISIT` | 探店分区不允许发布探店内容 |
| 401 | `UNAUTHORIZED` | 写操作未登录或 Token 无效 |
| 403 | `FORBIDDEN` | 非作者更新或删除动态 |
| 403 | `MEDIA_NOT_OWNED` | 媒体不属于当前用户 |
| 404 | `POST_NOT_FOUND` | 动态不存在、已删除或对当前用户不可见 |
| 404 | `SECTION_NOT_FOUND` | 分区不存在或停用 |
| 404 | `SHOP_NOT_FOUND` | 商户不存在或停用 |
| 404 | `MEDIA_NOT_FOUND` | 媒体不存在 |
| 409 | `MEDIA_ALREADY_BOUND` | 媒体已被其他业务占用 |
| 409 | `MEDIA_EXPIRED` | 临时媒体已经过期或删除 |
| 409 | `POST_STATUS_CONFLICT` | 并发操作期间动态状态发生变化 |

## 3. 数据库冻结

### 3.1 数字枚举

| 领域 | 数字 | Java 语义 |
|---|---:|---|
| 动态状态 | 0 | `NORMAL` |
| 动态状态 | 1 | `HIDDEN` |
| 动态状态 | 2 | `DELETED` |
| 探店标识 | 0 | `false` |
| 探店标识 | 1 | `true` |
| 媒体绑定类型 | 1 | `POST` |

数据库不增加跨字段 `CHECK`。普通/探店一致性、分区能力、商户状态和媒体状态由 Service 在事务内校验，以避免依赖不同 MySQL 版本的约束行为。

### 3.2 `post`

| 字段 | MySQL 类型 | Null | 默认 | 说明 |
|---|---|---|---|---|
| `id` | bigint unsigned | 否 | auto_increment | 主键；转换旧 Blog 时保留原 ID |
| `user_id` | bigint unsigned | 否 | 无 | 发布用户逻辑关联 |
| `section_id` | bigint unsigned | 否 | 无 | 官方分区逻辑关联 |
| `shop_visit` | tinyint unsigned | 否 | 0 | 是否探店 |
| `shop_id` | bigint unsigned | 是 | null | 探店商户；普通动态为空 |
| `city_code` | varchar(16) | 否 | 无 | 城市稳定编码 |
| `title` | varchar(120) | 是 | null | 可选标题 |
| `content` | varchar(5000) | 否 | 无 | 动态正文 |
| `liked_count` | int unsigned | 否 | 0 | 可校正的点赞冗余计数 |
| `comment_count` | int unsigned | 否 | 0 | 可校正的评论冗余计数 |
| `status` | tinyint unsigned | 否 | 0 | 动态状态 |
| `create_time` | timestamp | 否 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | timestamp | 否 | CURRENT_TIMESTAMP/on update | 更新时间 |

索引：

- 主键 `PRIMARY(id)`。
- `idx_post_section_status_time(section_id,status,create_time,id)`：分区时间流。
- `idx_post_user_status_time(user_id,status,create_time,id)`：用户动态列表。
- `idx_post_shop_status_time(shop_id,status,create_time,id)`：商户探店动态。
- `idx_post_city_status_time(city_code,status,create_time,id)`：城市推荐候选。

### 3.3 `post_media`

| 字段 | MySQL 类型 | Null | 默认 | 说明 |
|---|---|---|---|---|
| `id` | bigint unsigned | 否 | auto_increment | 主键 |
| `post_id` | bigint unsigned | 否 | 无 | 动态逻辑关联 |
| `media_asset_id` | bigint unsigned | 否 | 无 | 媒体资产逻辑关联 |
| `sort` | tinyint unsigned | 否 | 无 | 动态内顺序，从 0 开始 |
| `create_time` | timestamp | 否 | CURRENT_TIMESTAMP | 创建时间 |

索引：主键；`uk_post_media_sort(post_id,sort)`；`uk_post_media_asset(media_asset_id)`。唯一媒体索引确保一个媒体资产最多绑定一次业务记录。

### 3.4 `post_like`

| 字段 | MySQL 类型 | Null | 默认 | 说明 |
|---|---|---|---|---|
| `id` | bigint unsigned | 否 | auto_increment | 主键 |
| `post_id` | bigint unsigned | 否 | 无 | 动态逻辑关联 |
| `user_id` | bigint unsigned | 否 | 无 | 点赞用户逻辑关联 |
| `create_time` | timestamp | 否 | CURRENT_TIMESTAMP | 点赞时间 |

索引：主键；`uk_post_like_post_user(post_id,user_id)`；`idx_post_like_user_time(user_id,create_time,id)`。

`post_like` 是点赞事实真源，`post.liked_count` 是可重算冗余值，Redis 只承担加速和排序。

## 4. 事务、一致性与权限

### 4.1 创建动态与绑定媒体

创建动态在一个数据库事务中完成：

1. 通过 `CurrentUserProvider` 获取用户 ID。
2. 校验分区、商户和城市来源，不接收客户端城市编码。
3. 对去重后的媒体 ID 按 ID 升序加锁，防止并发交叉绑定。
4. 校验媒体全部存在、属于当前用户、状态为 `TEMPORARY`、未过期且绑定字段为空。
5. 写入 `post`，再按请求顺序写入 `post_media`。
6. 条件更新媒体为 `BOUND`，设置 `bound_type=POST`、`bound_id=postId` 并清空 `expire_time`；实际更新数必须等于媒体数。
7. 事务提交后投递关注流；Redis 失败不回滚已发布动态，交由补偿任务重建。

任一步骤失败时，动态、关系和媒体状态全部回滚。物理图片在上传阶段已经落盘，事务回滚后仍保持临时资产，可由用户重试发布或等待过期清理。

### 4.2 更新与删除

- 仅动态作者可更新或删除；未找到和无权限必须区分 404 与 403。
- 更新保留的媒体可继续使用；新媒体必须满足临时媒体绑定条件。
- 从动态移除的媒体在事务内标记为 `DELETED` 并删除关系，事务提交后尽力删除物理文件；删除失败由媒体清理任务补偿。
- 动态删除使用 `status=DELETED` 逻辑删除，不立即删除点赞事实、评论事实或媒体审计关系。
- 已隐藏或已删除动态不能新增点赞；作者仍不能通过普通读取接口看到已删除动态。

### 4.3 点赞幂等

- 点赞：先插入 `post_like`，只有实际新增关系时才将 `liked_count + 1`。
- 取消点赞：先删除当前用户关系，只有实际删除关系时才将 `liked_count - 1`，并使用非负保护。
- 唯一索引处理同一用户并发重复点赞；重复 PUT、DELETE 均返回 204。
- 数据库事务提交后同步 `post:liked:{postId}`；Redis 写失败进入补偿，不修改数据库成功结果。
- 点赞用户列表以 `post_like.create_time,id` 稳定排序，不能只依赖 Redis。

## 5. 旧 Blog 开发数据转换

### 5.1 字段映射

| Blog 来源 | Post 目标 | 规则 |
|---|---|---|
| `id` | `id` | 保留原 ID |
| `user_id` | `user_id` | 原值 |
| `shop_id=0` | `shop_visit=0,shop_id=null` | 普通动态 |
| `shop_id>0` | `shop_visit=1,shop_id=原值` | 探店动态 |
| 分区 | `section_id` | 按稳定 `code` 查询，不硬编码分区 ID |
| 城市 | `city_code` | 优先商户城市，其次用户资料城市，最后杭州 `330100` |
| `title` | `title` | 空白转 null；转换前校验不超过 120 字，不静默截断 |
| `content` | `content` | 原值；转换前校验不超过 5000 字 |
| `liked` | `liked_count` | null 转 0 |
| `comments` | `comment_count` | null 转 0 |
| 无 | `status` | 当前开发样例统一为 0 `NORMAL` |
| `create_time/update_time` | 同名字段 | 原值 |

当前 `seed-dev.sql` 的固定映射：

- Blog 4、5 → `FOOD_DISCOVERY`。
- Blog 6、7 → `WEEKEND_ESCAPE`。
- 若后续增加 `shop_id=0` 的开发样例 → `ROAM_DAILY`。
- 当前转换 SQL 对其他探店样例回退到 `FOOD_DISCOVERY`；新增样例时必须先明确分区再提交。

### 5.2 明确延后项

- 旧 `images` 只有逗号分隔路径，没有可靠 MIME、文件大小、宽高和所有者校验结果。本阶段不向 `media_asset` 或 `post_media` 写入伪造数据。
- 后续在允许重建并具备旧文件目录的隔离环境执行受控旧文件探测：成功读取并校验的图片才生成已绑定媒体；缺失或损坏文件进入核对清单，不阻断其他动态。
- 旧点赞用户关系仅存在 Redis 时，本阶段不伪造 `post_like`。在旧 Blog 接口停写前读取旧集合，按保留的 Post ID 写入事实表并核对计数。
- `blog`、`blog_comments` 继续保留，旧接口仍按当前状态运行；阶段 11 完成全栈切换与核对后再退役。

### 5.3 重建后核对规则

仅在用户明确允许重建隔离开发库后执行以下核对；本阶段未执行：

- `post` 行数等于 `blog` 行数，且 ID 集合完全一致。
- 每条 Post 的作者、商户、标题、正文、计数和审计时间与 Blog 映射一致。
- 所有 Post 的 `section_id` 均能解析到启用分区，且没有硬编码分区 ID。
- 普通动态全部属于 `ROAM_DAILY` 且 `shop_id is null`。
- 探店动态全部具有有效商户，城市与商户城市一致，分区允许探店。
- 当前阶段允许 `post_media` 与 `post_like` 为空；必须在阶段 4 完成真实转换后再核对媒体和点赞关系。

## 6. 验收与验证记录

阶段 3 完成条件：

- 三张表字段、默认值、索引名和逻辑关系与本文一致。
- `seed-dev.sql` 使用分区编码完成当前四条 Blog 的 Post 映射。
- 未写入伪造媒体元数据或点赞用户关系。
- `DATABASE_SCHEMA.md` 与 `BACKEND_DEVELOPMENT.md` 同步为 17 张表的快照事实。
- 不存在新增的数据库版本目录或编号 SQL。
- 未执行 `schema-init.sql`，运行数据库初始化状态保持“未实现”。

验证记录：

- 静态扫描：`schema-init.sql` 共 17 个 `CREATE TABLE`，其中 3 个为 Post 相关表。
- 静态扫描：`seed-dev.sql` 保留 4 条 Blog 样例并通过 1 条 `INSERT ... SELECT` 统一转换，分区关联使用 `content_section.code`。
- 静态扫描：资源目录不存在数据库版本目录、编号 SQL 或回退脚本。
- Reactor `mvn test`：34 项，0 失败，10 项外部环境测试默认跳过。
- Reactor `mvn -DskipTests compile`：通过。
- 未执行 `schema-init.sql`；SQL 在隔离 MySQL 8.x 的真实初始化验证延后到用户明确允许重建开发数据库时进行。
