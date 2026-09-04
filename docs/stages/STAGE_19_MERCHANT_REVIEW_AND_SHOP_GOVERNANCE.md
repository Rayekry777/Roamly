# 阶段 19：商户审核与门店治理

```yaml
designVersion: 1
designStatus: 已冻结
implementationStatus: 未实现
dependsOn: 阶段 18 已实现
affectedEnds: 后端、管理 Web
```

## 目标

交付平台商户审核、门店激活、停用和恢复，形成申请到店主经营权限的完整闭环。

## 进入条件与涉及端

- 进入条件为阶段 18 已实现，申请、经营媒体、存储关闭模式和商户隔离均已通过验收。
- 涉及后端与管理 Web；商户端只通过既有当前身份接口观察结果，不在本阶段新增审核操作。

## 状态机、数据与权限

- 申请由 `PENDING`（审核中）进入 `APPROVED`（审核通过）或 `REJECTED`（审核未通过）；门店使用 `PENDING`（待激活）、`ACTIVE`（营业中）、`SUSPENDED`（已停用）、`CLOSED`（已关闭）。
- 审核更新 `merchant_application`、`merchant_account` 和 `shop`，同时追加 `operation_audit_log`；字段与事务事实以数据库和后端契约为准。
- `PLATFORM_ADMIN`（平台超级管理员）与 `MERCHANT_REVIEWER`（商户审核员）可审核和治理，其他角色与商户账号不得调用管理命令。

## 后端

- 审核通过在单事务中条件更新申请、创建或激活门店、绑定申请账号为 `OWNER`（店主）并记录审核审计。
- 驳回必须填写 1 至 500 字原因；已处理申请重复决定返回原结果或 409，不覆盖其他审核人结果。
- 门店状态为 `PENDING`（待激活）、`ACTIVE`（营业中）、`SUSPENDED`（已停用）、`CLOSED`（已关闭）。
- 停用立即阻断该门店全部商户经营接口并使相关会话重新校验状态；恢复不自动恢复已下架商品。
- 列表响应默认脱敏，具备审核权限的详情按白名单显示必要字段；每次查看敏感详情写审计。

## 接口

- `GET /v1/admin/merchant-applications`、`GET /v1/admin/merchant-applications/{id}`。
- `POST .../{id}/approval`、`POST .../{id}/rejection`，均要求 `Idempotency-Key`。
- `GET /v1/admin/shops`、`GET /v1/admin/shops/{id}`。
- `POST .../{id}/suspension`、`POST .../{id}/activation`，携带原因、版本和幂等键。

## 管理 Web

- 商户申请支持状态、城市、类目、手机号和提交时间筛选；详情展示资料、经营图片和审核历史。
- 通过、驳回、停用和恢复使用确认弹窗，提交期间禁用重复操作；409 后刷新详情。
- 门店列表展示经营状态、店主、类目、城市、最近变更和停用原因。

## 失败处理

- 重复或并发决定使用幂等结果或 409，绝不覆盖先完成的审核人和原因。
- 审核事务任一步失败时申请、门店、账号与审计全部保持一致；敏感详情无权限时返回 403。
- 停用或恢复版本冲突后管理 Web 刷新服务端事实，商户会话在下一次授权检查时立即感知停用。

## 验收

- 并发审核、重复命令、事务回滚、跨角色越权、脱敏和敏感查看审计测试通过。
- 管理 Web Playwright 覆盖通过、驳回、停用、恢复和冲突刷新。
- 商户重新查询身份后能看到正确激活或停用状态，真实 HTTP/OpenAPI 通过。
