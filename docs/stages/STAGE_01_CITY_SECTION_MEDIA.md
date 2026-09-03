# 阶段 1：城市、官方分区、分区关注与临时媒体契约

```yaml
stage: 1
updatedAt: 2026-09-02
status: 开发中
implementationStatus: 已实现
schemaSnapshotStatus: 已实现
runtimeDatabaseInitialized: 未实现
```

## 1. 阶段目标与边界

阶段 1 已冻结接口和 Schema，并直接更新开发数据库快照与种子数据；阶段 2 已完成后端源码、测试和 OpenAPI 实现。当前不改造小程序页面，也未执行数据库初始化。

目标能力：

- 提供启用城市列表，首发城市为杭州，`cityCode=330100`。
- 提供平台维护的官方内容分区。
- 登录用户可以幂等关注或取消关注分区。
- 图片上传后先成为有所有权和过期时间的临时媒体，后续由 Post 或 ShopReview 事务绑定。
- 为现有商户补充城市与经营状态，为用户资料补充当前城市编码。

不包含：动态、评论、点评、团购商品的业务实现。数据库不做版本管理，结构直接维护在 `schema-init.sql`，开发数据直接维护在 `seed-dev.sql`。

## 2. API 冻结

### 2.1 通用约定

- Controller 内部路径为 `/v1`，生产环境对外为 `/api/v1`。
- JSON 中所有业务 ID 为字符串。
- `GET` 成功返回 200；图片创建返回 201；关注、取消关注和删除临时媒体返回 204。
- 公开接口不读取登录态；可选鉴权接口在 Token 有效时补充用户状态，无效 Token 按统一 401 处理。
- `Content-Type`：JSON 接口为 `application/json`，图片上传为 `multipart/form-data`。

### 2.2 城市

#### `GET /v1/cities`

- `operationId`：`listCities`
- 鉴权：公开。
- 请求参数：无。
- 响应：`Result<List<CityVO>>`。
- 排序：`sort ASC, id ASC`。
- 只返回 `status=ENABLED` 的城市。

`CityVO`：

| 字段 | JSON 类型 | 必填 | 说明 |
|---|---|---|---|
| `code` | string | 是 | 稳定城市编码，例如 `330100` |
| `name` | string | 是 | 城市名称，例如“杭州” |

错误响应：仅统一 500；该接口没有业务 404。

### 2.3 官方分区

#### `GET /v1/sections`

- `operationId`：`listSections`
- 鉴权：可选；`followedOnly=true` 时必须登录。
- 查询参数：`followedOnly`，boolean，默认 `false`。
- 响应：`Result<List<SectionVO>>`。
- 排序：平台 `sort ASC, id ASC`；`followedOnly` 只过滤，不重新排序。
- 只返回 `status=ENABLED` 的分区。

`SectionVO`：

| 字段 | JSON 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string | 是 | 分区 ID |
| `code` | string | 是 | 稳定编码 |
| `name` | string | 是 | 展示名称 |
| `icon` | string | 否 | 相对资源路径 |
| `allowShopVisit` | boolean | 是 | 是否允许探店发布 |
| `followedByMe` | boolean | 是 | 匿名时固定为 `false` |

#### `GET /v1/sections/{sectionId}`

- `operationId`：`getSection`
- 鉴权：可选。
- 路径参数：正整数字符串 `sectionId`。
- 响应：`Result<SectionDetailVO>`。

`SectionDetailVO` 在 `SectionVO` 基础上增加：

| 字段 | JSON 类型 | 必填 | 说明 |
|---|---|---|---|
| `description` | string | 否 | 分区说明 |
| `cover` | string | 否 | 分区封面相对路径 |

#### `PUT /v1/users/me/section-follows/{sectionId}`

- `operationId`：`followSection`
- 鉴权：登录。
- 语义：幂等关注；已经关注仍返回 204，不重复写入。

#### `DELETE /v1/users/me/section-follows/{sectionId}`

- `operationId`：`unfollowSection`
- 鉴权：登录。
- 语义：幂等取消；未关注仍返回 204。

分区接口错误：

| HTTP | code | 场景 |
|---:|---|---|
| 400 | `INVALID_ID` | `sectionId` 不是正整数字符串 |
| 401 | `UNAUTHORIZED` | 私有操作未登录，或 `followedOnly=true` 未登录 |
| 404 | `SECTION_NOT_FOUND` | 分区不存在或已停用 |
| 500 | `INTERNAL_ERROR` | 未预期错误 |

### 2.4 临时媒体

#### `POST /v1/media/images`

- `operationId`：`uploadImage`
- 鉴权：登录。
- 请求：multipart 字段 `file`，只允许一个文件。
- 响应：201 `Result<MediaAssetVO>`。

`MediaAssetVO`：

| 字段 | JSON 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string | 是 | 媒体资产 ID |
| `path` | string | 是 | 相对资源路径，由客户端结合 `assetBaseUrl` 解析 |
| `mimeType` | string | 是 | 实际识别的 MIME 类型 |
| `size` | integer/int64 | 是 | 文件字节数 |
| `width` | integer | 是 | 图片像素宽度 |
| `height` | integer | 是 | 图片像素高度 |
| `expiresAt` | string/date-time | 是 | 未绑定临时媒体的过期时间 |

上传约束：

- 非空；单张不超过 10MB。
- 允许 JPEG、PNG、WebP。
- 扩展名、声明 Content-Type、文件签名和图片解码结果必须一致。
- 服务端生成文件名和相对路径，不信任原始文件名。
- 上传成功后写入 `TEMPORARY` 媒体记录，默认 24 小时过期。
- 文件落盘成功但数据库写入失败时删除落盘文件；数据库提交成功后不得因响应中断删除资产。

#### `DELETE /v1/media/images/{mediaId}`

- `operationId`：`deleteTemporaryImage`
- 鉴权：媒体所有者。
- 路径参数：正整数字符串 `mediaId`。
- 语义：只删除当前用户的 `TEMPORARY` 资产；已删除资产重复调用返回 204；已绑定资产返回 409。
- 成功：先将数据库状态更新为 `DELETED`，提交后尽力删除物理文件；文件删除失败进入清理补偿，不能恢复数据库为可用状态。

媒体错误：

| HTTP | code | 场景 |
|---:|---|---|
| 400 | `EMPTY_IMAGE` | 文件为空 |
| 400 | `INVALID_IMAGE_TYPE` | 扩展名或类型不受支持 |
| 400 | `INVALID_IMAGE_CONTENT` | 文件签名或解码失败 |
| 401 | `UNAUTHORIZED` | 未登录 |
| 403 | `MEDIA_NOT_OWNED` | 媒体不属于当前用户 |
| 404 | `MEDIA_NOT_FOUND` | 媒体不存在 |
| 409 | `MEDIA_ALREADY_BOUND` | 媒体已绑定业务 |
| 413 | `MEDIA_TOO_LARGE` | 超过 10MB |
| 500 | `IMAGE_STORE_FAILED` | 存储或数据库写入失败 |

## 3. 数据库冻结

### 3.1 枚举映射

| 领域 | 数字 | Java 枚举语义 |
|---|---:|---|
| 城市状态 | 0 | `DISABLED` |
| 城市状态 | 1 | `ENABLED` |
| 分区状态 | 0 | `DISABLED` |
| 分区状态 | 1 | `ENABLED` |
| 媒体状态 | 0 | `TEMPORARY` |
| 媒体状态 | 1 | `BOUND` |
| 媒体状态 | 2 | `DELETED` |
| 媒体绑定类型 | 1 | `POST` |
| 媒体绑定类型 | 2 | `SHOP_REVIEW` |
| 商户经营状态 | 0 | `DISABLED` |
| 商户经营状态 | 1 | `ENABLED` |

### 3.2 `city`

| 字段 | MySQL 类型 | Null | 默认 | 说明 |
|---|---|---|---|---|
| `id` | bigint unsigned | 否 | auto_increment | 主键 |
| `code` | varchar(16) | 否 | 无 | 稳定城市编码 |
| `name` | varchar(64) | 否 | 无 | 城市名称 |
| `status` | tinyint unsigned | 否 | 1 | 状态 |
| `sort` | int unsigned | 否 | 0 | 展示排序 |
| `create_time` | timestamp | 否 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | timestamp | 否 | CURRENT_TIMESTAMP/on update | 更新时间 |

索引：主键；`uk_city_code(code)`；`idx_city_status_sort(status,sort,id)`。

### 3.3 `content_section`

| 字段 | MySQL 类型 | Null | 默认 | 说明 |
|---|---|---|---|---|
| `id` | bigint unsigned | 否 | auto_increment | 主键 |
| `code` | varchar(32) | 否 | 无 | 稳定分区编码 |
| `name` | varchar(32) | 否 | 无 | 展示名称 |
| `description` | varchar(255) | 是 | null | 分区说明 |
| `icon` | varchar(255) | 是 | null | 图标相对路径 |
| `cover` | varchar(255) | 是 | null | 封面相对路径 |
| `allow_shop_visit` | tinyint unsigned | 否 | 0 | 是否允许探店 |
| `status` | tinyint unsigned | 否 | 1 | 状态 |
| `sort` | int unsigned | 否 | 0 | 展示排序 |
| `create_time` | timestamp | 否 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | timestamp | 否 | CURRENT_TIMESTAMP/on update | 更新时间 |

索引：主键；`uk_section_code(code)`；`idx_section_status_sort(status,sort,id)`。

### 3.4 `section_follow`

| 字段 | MySQL 类型 | Null | 默认 | 说明 |
|---|---|---|---|---|
| `id` | bigint unsigned | 否 | auto_increment | 主键 |
| `user_id` | bigint unsigned | 否 | 无 | 用户逻辑关联 |
| `section_id` | bigint unsigned | 否 | 无 | 分区逻辑关联 |
| `create_time` | timestamp | 否 | CURRENT_TIMESTAMP | 创建时间 |

索引：主键；`uk_section_follow_user_section(user_id,section_id)`；`idx_section_follow_section_time(section_id,create_time,id)`。

### 3.5 `media_asset`

| 字段 | MySQL 类型 | Null | 默认 | 说明 |
|---|---|---|---|---|
| `id` | bigint unsigned | 否 | auto_increment | 主键 |
| `owner_user_id` | bigint unsigned | 否 | 无 | 上传用户 |
| `storage_path` | varchar(512) | 否 | 无 | 唯一相对存储路径 |
| `mime_type` | varchar(64) | 否 | 无 | 实际 MIME |
| `file_size` | bigint unsigned | 否 | 无 | 字节数 |
| `width` | int unsigned | 否 | 无 | 像素宽度 |
| `height` | int unsigned | 否 | 无 | 像素高度 |
| `status` | tinyint unsigned | 否 | 0 | 媒体状态 |
| `bound_type` | tinyint unsigned | 是 | null | 绑定业务类型 |
| `bound_id` | bigint unsigned | 是 | null | 绑定业务 ID |
| `expire_time` | timestamp | 是 | null | 临时资产过期时间 |
| `create_time` | timestamp | 否 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | timestamp | 否 | CURRENT_TIMESTAMP/on update | 更新时间 |

索引：主键；`uk_media_storage_path(storage_path)`；`idx_media_status_expire(status,expire_time,id)`；`idx_media_owner_status(owner_user_id,status,id)`；`idx_media_bound(bound_type,bound_id,id)`。

应用层必须维护：`TEMPORARY` 时绑定字段为空且过期时间非空；`BOUND` 时绑定字段非空且过期时间为空。

### 3.6 现有表变更

- `shop.city_code varchar(16) NOT NULL DEFAULT '330100'`：兼容当前尚未传入城市编码的商户写入；城市接口完成后移除业务对默认值的依赖。
- `shop.status tinyint unsigned NOT NULL DEFAULT 1`。
- 新增 `idx_shop_city_type_status(city_code,type_id,status,id)`。
- `user_info.city_code varchar(16) NULL`：已有用户没有选择时允许为空。

所有关系仍由应用维护逻辑外键，本阶段不增加数据库外键。

## 4. 初始化数据冻结

首发城市：

| code | name | sort |
|---|---|---:|
| `330100` | 杭州 | 1 |

官方分区：

| code | name | allowShopVisit | sort |
|---|---|---:|---:|
| `ROAM_DAILY` | 漫游日常 | 0 | 1 |
| `FOOD_DISCOVERY` | 美食探店 | 1 | 10 |
| `COFFEE_DESSERT` | 咖啡甜品 | 1 | 20 |
| `WEEKEND_ESCAPE` | 周末去哪 | 1 | 30 |
| `VALUE_DEALS` | 省钱团购 | 1 | 40 |

分区 ID 不作为配置或客户端常量；业务根据 `code` 获取系统分区。

## 5. 数据库快照产物

- 结构真源：`ray-server/src/main/resources/schema-init.sql`。
- 开发数据真源：`ray-server/src/main/resources/seed-dev.sql`。
- 本阶段直接新增 4 张表，并修改 `shop`、`user_info` 的最终建表定义。
- `seed-dev.sql` 初始化杭州和 5 个官方分区；现有商户 INSERT 使用显式列名，新增列使用建表默认值。
- 不创建 `db/migration`、`db/manual`、版本号、迁移历史或回退脚本。
- `schema-init.sql` 会先删除再重建业务表，只能对允许重建的开发数据库执行。

## 6. 阶段 2 实现结果

- 已新增 City、ContentSection、SectionFollow、MediaAsset Entity、VO、Mapper、Service 和 Controller。
- 已实现启用城市排序、分区可选鉴权、批量关注状态合并以及关注/取消关注幂等语义。
- 已实现媒体所有权、临时状态、24 小时过期、真实图片宽高校验、数据库失败文件补偿和定时清理重试。
- Sa-Token 路由按方法区分公开、可选认证和私有接口；可选认证接口携带无效 Token 时返回 401。
- OpenAPI 已包含冻结的 operationId、Schema、状态码、Bearer 和统一错误响应。
- `DATABASE_SCHEMA.md` 已同步为 SQL 快照事实；测试连接的运行数据库尚未重建，因此当前阶段保持“开发中”。

## 7. 验收清单

- 城市与分区列表只返回启用数据且排序稳定。
- 匿名分区列表的 `followedByMe=false`；`followedOnly=true` 匿名返回 401。
- 分区关注与取消均幂等，唯一约束可阻止并发重复关注。
- “漫游日常”存在、编码唯一、不可通过业务接口删除。
- 图片类型、大小、文件签名、宽高、路径边界和所有权校验完整。
- 临时媒体过期清理、删除重试和数据库/文件补偿可验证。
- `schema-init.sql` 与 `seed-dev.sql` 在可重建的隔离 MySQL 8.x 环境完整初始化通过。
- 初始化后表、列、索引和种子数据与本契约一致。

## 8. 验证记录

- Reactor `mvn test`：34 项，0 失败，10 项外部环境测试默认跳过。
- 真实 OpenAPI/Sa-Token 启动测试：5 项，0 失败；强制 `spring.sql.init.mode=never`。
- OpenAPI 已验证全部路径集合、唯一 `operationId`、可解析 `$ref`、Bearer 声明以及媒体 403/409/413 响应。
- 当前运行数据库确认尚无 `media_asset`，未执行包含 `DROP TABLE` 的 `schema-init.sql`。
