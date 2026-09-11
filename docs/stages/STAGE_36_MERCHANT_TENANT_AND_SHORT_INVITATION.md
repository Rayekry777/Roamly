# 阶段 36：商户租户身份与短时邀请

```yaml
designVersion: 1
designStatus: 已冻结
implementationStatus: 开发中
dependsOn: 阶段 34 商户账号与个人信息
affectedEnds: 后端、商户小程序、管理 Web
```

## 目标与边界

- 用 `VISITOR`（游客）与 `TENANT`（租户）明确区分未绑定账号和公司主账号。
- 注册账号为游客；入驻审核通过后成为租户；受邀游客只能成为店长或核销员。
- `shop_id` 继续作为公司和数据隔离边界，不新增租户表，不支持一账号多公司。
- 只移除员工邀请二维码；消费者券二维码和商户核销扫码保持不变。

## 身份、权限与事务

- 角色固定为 `VISITOR/TENANT/MANAGER/VERIFIER`。游客不绑定门店，租户和员工必须绑定门店。
- 游客可维护本人资料；未入驻或被驳回时可维护入驻资料；只有从未创建入驻申请的未入驻游客可以接受员工邀请。
- 只有 `TENANT + ACTIVE` 拥有员工管理权限。员工列表只返回店长和核销员。
- 保存或提交入驻与接受邀请统一先锁定账号，确保并发时只能有一条公司归属路径成功。

## 邀请契约

- `POST /v1/merchant/staff-invitations` 接收已注册手机号与 `MANAGER/VERIFIER`，返回六位 `credentialCode`、`expireTime` 和 `remainingSeconds`。
- 凭证实际有效 60 秒、单次使用；数据库只保存手机号绑定的 HMAC-SHA256 摘要，明文只在响应及最长 60 秒幂等缓存中存在。
- 同一手机号同时只允许一个有效邀请；签发前校验账号存在、游客角色、未入驻、无门店且无任何入驻申请。
- `POST /v1/merchant/staff-invitations/acceptance` 接收 `credentialCode`；错误、过期、撤销和已消费统一为不可用，账号 60 秒内失败五次后限流。
- 签发和接受保留幂等键；签发同键同请求在有效期内返回相同凭证，同键不同请求冲突。
- 生产环境必须通过 `RAY_MERCHANT_INVITATION_HMAC_SECRET` 提供独立密钥。

## 数据与客户端

- `merchant_account.role` 默认 `VISITOR` 并增加角色、状态与门店组合约束。
- `merchant_staff_invitation` 使用 `credential_digest`，保存签发指纹、签发幂等键和接受幂等键；业务表总数不变。
- 管理端门店摘要和详情的 `owner*` 字段改为 `tenant*`。
- 商户小程序员工页展示六位凭证、倒计时、复制和撤销；接受页只保留六位数字输入。

## 验收

- 覆盖游客注册、入驻审核迁移、租户权限、邀请资格、60 秒边界、限流、幂等、单次接受与归属并发。
- 校验 OpenAPI 角色枚举、请求响应、安全声明和唯一 `operationId`。
- 后端默认测试、编译、商户小程序检查和管理 Web 检查全部通过后，状态改为“已实现”。
- Demo 建库脚本只更新文件；未经明确授权不执行数据库重建或数据库集成测试。

## 实际验证记录（2026-09-08）

- 后端默认 `mvn test` 共 209 项，187 项通过、22 项按环境开关跳过，0 失败；邀请服务专项测试 10 项通过。
- 真实运行时 `OpenApiAndAuthRuntimeTest` 8 项通过，确认 185 个唯一 `operationId`、Bearer 安全声明、邀请请求响应、`canAcceptStaffInvitation` 与管理端 `tenant*` 字段。
- 商户小程序 `npm run verify`、`npm run format:check` 与构建通过，15 个 Vitest 文件共 73 项通过；管理 Web `pnpm verify`、`pnpm format:check` 与生产构建通过，8 个 Vitest 文件共 28 项通过。
- 商户小程序 `npm run visual:check` 因仓库缺少 `artifacts/ui-qa/references/home-expanded.png` 等基准图未执行，320/375/390/430px 视觉验收仍未确认。
- 未执行 Demo 数据库重建及 `DatabaseBusinessClosureIntegrationTest`；源码、OpenAPI 与客户端已完成，但视觉与数据库门禁未闭环，因此状态保持“开发中”。
