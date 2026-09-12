# 阶段 27：动态二维码与实时资源事件

```yaml
designVersion: 3
designStatus: 已冻结
implementationStatus: 已实现
dependsOn: 阶段 26 已实现
affectedEnds: 后端、消费者小程序、商户小程序、管理 Web
```

## 目标

增加 60 秒动态券二维码、商户 WebSocket 多设备刷新和管理 SSE 资源刷新；推送不替代权威查询。

## 设计复核（v3）

- 商户端使用原生 WebSocket `wss://<host>/v1/merchant/ws`，握手只接受 `Authorization: Bearer <merchant-token>`，服务端从独立 `MERCHANT`（商户端）登录域解析账号和所属门店；非活动账号、无门店账号和跨域 Token 拒绝握手。
- 连接注册到门店级会话集合，Redis Pub/Sub 频道 `roamly:realtime-events` 负责实例间转发；事件发送失败只影响实时性，不回滚已提交的业务事务。
- 管理 SSE 票据绑定签发管理员 ID，30 秒有效且连接成功时原子消费；SSE 连接本身不设置固定总寿命，并以 25 秒注释心跳维持空闲连接和发现断线。SSE 订阅登记管理员固定权限，事件按 `requiredPermission`（所需权限）过滤；管理员退出、停用、改密或角色变化时主动关闭旧事件流，长效 Bearer Token 不出现在 URL。
- WebSocket 与 SSE 事件统一为 `eventId/type/resourceId/shopId/requiredPermission/occurredAt`，客户端只回查资源；连接建立发送 `CONNECTED`，心跳请求返回 `PONG`，断线由客户端回退查询。

## 进入条件与涉及端

- 进入条件为阶段 26 已实现，手输核销、预览令牌、确认、撤销和审计均已有稳定权威查询。
- 涉及后端、消费者小程序、商户小程序和管理 Web；三个客户端分别只消费本端认证域与事件通道。

## 状态机、数据与权限

- 动态二维码令牌与管理事件票据只有有效、已消费和已过期语义，不新增券或资金业务状态；推送也不驱动业务状态迁移。
- 短期令牌和连接会话存 Redis，不新增 Roamly 业务表；业务事件只携带资源定位信息，数据库事实仍由阶段 26 及后续领域表维护。
- 消费者只能申请自己的券令牌，商户连接只能订阅所属门店，管理员事件票据按 `ADMIN`（管理端）权限过滤资源范围。

## 后端

- 动态二维码令牌使用签名随机载荷，绑定券、用户、用途和过期时间，不包含裸券 ID 或手输编号；过期固定为 60 秒。
- 商户 WebSocket 使用 `MERCHANT`（商户端）Token 握手，只订阅所属门店；Redis 负责多实例会话分发。
- 管理端先申请 30 秒有效、单次使用的事件票据，再建立 SSE；长期 Bearer Token 不放入 URL。
- 内部事件包括 `VOUCHER_REDEEMED`（券已核销）、`REDEMPTION_REVERSED`（核销已撤销）、`MERCHANT_REVIEWED`（商户已审核）、`REFUND_UPDATED`（退款已更新）、`SETTLEMENT_UPDATED`（结算已更新）。
- 事件仅包含事件 ID、类型、资源 ID 和发生时间；丢失、重复或乱序不能改变业务事实。

## 接口

- 消费者 `POST /v1/users/me/vouchers/{voucherId}/qr-tokens`。
- 商户 `POST /v1/merchant/redemptions/previews/by-qr-token`，确认仍复用阶段 26 接口。
- 商户 WebSocket 路径 `/v1/merchant/ws`；管理端 `POST /v1/admin/event-tickets`、`GET /v1/admin/events?ticket=...`。

## 客户端

- 消费者券详情显示二维码、剩余秒数、自动刷新和失败重试；退到后台停止刷新，回前台重新获取。
- 商户扫码调用 `wx.scanCode`，用户取消、相机拒绝、令牌过期和业务不可用分别反馈；WebSocket 断线使用指数退避并回退手动刷新。
- 管理 Web 收到 SSE 后按资源 ID 刷新或提示刷新，连接失败回退定时查询。

## 失败处理

- 二维码伪造、过期、跨用户、跨店或重放均拒绝并回到权威券查询；相机取消和授权拒绝使用不同客户端反馈。
- WebSocket 或 SSE 鉴权失败立即断开，Redis 或网络不可用时回退手动刷新或定时查询。
- 重复、乱序或丢失事件只影响刷新及时性，客户端不得据此直接改写核销、退款或结算状态。

## 验收

- 令牌伪造、过期、跨用户、跨店、重放，推送越权和三类 Token 混用测试通过。
- Redis 多实例分发、重复/乱序事件、断线重连和查询回退测试通过。
- 两个小程序与管理 Web 的自动化、真机扫码记录和运行时安全验证通过。

## 实施记录

- 已完成消费者 60 秒动态券令牌、Redis 单次消费和商户二维码预览接口。
- 已完成管理端 30 秒事件票据与 SSE 连接握手，事件仅用于刷新提示。
- 2026-09-12 修复事件流误用票据有效期作为会话超时的问题；异步超时按正常断线收口，不再向已提交的 `text/event-stream` 响应写入 JSON 错误体。
- 商户端继续复用 `wx.scanCode`，断网时保留手输券码回退。
- 自动化验证：后端编译与全量单元测试通过；真机扫码记录保持未确认。
