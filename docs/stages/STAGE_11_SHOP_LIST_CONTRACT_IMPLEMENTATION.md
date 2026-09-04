# 阶段 11：商户列表筛选与排序

状态：已实现（Demo 范围）
更新时间：2026-09-04

`GET /v1/shops` 按 `cityCode` 隔离，支持 `typeId`、`keyword`、`DISTANCE`（距离优先）、`SCORE`（评分优先）、`POPULAR`（热度优先）、页码和坐标。距离排序必须提供完整有效坐标；未定位时客户端降级为评分排序，不伪造本地全量排序。

无角色授权的 `POST /v1/shops` 与 `PUT /v1/shops/{shopId}` 已删除。商户管理后台是明确非目标。
