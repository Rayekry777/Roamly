# 阶段 33：消费者账号与资料重构

```yaml
status: 已实现
updatedAt: 2026-09-08
scope: 消费者显式注册、短信/密码登录、本人资料、公开主页、手机号与密码修改、头像生命周期
```

## 目标与边界

- 验证码登录只登录已注册消费者；注册单独校验验证码并设置 BCrypt 密码，成功后直接签发消费者 Token。
- 本人资料只编辑头像、昵称、手机号、密码、性别和生日；公开主页只暴露头像、昵称、性别及关系/动态统计。
- 不提供忘记密码、无旧密码找回、真实短信、微信一键登录，也不修改商户和管理端认证。

## HTTP 契约

- `POST /v1/auth/sms-codes` 接受手机号和 `LOGIN/REGISTRATION` 场景；验证码 Redis 键按场景隔离，2 分钟有效、60 秒发送冷却。
- `POST /v1/auth/registrations` 接受手机号、验证码、密码和确认密码；手机号唯一，成功创建 `user` 与 `user_profile` 并登录。
- `POST /v1/auth/sessions` 为短信登录，`POST /v1/auth/password-sessions` 为密码登录；密码失败按手机号与客户端地址摘要累计，5 次后限制 15 分钟。
- `GET/PUT /v1/users/me/profile` 查询本人资料或更新性别、生日；`PUT /v1/users/me/nickname` 与 `PUT /v1/users/me/avatar` 分别处理限频昵称和头像绑定。
- `POST /v1/users/me/phone-change/sms-codes` 向新号码发码，`PUT /v1/users/me/phone` 校验旧密码、新号验证码和唯一性。
- `POST /v1/users/me/password-change/sms-codes` 向当前号码发码，`PUT /v1/users/me/password` 校验旧密码、验证码和两次新密码。
- `PUT /v1/users/me/city-preference` 只同步内部城市偏好；`GET /v1/users/{userId}/profile` 为匿名公开资料，不返回手机号、生日和城市偏好。
- 昵称在确认更新时、手机号在请求换绑验证码时、密码在确认修改时分别校验新值不得与当前值相同，并返回明确的 `NICKNAME_UNCHANGED`、`PHONE_UNCHANGED` 或 `PASSWORD_UNCHANGED`。

## 数据与事务

- `user` 保存唯一手机号、BCrypt `password_hash`、昵称、`nickname_updated_at`、头像路径和头像媒体 ID；昵称使用北京时间自然日限频并通过条件更新防并发。
- `user_profile` 替代 `user_info`，只保存 `gender`、`birthday` 与内部 `current_city_code`；性别为 `UNDISCLOSED/MALE/FEMALE`。
- 关注、粉丝与正常动态数从关系表实时统计，不保存冗余资料计数；普通动态继续读取内部城市偏好。
- 头像使用 `media_asset` 的 `USER_AVATAR` 绑定类型；更新时锁定用户和新媒体，提交后清理旧头像文件。
- 手机号与密码修改在事务成功后注销该用户全部消费者会话；验证码仅在业务成功后消费。

## 失败模式与验收

- 固定错误覆盖账号未注册/已注册、验证码无效、密码错误/受限、手机号占用、昵称限频、昵称/手机号/密码未变化、生日未来日期、媒体不存在/越权/过期及密码确认不一致。
- 单元测试覆盖认证、限流、资料隐私、昵称并发、换绑/改密和头像生命周期；运行时测试校验 OpenAPI 路径、安全声明、错误响应与唯一 `operationId`。
- Demo 数据库通过完整快照重建验收，三个消费者种子密码统一为 `Roamly123`；不对生产或需保留数据的数据库执行该快照。

## 实际验证记录（2026-09-08）

- `mvn -q -pl ray-server -am test`：193 项执行，171 项通过、22 项按环境开关跳过，0 失败、0 错误。
- `mvn -q -pl ray-server -am -DskipTests compile` 与 `test-compile` 通过；未执行数据库重建和真实短信联调。
- 消费者小程序 `npm run verify`：类型检查、ESLint、样式检查及 153 项 Vitest 全部通过；`npm run format:check` 通过。
- 运行时 OpenAPI 测试保留为环境开关控制，新增认证、资料与安全路径及 Schema 已纳入契约；未执行真机验收。
