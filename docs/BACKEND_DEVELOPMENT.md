# Roamly 后端开发与全栈设计契约

```yaml
version: 3
updatedAt: 2026-09-02
scope: Roamly 分区社区、Threads 式评论、本地生活点评与团购交易
reviewStatus: draft
implementationStatus: 未实现
```

## 1. 文档定位与状态规则

本文档同时维护当前系统事实、目标设计和实施阶段，是后端架构、HTTP 契约、目标数据模型、迁移边界及验收状态的总入口。

- 当前数据库事实以 [DATABASE_SCHEMA.md](./DATABASE_SCHEMA.md) 为准。
- HTTP 接口实现完成后以实际 OpenAPI `/v3/api-docs` 为正式契约真源。
- 小程序页面、组件、状态和交互细节以同级项目的 [小程序开发与产品契约](../../Roamly-miniapp/docs/MINIAPP_DEVELOPMENT.md) 为准。
- 目标接口和目标表仅完成设计时统一标记“未实现”，不能视为已经存在。
- 状态只使用“未确认、未实现、开发中、已实现、已废弃”。
- 每个阶段开始前必须冻结该阶段的 OpenAPI、字段、索引、事务、迁移和客户端状态机；源码、SQL、测试和文档全部完成后才能标记“已实现”。

阶段设计文档：

- [阶段 1：城市、官方分区、分区关注与临时媒体契约](./stages/STAGE_01_CITY_SECTION_MEDIA.md)
- [阶段 3：统一动态、动态媒体、动态点赞与 Blog 转换契约](./stages/STAGE_03_POST_SCHEMA.md)

## 2. 当前系统基线

### 2.1 工程与技术栈

- 聚合模块：`ray-common`、`ray-pojo`、`ray-server`。
- 依赖方向：`ray-server -> ray-common + ray-pojo`，公共模块不得反向依赖服务端。
- 技术栈：Java 21、Spring Boot 3.5.11、MyBatis-Plus 3.5.12、Sa-Token 1.46.0、Knife4j 5.2.1、Springdoc 2.8.9、RedisTemplate、Redisson、MySQL。
- 服务端继续保持 `controller / service / service.impl / mapper / config / interceptor / handler / utils` 技术分层，本轮不按业务域拆分 `ray-server`。
- Controller 只处理 HTTP 契约、输入校验、ID 转换和状态码；查询、事务、缓存、文件绑定与业务异常全部下沉 Service。
- Entity 为适配 MyBatis 的可变类；请求 DTO 和简单 VO 优先使用 `record`。
- 所有业务 ID 在 Java 内部使用 `Long`，JSON、OpenAPI 和小程序使用字符串。

### 2.2 当前已实现能力

| 能力 | 当前路径 | 状态 | 目标处置 |
|---|---|---|---|
| 短信验证码、登录、注销 | `/v1/auth/**` | 已实现 | 保留 |
| 用户、资料、签到 | `/v1/users/**` | 已实现 | 保留并扩展城市 |
| 用户关注与共同关注 | `/v1/users/**/following**` | 已实现 | 保留并补唯一约束 |
| 商户与商户分类 | `/v1/shops/**`、`/v1/shop-types` | 已实现 | 保留并扩展聚合能力 |
| Blog、点赞、关注流 | `/v1/blogs/**`、`/v1/feeds/following` | 已实现 | 迁移至 Post 后废弃 |
| 评论 | 无 HTTP 路由 | 未实现 | 由 PostComment 替代 |
| 优惠券与秒杀订单 | `/v1/vouchers`、`/v1/seckill-vouchers/**` | 已实现 | 迁移至团购商品和订单 |
| Blog 图片 | `/v1/blog-images`、`/blogs/**` | 已实现 | 迁移至通用媒体资产 |

### 2.3 当前数据库与迁移风险

- 当前数据库快照有 17 张业务表，结构来源为 `ray-server/src/main/resources/schema-init.sql`。
- 数据库不使用 Flyway、Liquibase、编号迁移或迁移历史，结构直接维护在 `schema-init.sql`，开发数据直接维护在 `seed-dev.sql`。
- `schema-init.sql` 含 `DROP TABLE`，只允许用于全新开发数据库，禁止在现有数据库或生产数据库执行。
- [DATABASE_SCHEMA.md](./DATABASE_SCHEMA.md) 记录当前 SQL 快照事实；运行中的数据库是否已按快照重建需要单独确认。

## 3. 目标产品形态与领域边界

Roamly 由三个相互关联但职责独立的领域组成：

```text
分区社区
  └─ 官方分区 → 动态 → 媒体 / 点赞 / Threads 式评论
                     └─ 可选关联商户

本地生活
  └─ 城市 → 商户 → 独立点评 / 探店动态 / 团购商品

团购交易
  └─ 团购商品 → 订单 → 支付结果边界 → 用户券包
```

产品交互借鉴小黑盒的分区信息架构和 Threads 的连续内容、追加回复体验，但不得复制其品牌、素材、专有视觉和算法实现。

### 3.1 统一发布模型

所有社区内容统一使用 `ContentPost`，不再拆分日常和探店两个 Entity，也不增加内容类型枚举。

```text
shopVisit = false
→ 服务端绑定 code=ROAM_DAILY 的“漫游日常”分区
→ sectionId 和 shopId 不允许提交

shopVisit = true
→ sectionId 必填
→ shopId 必填
→ 分区必须启用且 allowShopVisit=true
→ 动态 cityCode 由商户决定
```

- 一个动态只能属于一个官方分区。
- “漫游日常”通过固定编码查询，不硬编码数据库 ID。
- 标题可选，正文必填，最多绑定 9 个当前用户拥有的临时媒体资产。
- 首页卡片只显示分区和热门评论摘要；完整商户连接仅在探店详情中展示。

### 3.2 Threads 式评论

- 根评论是讨论起点，回复以追加方式展示。
- 数据保留根评论、直接父评论和被回复用户三种关系。
- 展示层所有回复统一一级缩进，不随回复深度无限向右移动。
- 第一阶段评论只支持文字，不支持评论图片。
- 首页最多展示一条有效热门根评论，接口必须批量组装，禁止客户端逐动态请求评论。
- 有回复的根评论删除后保留占位；无回复评论可以逻辑隐藏。

### 3.3 商户点评与探店内容

- 探店动态是社区内容，不直接计入商户评分。
- `ShopReview` 独立保存 1～5 分、正文和图片。
- 同一用户对同一商户只能保留一条有效点评。
- 所有登录用户可以点评；“已消费”由服务端根据已核销用户券计算，客户端不得提交或修改。
- 商户平均分仅基于正常状态的点评聚合。

### 3.4 团购交易边界

- 第一阶段支持商品浏览、下单、取消未支付订单、订单查询和用户券包。
- 真实微信支付、退款、商户管理、扫码核销只保留状态和内部事件边界，不提供伪实现。
- 普通用户无团购商品管理接口；开发商品通过受控 SQL 初始化。

## 4. 模块与代码承载

| 模块 | 目标职责 | 约束 |
|---|---|---|
| `ray-common` | `Result`、分页结果、业务异常、错误码、Redis Key、通用枚举 | 不依赖 pojo/server |
| `ray-pojo` | `entity / dto / vo` | 不依赖 server，不直接暴露 Entity |
| `ray-server` | Controller、Service、ServiceImpl、Mapper、配置、鉴权、缓存、上传和异步消费 | 保持技术分层 |

命名约定：

- Entity 使用单数业务名：`ContentSection`、`ContentPost`、`PostComment`。
- 请求使用“业务动作 + Request”，例如 `PostCreateRequest`。
- 展示模型统一使用 `VO`。
- 项目响应模型继续使用 `Result` 后缀，避免与依赖的 `ApiResponse` 重名。
- Controller 使用正常 import 的 Swagger `ApiResponse`、`ApiResponses`，不得写完整限定注解名。

## 5. HTTP 与鉴权总契约

### 5.1 路径边界

- Controller、Sa-Token 拦截器和 OpenAPI `paths` 使用 `/v1/**`。
- 生产 Nginx 对外暴露 `/api/v1/**`，转发时剥离 `/api`。
- OpenAPI `servers.url=/api`。
- 小程序业务 API 只写 `/v1/**`，体验版和正式版 `apiBaseUrl` 包含 `/api`。
- 静态资源通过 `assetBaseUrl` 解析，`assetBaseUrl` 不包含 `/api`；当前 `/blogs/**` 保持可访问，媒体资产迁移后再决定对象存储域名。

### 5.2 统一响应

```text
Result<T>             { code, message, data }
ErrorResult           { code, message, fieldErrors }
PageResult<T>         { items, page, size, total }
CursorPageResult<T>   { items, nextCursor, nextOffset, hasMore }
```

- 页码分页：`page >= 1`，`1 <= size <= 100`，默认 `page=1,size=10`。
- 信息流默认 `size=10`，最大 20；评论最大 50。
- 创建资源返回 201。
- 查询和普通更新返回 200。
- 注销、删除、取消、关注切换和点赞切换等空响应返回 204。
- 请求参数和跨字段规则不满足返回 400；未登录 401；无操作权限 403；资源不存在 404；重复资源或状态冲突 409；文件超限 413。

### 5.3 Sa-Token

- 请求头：`Authorization: Bearer <opaque-token>`，OpenAPI 使用 `type=http`、`scheme=bearer`、`bearerFormat=opaque`。
- Token UUID，总有效期 30 天，连续 7 天无访问失效，自动续签。
- 允许同账号多设备独立 Token，注销只影响当前 Token。
- Token 只从 Header 读取；不从 Cookie 和请求体读取。
- Service 通过 `CurrentUserProvider` 获取当前用户 ID。
- 推荐流、分区、动态详情、评论读取、商户和团购商品允许匿名访问；返回个性化状态时 Token 可选。
- 关注流、发布、点赞、评论写入、点评、订单和券包必须登录。

## 6. 完整 API 目标草案

本节接口均为目标契约，实际进度以“状态”列为准。每个接口实现时必须补齐唯一 `operationId`、真实 `@ApiResponses`、参数说明、安全声明和 Schema。

### 6.1 认证、用户与用户关注

| 方法 | 路径 | 鉴权 | 请求 | 响应 | 状态 |
|---|---|---|---|---|---|
| POST | `/v1/auth/sms-codes` | 公开 | `SmsCodeRequest` | 204 | 已实现 |
| POST | `/v1/auth/sessions` | 公开 | `LoginRequest` | `Result<AuthTokenVO>` | 已实现 |
| DELETE | `/v1/auth/session` | 登录 | 无 | 204 | 已实现 |
| GET | `/v1/users/me` | 登录 | 无 | `Result<UserVO>` | 已实现 |
| GET | `/v1/users/{userId}` | 公开 | 无 | `Result<UserVO>` | 已实现 |
| GET | `/v1/users/{userId}/profile` | 公开 | 无 | `Result<UserInfoVO>` | 已实现 |
| PUT | `/v1/users/me/check-ins/today` | 登录 | 无 | 204 | 已实现 |
| GET | `/v1/users/me/check-ins/streak` | 登录 | 无 | `Result<SignStreakVO>` | 已实现 |
| PUT | `/v1/users/me/following/{userId}` | 登录 | 无 | 204 | 已实现 |
| DELETE | `/v1/users/me/following/{userId}` | 登录 | 无 | 204 | 已实现 |
| GET | `/v1/users/me/following/{userId}` | 登录 | 无 | `Result<FollowStatusVO>` | 已实现 |
| GET | `/v1/users/{userId}/common-following` | 登录 | 无 | `Result<List<UserVO>>` | 已实现 |

### 6.2 城市、分区与信息流

| 方法 | 路径 | 鉴权 | 查询参数 | 响应 | 状态 |
|---|---|---|---|---|---|
| GET | `/v1/cities` | 公开 | 无 | `Result<List<CityVO>>` | 已实现 |
| GET | `/v1/sections` | 可选 | `followedOnly` | `Result<List<SectionVO>>` | 已实现 |
| GET | `/v1/sections/{sectionId}` | 可选 | 无 | `Result<SectionDetailVO>` | 已实现 |
| GET | `/v1/sections/{sectionId}/posts` | 可选 | `cityCode,sort,cursor,offset,size` | `Result<CursorPageResult<PostCardVO>>` | 未实现 |
| PUT | `/v1/users/me/section-follows/{sectionId}` | 登录 | 无 | 204 | 已实现 |
| DELETE | `/v1/users/me/section-follows/{sectionId}` | 登录 | 无 | 204 | 已实现 |
| GET | `/v1/feeds/recommended` | 可选 | `cityCode,cursor,offset,size` | `Result<CursorPageResult<PostCardVO>>` | 未实现 |
| GET | `/v1/feeds/following` | 登录 | `cursor,offset,size` | `Result<CursorPageResult<PostCardVO>>` | 未实现 |

规则：

- `cityCode` 在推荐流和“漫游日常”分区流中必填。
- 分区 `sort` 仅允许 `LATEST`、`HOT`。
- `followedOnly=true` 时必须登录；匿名请求只能获取全部启用分区。
- 推荐与热门排序游标由服务端生成，客户端不得解析游标业务含义。

### 6.3 动态

| 方法 | 路径 | 鉴权 | 请求/查询 | 响应 | 状态 |
|---|---|---|---|---|---|
| POST | `/v1/posts` | 登录 | `PostCreateRequest` | 201 `Result<IdVO>` | 未实现 |
| GET | `/v1/posts/{postId}` | 可选 | 无 | `Result<PostDetailVO>` | 未实现 |
| PUT | `/v1/posts/{postId}` | 作者 | `PostUpdateRequest` | `Result<PostDetailVO>` | 未实现 |
| DELETE | `/v1/posts/{postId}` | 作者 | 无 | 204 | 未实现 |
| GET | `/v1/users/me/posts` | 登录 | `page,size` | `Result<PageResult<PostCardVO>>` | 未实现 |
| GET | `/v1/users/{userId}/posts` | 公开 | `page,size` | `Result<PageResult<PostCardVO>>` | 未实现 |
| PUT | `/v1/posts/{postId}/like` | 登录 | 无 | 204 | 未实现 |
| DELETE | `/v1/posts/{postId}/like` | 登录 | 无 | 204 | 未实现 |
| GET | `/v1/posts/{postId}/likes` | 公开 | `page,size` | `Result<PageResult<UserVO>>` | 未实现 |

`PostCreateRequest`：

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `title` | string | 否 | 最多 120 字，空白转为 null |
| `content` | string | 是 | 去除首尾空白后 1～5000 字 |
| `mediaIds` | string[] | 否 | 最多 9 个，不重复 |
| `shopVisit` | boolean | 是 | 探店开关 |
| `sectionId` | string | 条件必填 | 仅 `shopVisit=true` 时允许且必填 |
| `shopId` | string | 条件必填 | 仅 `shopVisit=true` 时允许且必填 |

`PostUpdateRequest` 与创建请求字段一致，完整替换可编辑内容；修改时仍重新执行探店、分区、商户和媒体所有权校验。

`PostCardVO` 至少包含：

- `id`、`author`、`section`、`title`、`contentPreview`、`media`、`shopVisit`、`createdTime`。
- `likedCount`、`commentCount`、`likedByMe`、`followingAuthor`。
- 可空 `highlightComment`，包含评论 ID、作者摘要、内容摘要、点赞数和回复数。

`PostDetailVO` 在卡片字段基础上提供完整正文、完整媒体、可空 `shop`、`editable`、`deletable` 和默认评论排序。

### 6.4 评论

| 方法 | 路径 | 鉴权 | 请求/查询 | 响应 | 状态 |
|---|---|---|---|---|---|
| GET | `/v1/posts/{postId}/comments` | 可选 | `sort,cursor,offset,size` | `Result<CursorPageResult<CommentThreadVO>>` | 未实现 |
| POST | `/v1/posts/{postId}/comments` | 登录 | `CommentCreateRequest` | 201 `Result<CommentVO>` | 未实现 |
| GET | `/v1/comments/{commentId}/replies` | 可选 | `cursor,offset,size` | `Result<CursorPageResult<CommentVO>>` | 未实现 |
| POST | `/v1/comments/{commentId}/replies` | 登录 | `CommentCreateRequest` | 201 `Result<CommentVO>` | 未实现 |
| DELETE | `/v1/comments/{commentId}` | 作者 | 无 | 204 | 未实现 |
| PUT | `/v1/comments/{commentId}/like` | 登录 | 无 | 204 | 未实现 |
| DELETE | `/v1/comments/{commentId}/like` | 登录 | 无 | 204 | 未实现 |

- `CommentCreateRequest.content` 去除首尾空白后 1～1000 字。
- 根评论列表支持 `HOT`、`LATEST`；回复固定按创建时间升序追加。
- `CommentThreadVO` 包含根评论、最多两条预览回复、总回复数、是否还有回复和下一页游标。
- 回复接口的 `{commentId}` 是直接回复目标；服务端据此派生 `rootId`、`parentId` 和 `replyToUserId`。
- 首页热门评论只从正常可见的根评论中选择。

### 6.5 媒体

| 方法 | 路径 | 鉴权 | 请求 | 响应 | 状态 |
|---|---|---|---|---|---|
| POST | `/v1/media/images` | 登录 | multipart `file` | 201 `Result<MediaAssetVO>` | 已实现 |
| DELETE | `/v1/media/images/{mediaId}` | 所有者 | 无 | 204 | 已实现 |

- 单张最大 10MB，只允许 JPEG、PNG、WebP，并同时校验扩展名、Content-Type 和文件签名。
- 上传成功生成 `TEMPORARY` 媒体，返回字符串 ID、相对资源地址、宽高、大小和 MIME 类型。
- 动态或点评创建事务只能绑定当前用户、临时状态且尚未占用的媒体。
- 已绑定媒体不能通过删除临时媒体接口删除。

### 6.6 商户与点评

| 方法 | 路径 | 鉴权 | 请求/查询 | 响应 | 状态 |
|---|---|---|---|---|---|
| GET | `/v1/shop-types` | 公开 | 无 | `Result<List<ShopTypeVO>>` | 已实现 |
| GET | `/v1/shops` | 公开 | 筛选与分页 | `Result<PageResult<ShopCardVO>>` | 目标扩展 |
| GET | `/v1/shops/{shopId}` | 可选 | 经纬度可选 | `Result<ShopDetailVO>` | 目标扩展 |
| GET | `/v1/shops/{shopId}/posts` | 可选 | `cursor,offset,size` | `Result<CursorPageResult<PostCardVO>>` | 未实现 |
| GET | `/v1/shops/{shopId}/reviews` | 可选 | `page,size,sort` | `Result<PageResult<ShopReviewVO>>` | 未实现 |
| POST | `/v1/shops/{shopId}/reviews` | 登录 | `ShopReviewCreateRequest` | 201 `Result<ShopReviewVO>` | 未实现 |
| PUT | `/v1/shops/{shopId}/reviews/me` | 登录 | `ShopReviewUpdateRequest` | `Result<ShopReviewVO>` | 未实现 |
| DELETE | `/v1/shops/{shopId}/reviews/me` | 登录 | 无 | 204 | 未实现 |

商户列表参数：

- `cityCode` 必填。
- `typeId`、`keyword`、`longitude`、`latitude` 可选。
- `sort` 允许 `DISTANCE`、`SCORE`、`POPULAR`。
- 只有经纬度同时合法时才计算距离和允许 `DISTANCE` 排序。

点评请求：

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `score` | integer | 是 | 1～5 |
| `content` | string | 是 | 去除首尾空白后 1～2000 字 |
| `mediaIds` | string[] | 否 | 最多 9 个，不重复 |

- 重复创建点评返回 409，客户端应跳转编辑原点评。
- 已消费标识由服务端寻找当前用户、当前商户的已核销用户券生成。

### 6.7 团购、订单和券包

| 方法 | 路径 | 鉴权 | 请求/查询 | 响应 | 状态 |
|---|---|---|---|---|---|
| GET | `/v1/shops/{shopId}/voucher-products` | 公开 | `status` | `Result<List<VoucherProductVO>>` | 未实现 |
| GET | `/v1/voucher-products/{productId}` | 公开 | 无 | `Result<VoucherProductDetailVO>` | 未实现 |
| POST | `/v1/voucher-products/{productId}/orders` | 登录 | `VoucherOrderCreateRequest` | 201 `Result<VoucherOrderVO>` | 未实现 |
| GET | `/v1/users/me/orders` | 登录 | `status,page,size` | `Result<PageResult<VoucherOrderVO>>` | 未实现 |
| GET | `/v1/users/me/orders/{orderId}` | 登录 | 无 | `Result<VoucherOrderDetailVO>` | 未实现 |
| DELETE | `/v1/users/me/orders/{orderId}` | 登录 | 无 | 204 | 未实现 |
| GET | `/v1/users/me/vouchers` | 登录 | `status,page,size` | `Result<PageResult<UserVoucherVO>>` | 未实现 |
| GET | `/v1/users/me/vouchers/{userVoucherId}` | 登录 | 无 | `Result<UserVoucherDetailVO>` | 未实现 |

- `VoucherOrderCreateRequest.quantity` 第一阶段固定为 1，字段保留并校验为 1。
- 创建订单时服务端重新读取价格、销售期、库存、限购和商户状态，不信任客户端金额。
- 删除订单接口语义为取消当前用户的未支付订单；其他状态返回 409。
- 支付成功内部事件按订单 ID 幂等发券，不在第一阶段暴露模拟支付 HTTP 接口。

### 6.8 目标业务错误码

| 错误码 | HTTP | 场景 |
|---|---:|---|
| `INVALID_ARGUMENT` | 400 | 通用参数或跨字段校验失败 |
| `UNAUTHORIZED` | 401 | 缺少、无效或失效 Token |
| `FORBIDDEN` | 403 | 非作者删除/修改，媒体非所有者 |
| `RESOURCE_NOT_FOUND` | 404 | 通用资源不存在 |
| `SECTION_NOT_FOUND` | 404 | 分区不存在或停用 |
| `SECTION_NOT_ALLOWED_FOR_SHOP_VISIT` | 400 | 探店选择了不允许探店的分区 |
| `SHOP_NOT_FOUND` | 404 | 商户不存在或不可用 |
| `POST_NOT_FOUND` | 404 | 动态不存在或不可见 |
| `COMMENT_NOT_FOUND` | 404 | 评论不存在或不可见 |
| `REVIEW_ALREADY_EXISTS` | 409 | 用户已点评该商户 |
| `MEDIA_NOT_FOUND` | 404 | 媒体不存在 |
| `MEDIA_NOT_OWNED` | 403 | 媒体不属于当前用户 |
| `MEDIA_ALREADY_BOUND` | 409 | 媒体已经绑定业务 |
| `MEDIA_TOO_LARGE` | 413 | 图片超过限制 |
| `VOUCHER_PRODUCT_NOT_AVAILABLE` | 409 | 商品未开售、下架或过期 |
| `VOUCHER_OUT_OF_STOCK` | 409 | 库存不足 |
| `VOUCHER_PURCHASE_LIMIT_REACHED` | 409 | 超过限购 |
| `ORDER_STATUS_CONFLICT` | 409 | 当前订单状态不允许操作 |

## 7. 完整目标数据库草案

本节同时记录已经写入 SQL 快照的结构和后续目标设计，实际状态以表清单为准。已写入 `schema-init.sql` 只表示可重建开发库的结构快照已实现，不代表运行数据库已经初始化，也不代表对应 HTTP 业务已经实现。

### 7.1 设计原则

- MySQL、InnoDB、utf8mb4。
- 所有业务表使用 `create_time`、`update_time`；逻辑删除或审核型数据增加 `status`。
- 金额统一使用分为单位的整数，评分避免浮点存储。
- 继续使用逻辑外键，不新增数据库物理外键；所有逻辑关联必须有必要索引并由 Service 校验。
- 点赞、关注、点评、券码和发券幂等必须由数据库唯一约束兜底。
- 计数字段是可校正的冗余值，事实关系表是最终依据。

### 7.2 新增表清单

| 表 | 用途 | 隔离范围 | 状态 |
|---|---|---|---|
| `tb_city` | 可用城市字典 | 平台级 | 已实现 |
| `tb_content_section` | 官方内容分区 | 平台级 | 已实现 |
| `tb_section_follow` | 用户关注分区 | 用户级 | 已实现 |
| `tb_media_asset` | 临时和已绑定媒体 | 用户级 | 已实现 |
| `tb_post` | 统一社区动态 | 城市/用户级 | 已实现 |
| `tb_post_media` | 动态媒体及顺序 | 动态级 | 已实现 |
| `tb_post_like` | 动态点赞事实 | 用户/动态级 | 已实现 |
| `tb_post_comment` | 根评论和追加回复 | 动态级 | 未实现 |
| `tb_post_comment_like` | 评论点赞事实 | 用户/评论级 | 未实现 |
| `tb_shop_review` | 商户点评 | 商户/用户级 | 未实现 |
| `tb_shop_review_media` | 点评媒体及顺序 | 点评级 | 未实现 |
| `tb_voucher_product` | 团购商品 | 商户级 | 未实现 |
| `tb_user_voucher` | 用户券实例 | 用户级 | 未实现 |

### 7.3 字段与索引

#### `tb_city`

| 字段 | 建议类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | bigint unsigned | 否 | 主键 |
| `code` | varchar(16) | 否 | 稳定城市编码 |
| `name` | varchar(64) | 否 | 城市名称 |
| `status` | tinyint unsigned | 否 | 0 停用，1 启用 |
| `sort` | int unsigned | 否 | 展示顺序 |
| `create_time`、`update_time` | timestamp | 否 | 审计时间 |

索引：唯一索引 `uk_city_code(code)`；列表索引 `idx_city_status_sort(status,sort,id)`。

#### `tb_content_section`

| 字段 | 建议类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | bigint unsigned | 否 | 主键 |
| `code` | varchar(32) | 否 | 稳定编码 |
| `name` | varchar(32) | 否 | 分区名称 |
| `description` | varchar(255) | 是 | 分区说明 |
| `icon` | varchar(255) | 是 | 图标相对路径 |
| `cover` | varchar(255) | 是 | 封面相对路径 |
| `allow_shop_visit` | tinyint unsigned | 否 | 是否允许探店发布 |
| `status` | tinyint unsigned | 否 | 0 停用，1 启用 |
| `sort` | int unsigned | 否 | 展示顺序 |
| `create_time`、`update_time` | timestamp | 否 | 审计时间 |

索引：唯一索引 `uk_section_code(code)`；列表索引 `idx_section_status_sort(status,sort,id)`。初始化固定分区 `ROAM_DAILY`，并由应用保证其存在且不可删除。

#### `tb_section_follow`

字段：`id`、`user_id`、`section_id`、`create_time`。

索引：唯一索引 `uk_section_follow_user_section(user_id,section_id)`；反向查询索引 `idx_section_follow_section_time(section_id,create_time,id)`。

#### `tb_media_asset`

字段：`id`、`owner_user_id`、`storage_path`、`mime_type`、`file_size`、`width`、`height`、`status`、`bound_type`、`bound_id`、`expire_time`、`create_time`、`update_time`。

- 状态：`TEMPORARY / BOUND / DELETED`，数据库保存对应数字枚举。
- `bound_type` 第一阶段允许 `POST`、`SHOP_REVIEW`。
- 索引：唯一索引 `uk_media_storage_path(storage_path)`；清理索引 `idx_media_status_expire(status,expire_time,id)`；用户查询索引 `idx_media_owner_status(owner_user_id,status,id)`。

#### `tb_post`

字段：`id`、`user_id`、`section_id`、`shop_visit`、可空 `shop_id`、`city_code`、可空 `title`、`content`、`liked_count`、`comment_count`、`status`、`create_time`、`update_time`。

- `shop_visit=0` 时 `shop_id` 必须为空，分区由服务端绑定为 `ROAM_DAILY`。
- `shop_visit=1` 时 `shop_id` 必填，分区必须允许探店。
- 状态：`NORMAL / HIDDEN / DELETED`。
- 索引：
  - `idx_post_section_status_time(section_id,status,create_time,id)`
  - `idx_post_user_status_time(user_id,status,create_time,id)`
  - `idx_post_shop_status_time(shop_id,status,create_time,id)`
  - `idx_post_city_status_time(city_code,status,create_time,id)`

#### `tb_post_media`

字段：`id`、`post_id`、`media_asset_id`、`sort`、`create_time`。

索引：唯一索引 `uk_post_media_sort(post_id,sort)`；唯一索引 `uk_post_media_asset(media_asset_id)`。

#### `tb_post_like`

字段：`id`、`post_id`、`user_id`、`create_time`。

索引：唯一索引 `uk_post_like_post_user(post_id,user_id)`；用户记录索引 `idx_post_like_user_time(user_id,create_time,id)`。

#### `tb_post_comment`

字段：`id`、`post_id`、`user_id`、可空 `root_id`、可空 `parent_id`、可空 `reply_to_user_id`、`content`、`liked_count`、`reply_count`、`status`、`create_time`、`update_time`。

- 根评论：`root_id`、`parent_id`、`reply_to_user_id` 均为空。
- 回复：`root_id` 指向根评论，`parent_id` 指向直接回复目标，`reply_to_user_id` 保存目标作者。
- 状态：`NORMAL / DELETED / HIDDEN`；`DELETED` 可展示占位，`HIDDEN` 不对普通用户展示。
- 索引：
  - `idx_comment_post_status_time(post_id,status,create_time,id)`
  - `idx_comment_post_hot(post_id,status,liked_count,create_time,id)`
  - `idx_comment_root_status_time(root_id,status,create_time,id)`

#### `tb_post_comment_like`

字段：`id`、`comment_id`、`user_id`、`create_time`。

索引：唯一索引 `uk_comment_like_comment_user(comment_id,user_id)`；用户记录索引 `idx_comment_like_user_time(user_id,create_time,id)`。

#### `tb_shop_review`

字段：`id`、`shop_id`、`user_id`、可空 `verified_user_voucher_id`、`score`、`content`、`status`、`create_time`、`update_time`。

- `score` 使用 1～5 整数；商户聚合评分继续以放大 10 倍的整数保存。
- 唯一索引 `uk_review_shop_user(shop_id,user_id)`。
- 列表索引 `idx_review_shop_status_time(shop_id,status,create_time,id)`。
- 聚合索引 `idx_review_shop_status_score(shop_id,status,score)`。

#### `tb_shop_review_media`

字段：`id`、`review_id`、`media_asset_id`、`sort`、`create_time`。

索引：唯一索引 `uk_review_media_sort(review_id,sort)`；唯一索引 `uk_review_media_asset(media_asset_id)`。

#### `tb_voucher_product`

字段：`id`、`shop_id`、`title`、可空 `sub_title`、可空 `cover`、`rules`、`pay_price`、`original_price`、`deduction_value`、`sale_type`、`total_stock`、`available_stock`、`sold_count`、`purchase_limit`、可空 `sale_begin_time`、可空 `sale_end_time`、`validity_type`、可空 `valid_begin_time`、可空 `valid_end_time`、可空 `valid_days`、`status`、`version`、`create_time`、`update_time`。

- 金额单位均为分。
- `sale_type`：`NORMAL / SECKILL`。
- `validity_type`：`FIXED_RANGE / DAYS_AFTER_PURCHASE`。
- `status`：`DRAFT / ON_SALE / SOLD_OUT / OFF_SALE`。
- 索引：`idx_voucher_product_shop_status(shop_id,status,id)`、`idx_voucher_product_sale(status,sale_begin_time,sale_end_time,id)`。

#### `tb_voucher_order` 目标调整

字段：`id`、`user_id`、`product_id`、`shop_id`、商品标题快照、单价快照、`quantity`、`total_amount`、`pay_amount`、`pay_type`、`status`、`create_time`、可空 `pay_time`、可空 `cancel_time`、可空 `refund_time`、`update_time`。

- 状态：`PENDING_PAYMENT / PAID / CANCELED / REFUNDING / REFUNDED`。
- 索引：`idx_order_user_status_time(user_id,status,create_time,id)`、`idx_order_product_user(product_id,user_id,id)`。
- 第一阶段一张订单只购买一种商品且数量固定为 1。

#### `tb_user_voucher`

字段：`id`、`user_id`、`order_id`、`product_id`、`shop_id`、`voucher_code`、`status`、`valid_begin_time`、`expire_time`、可空 `use_time`、可空 `refund_time`、`create_time`、`update_time`。

- 状态：`UNUSED / USED / EXPIRED / REFUNDED`。
- 唯一索引 `uk_user_voucher_code(voucher_code)`。
- 发券幂等唯一索引 `uk_user_voucher_order(order_id)`。
- 券包索引 `idx_user_voucher_user_status_expire(user_id,status,expire_time,id)`。

### 7.4 现有表调整

- `tb_shop` 增加 `city_code`、经营状态和 `idx_shop_city_type_status(city_code,type_id,status,id)`。
- `tb_user_info` 增加 `city_code`，原 `city` 在迁移期保留用于显示兼容。
- `tb_follow` 增加唯一索引 `uk_follow_user_target(user_id,follow_user_id)`。
- 旧 `tb_blog`、`tb_blog_comments`、`tb_voucher`、`tb_seckill_voucher` 在迁移完成并校验前保留只读，最后阶段再退役。

## 8. Redis、事务与一致性

### 8.1 目标 Redis Key

| Key | 类型 | 用途 | 数据真源 |
|---|---|---|---|
| `feed:following:{userId}` | ZSET | 关注流时间线 | `tb_post` + `tb_follow` |
| `post:liked:{postId}` | ZSET | 点赞状态和最近点赞用户 | `tb_post_like` |
| `post:hot:{cityCode}:{period}` | ZSET | 城市热门动态 | 动态、点赞、评论事实 |
| `section:hot:{sectionId}:{period}` | ZSET | 分区热门动态 | 动态、点赞、评论事实 |
| `post:highlight-comment:{postId}` | STRING | 首页热门评论 ID | `tb_post_comment` |
| `voucher:stock:{productId}` | STRING/HASH | 秒杀库存预扣 | `tb_voucher_product` |
| `voucher:ordered:{productId}` | SET | 秒杀重复下单判断 | `tb_voucher_order` |

Sa-Token 使用框架自身命名空间，不与业务 Redis Key 混用。

### 8.2 事务边界

- 发布动态：写动态、绑定媒体、写媒体关系在同一数据库事务中完成；提交后再投递关注流。
- 点赞：数据库唯一关系为事实，计数更新必须防止重复增减；Redis 写失败进入补偿，不回滚已经提交的事实关系。
- 评论：新增/删除评论与动态计数在同一事务中完成；提交后失效热门评论缓存。
- 点评：写点评、绑定媒体、更新商户聚合评分在一个事务或可靠重算流程内完成。
- 下单：普通商品使用数据库条件扣减；秒杀使用 Lua 原子校验和预扣，再通过 Redis Stream 异步落库。
- 发券：支付成功事件按订单 ID 幂等；订单状态改变和用户券写入必须保持一致。

### 8.3 热门评论

候选仅限正常可见的根评论。第一阶段评分方向为：

```text
点赞权重 + 回复权重 + 动态作者参与加权 - 发布时间衰减
```

具体权重在评论阶段开工前基于测试数据冻结。点赞、回复、删除、隐藏和作者回复均使缓存失效；缓存未命中时回源计算并设置短 TTL。

## 9. 数据库快照与数据转换

### 9.1 快照维护规则

- 不引入 Flyway、Liquibase、编号迁移脚本、迁移历史表或独立回退脚本。
- 所有表的最终结构直接修改 `schema-init.sql`，开发初始化数据直接修改 `seed-dev.sql`。
- 后续所有阶段都遵守该规则，不创建阶段迁移目录或版本化 SQL 文件。
- 每次结构变化同步更新 `DATABASE_SCHEMA.md`、Entity、Mapper、OpenAPI 和测试。
- `schema-init.sql` 是可重建环境的完整快照，会执行 `DROP TABLE`；运行前必须明确允许丢弃当前开发库数据。
- 需要保留的数据先导出，重建后再通过受控导入或 `seed-dev.sql` 的转换语句恢复；不在仓库维护多版本升级链。

### 9.2 Blog 数据转换

- `tb_blog.shop_id=0`：迁移为 `shop_visit=0`，绑定 `ROAM_DAILY`，城市取用户当前城市或首发默认城市。
- `tb_blog.shop_id>0`：迁移为 `shop_visit=1`，保留商户，城市取商户城市，并绑定预设历史探店分区。
- 保留原 Blog ID 作为 Post ID，降低评论、URL 和 Redis 关系迁移复杂度。
- 旧图片缺少可靠 MIME、文件大小和宽高，阶段 3 不伪造媒体资产；阶段 4 仅将可读取并通过校验的文件拆为媒体资产和 `tb_post_media` 顺序记录，失败项进入核对清单且不阻断其余动态。
- `tb_blog_comments` 映射为根评论、直接父评论和回复用户；无法解析的孤儿评论进入异常清单，不伪造父关系。
- 旧 Blog 点赞若只存在 Redis，按保留的 Post ID 转存数据库关系和新 Key；迁移前后核对点赞用户集合与计数。

### 9.3 优惠券数据转换

- `tb_voucher` 迁移为 `tb_voucher_product`，普通券和秒杀券映射为不同 `sale_type`。
- `tb_seckill_voucher` 的库存和销售时间合并进团购商品。
- 旧订单保留原 ID并补充商品、商户和金额快照。
- 历史已核销订单生成 `USED` 用户券；已支付未核销订单生成 `UNUSED` 用户券；其他状态不发券。
- 新接口切换后停止旧写入，不维护长期双写；开发库通过完整快照和种子数据重建。

## 10. 分阶段实施计划

| 阶段 | 工作内容 | 完成条件 | 当前状态 |
|---:|---|---|---|
| 0 | 完成后端和小程序总契约 | 两份契约、链接、状态和范围一致 | 已实现 |
| 1 | 冻结城市、分区、媒体字段并更新 SQL 快照 | OpenAPI、DDL 与种子数据已写入结构/数据真源 | 已实现 |
| 2 | 实现城市、分区、分区关注、临时媒体 | 后端测试、OpenAPI、文档通过 | 开发中 |
| 3 | 冻结统一动态、点赞和 Blog 数据转换 | 字段、索引及快照种子映射定稿 | 已实现 |
| 4 | 实现 Post、媒体绑定、点赞和数据转换 | 新接口可用，重建后的数据核对一致 | 未实现 |
| 5 | 改造小程序导航、首页卡片和发布器 | 五入口、分区标签和统一发布验收 | 未实现 |
| 6 | 冻结并实现推荐、关注、分区信息流 | 游标、去重、城市隔离、热门摘要通过 | 未实现 |
| 7 | 冻结评论模型、删除语义和排序 | OpenAPI、SQL、热门规则评审完成 | 未实现 |
| 8 | 实现评论后端与 Threads 式界面 | 评论、回复、定位、缓存和计数通过 | 未实现 |
| 9 | 冻结并实现商户点评 | 评分、媒体、唯一点评和消费标识通过 | 未实现 |
| 10 | 冻结并实现团购商品、订单和券包 | 库存、限购、订单、发券幂等通过 | 未实现 |
| 11 | 停用旧接口、切换 Redis、从快照移除旧表 | 全量核对和客户端切换完成 | 未实现 |
| 12 | 搜索、通知、举报、审核、支付与核销 | 另行设计和评审 | 未实现 |

每个阶段开工前必须补全：

- 最终 Request、VO、OpenAPI Schema、operationId 和错误码。
- 最终字段类型、长度、默认值、索引名、`schema-init.sql` 和 `seed-dev.sql`。
- 权限、事务、幂等、缓存失效和异步失败处理。
- 小程序页面状态机、加载恢复和交互验收。
- 开发数据转换、重建核对和运行数据库初始化确认。

## 11. 全栈验收矩阵

### 11.1 后端

- 普通动态只能自动进入“漫游日常”，探店缺少分区或商户返回 400。
- 媒体不能跨用户、重复绑定或绑定已删除资源。
- 推荐流匿名可访问，关注流匿名返回 401。
- 首页一次查询返回分区和热门评论摘要，不产生逐动态 HTTP 请求或明显 N+1 查询。
- 评论深层关系、追加顺序、删除占位、热门排序和缓存失效正确。
- 点评重复创建返回 409，消费标识不能由客户端伪造。
- 团购库存、销售期、限购、重复下单、取消和发券幂等正确。
- 历史 Blog、图片、评论、点赞、订单和时间迁移前后数量与关系一致。
- OpenAPI 全部 `$ref`、operationId、Bearer、安全覆盖和错误响应有效。

### 11.2 小程序

- 五入口导航、推荐/关注切换、分区标签和中央发布按钮正确。
- 推荐与关注分别保留列表、游标和滚动位置。
- 热门评论跳转后能定位对应评论串，回复无需刷新整页。
- 深层回复始终保持一级视觉缩进。
- 发布开关严格控制分区和商户，图片上传、重试、放弃和临时资源清理正确。
- 未登录可浏览，互动时登录，登录后恢复原页面和意图。
- 401 清理登录态但不误删发布草稿。
- 定位拒绝、超时、断网和服务端错误均有明确降级或重试入口。
- 所有业务 ID 始终按字符串处理。

### 11.3 文档与工程

- 后端和小程序契约版本、接口、模型和状态一致。
- 目标表落库前不改写当前数据库事实。
- 文档链接不存在已删除文件引用。
- 后端文档修改执行 `git diff --check`；业务阶段按风险执行 Maven 测试和真实 OpenAPI 验证。
- 小程序业务阶段执行 `npm run verify`，并完成 Android、iOS 真机验证。

## 12. 当前验证记录

| 日期 | 范围 | 结果 |
|---|---|---|
| 2026-09-02 | 三模块重构 | Reactor `mvn clean test` 通过：22 项、0 失败，其中 10 项外部环境测试默认跳过；编译通过 |
| 2026-09-02 | Sa-Token、OpenAPI 与最小启动 | test Profile、Redis database 15、`spring.sql.init.mode=never` 下运行时测试通过 |
| 2026-09-02 | API v1 与小程序迁移 | Bearer、字符串 ID、统一响应、Nginx `/api` 边界及小程序验证通过 |
| 2026-09-02 | 本次新产品设计 | 完成全栈契约基线；各目标能力按阶段状态推进 |
| 2026-09-02 | 阶段 1 城市、分区与媒体设计 | 字段级 API、4 张新表、2 张现有表调整和种子数据已写入 SQL 快照；未执行数据库初始化 |
| 2026-09-02 | 阶段 2 后端实现 | 城市、分区、分区关注与临时媒体源码和 OpenAPI 已实现；34 项默认测试及 5 项真实 OpenAPI/Sa-Token 测试通过。运行数据库未按新快照重建，阶段保持开发中 |
| 2026-09-02 | 阶段 3 动态数据契约 | 冻结统一动态、媒体关系、点赞事实和 Blog 转换规则；SQL 快照新增 3 张表并按分区编码转换 4 条开发 Blog；34 项默认测试及编译通过，未执行数据库初始化，Post HTTP 能力仍未实现 |

## 13. 当前风险与明确非目标

- 快照模式会重建并清空开发数据库，执行前必须确认数据可丢弃或已经导出；本项目不提供存量数据库自动升级能力。
- 当前本地文件上传不支持多实例共享，生产扩容前应迁移对象存储。
- Redis Stream 当前固定消费者名不适合多实例，团购阶段必须改为实例唯一 consumer name。
- 第一阶段不实现评论图片、用户自建公共分区、角色权限、商家后台、真实支付、退款和扫码核销。
- 本设计不承诺兼容旧 Blog API；正式切换时后端和小程序作为一次破坏性升级同步发布。
