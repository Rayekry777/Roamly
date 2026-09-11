# 阶段 34：商户账号与个人信息

```yaml
status: 开发中
updatedAt: 2026-09-08
scope: 商户显式注册、短信/密码登录、本人资料、手机号与密码修改、头像生命周期
```

## 目标与边界

- 商户登录页提供验证码登录、注册并登录和密码登录；验证码登录只允许已有商户账号。
- 本人资料允许修改头像、昵称、手机号和密码，角色、账号状态与所属门店只读。
- 保留独立 `MERCHANT` 登录域、五种账号状态、三种固定角色和既有退出登录行为。
- 不提供忘记密码、微信一键登录、跨门店切换或从个人信息页修改门店经营资料。

## HTTP 契约

- `POST /v1/merchant/auth/sms-codes` 接受手机号与 `LOGIN/REGISTRATION` 场景。
- `POST /v1/merchant/auth/login` 为短信登录；`POST /v1/merchant/auth/registrations` 注册并登录；`POST /v1/merchant/auth/password-sessions` 为密码登录。
- `GET /v1/merchant/account/profile` 返回完整手机号、昵称、私有头像内容路径、角色、状态和门店摘要。
- `PUT /v1/merchant/account/nickname` 与 `PUT /v1/merchant/account/avatar` 修改账号资料。
- `POST /v1/merchant/account/phone-change/sms-codes`、`PUT /v1/merchant/account/phone` 完成手机号换绑。
- `POST /v1/merchant/account/password-change/sms-codes`、`PUT /v1/merchant/account/password` 完成密码修改。

## 数据、权限与一致性

- `merchant_account` 保存 BCrypt 密码摘要与头像媒体 ID；开发种子密码统一为 `Roamly123`。
- `business_media_asset` 新增 `MERCHANT_AVATAR` 用途和 `MERCHANT_ACCOUNT` 归属，替换头像后在事务提交后清理旧对象。
- 任意已登录商户状态均可维护本人账号资料；经营权限、入驻状态和门店治理规则不变。
- 手机号与密码修改提交后注销该账号全部商户端会话，验证码只在业务成功后消费。

## 验收

- 后端覆盖注册、三种登录、验证码场景、密码限流、资料更新、会话失效和头像生命周期。
- OpenAPI 覆盖新增路径、Bearer 声明、错误响应、Schema 与唯一 `operationId`。
- 商户小程序覆盖三模式登录、受保护入口回跳、个人信息入口与页面、换绑后重登和原退出流程。

## 当前验证

- 后端默认测试共 201 项：179 项通过、22 项按环境开关跳过，0 失败；`ray-server` 编译通过。
- 商户小程序 14 个 Vitest 文件共 69 项通过，ESLint、Stylelint、目标文件格式检查及微信 npm 构建通过。
- 运行时 OpenAPI、Demo 数据库闭环和微信真机尺寸验收尚未执行，因此阶段状态保持“开发中”。
