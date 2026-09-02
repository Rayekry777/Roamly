# API v1 破坏性迁移说明

本次升级不保留旧接口和旧 Token 兼容层。后端与 `Roamly-miniapp` 必须同步发布；客户端升级后需要重新登录。正式 HTTP 契约以运行中的 `/v3/api-docs` 为准。

## 通用变化

- 对外地址统一为 `/api/v1`；后端 Controller 与客户端业务路径使用 `/v1`，生产环境的 `apiBaseUrl` 和 Nginx 负责唯一的 `/api` 前缀。
- 请求头从旧 `authorization: <token>` 改为 `Authorization: Bearer <token>`。
- Token 存储由自研 Redis 会话改为 Sa-Token RedisTemplate，旧 Token 全部失效。
- 成功结构统一为 `{code,message,data}`，错误结构统一为 `{code,message,fieldErrors}`。
- OpenAPI 模型统一命名为 `Result`、`ErrorResult`、`PageResult` 和 `CursorPageResult`。
- 所有业务 ID 在 JSON 中使用字符串，不再传 JavaScript number。
- 页码分页统一为 `{items,page,size,total}`，滚动分页统一为 `{items,nextCursor,nextOffset,hasMore}`。

## 路径映射

下表记录客户端可见的对外地址。后端直连调试时去掉 `/api`，例如对外 `/api/v1/shops` 对应后端 `/v1/shops`。

| 旧接口 | API v1 |
|---|---|
| `POST /user/code` | `POST /api/v1/auth/sms-codes` |
| `POST /user/login` | `POST /api/v1/auth/sessions` |
| `POST /user/logout` | `DELETE /api/v1/auth/session` |
| `GET /user/me` | `GET /api/v1/users/me` |
| `GET /user/{id}` | `GET /api/v1/users/{userId}` |
| `GET /user/info/{id}` | `GET /api/v1/users/{userId}/profile` |
| `POST /user/sign` | `PUT /api/v1/users/me/check-ins/today` |
| `GET /user/sign/count` | `GET /api/v1/users/me/check-ins/streak` |
| `PUT /follow/{id}/{isFollow}` | `PUT/DELETE /api/v1/users/me/following/{userId}` |
| `GET /follow/or/not/{id}` | `GET /api/v1/users/me/following/{userId}` |
| `GET /follow/common/{id}` | `GET /api/v1/users/{userId}/common-following` |
| `GET /shop/{id}` | `GET /api/v1/shops/{shopId}` |
| `GET /shop/of/type`、`GET /shop/of/name` | `GET /api/v1/shops`，使用 `typeId/name/page/size/longitude/latitude` |
| `POST /shop` | `POST /api/v1/shops` |
| `PUT /shop` | `PUT /api/v1/shops/{shopId}` |
| `GET /shop-type/list` | `GET /api/v1/shop-types` |
| `GET /blog/hot` | `GET /api/v1/blogs` |
| `POST /blog` | `POST /api/v1/blogs` |
| `GET /blog/{id}` | `GET /api/v1/blogs/{blogId}` |
| `GET /blog/of/me` | `GET /api/v1/users/me/blogs` |
| `GET /blog/of/user` | `GET /api/v1/users/{userId}/blogs` |
| `PUT /blog/like/{id}` | `PUT/DELETE /api/v1/blogs/{blogId}/like` |
| `GET /blog/likes/{id}` | `GET /api/v1/blogs/{blogId}/likes` |
| `GET /blog/of/follow` | `GET /api/v1/feeds/following` |
| `POST /voucher` | `POST /api/v1/vouchers` |
| `POST /voucher/seckill` | `POST /api/v1/seckill-vouchers` |
| `GET /voucher/list/{shopId}` | `GET /api/v1/shops/{shopId}/vouchers` |
| `POST /voucher-order/seckill/{id}` | `POST /api/v1/seckill-vouchers/{voucherId}/orders` |
| `POST /upload/blog` | `POST /api/v1/blog-images` |
| `DELETE /upload/blog?name=...` | `DELETE /api/v1/blog-images?path=...` |

静态资源 `/blogs/**` 不变。评论接口没有迁移目标，继续标记为未实现。

生产环境约定：

```nginx
location /api/ {
    proxy_pass http://backend/;
}
```

`proxy_pass` 末尾的 `/` 用于剥离网关前缀。客户端不得同时在 `apiBaseUrl` 和业务路径中重复配置 `/api`。
