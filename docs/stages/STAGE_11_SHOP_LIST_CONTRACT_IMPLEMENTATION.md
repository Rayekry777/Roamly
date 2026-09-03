# 阶段 11：商户列表筛选与排序契约

状态：开发中
更新时间：2026-09-03

本阶段将既有商户列表的旧查询参数统一为城市隔离的正式契约。它不调整表结构、不执行初始化 SQL，也不修改商户详情、点评或团购接口。

## 1. 已冻结的 HTTP 契约

| 项目 | 最终定义 |
|---|---|
| operationId | `listShops` |
| 方法与内部路径 | `GET /v1/shops` |
| 外部生产路径 | `GET /api/v1/shops`，由 Nginx 剥离 `/api` 后转发 |
| 鉴权 | 公开；不读取或返回用户个性化状态 |
| 成功响应 | `200 Result<PageResult<ShopVO>>` |
| 请求 DTO | 无请求体、无 DTO；仅使用下述查询参数 |
| ID 表示 | `typeId` 输入及 `ShopVO.id/typeId` 输出均为字符串 |

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 规则 |
|---|---|---|---|---|
| `cityCode` | string | 是 | 无 | 1～16 位字母、数字、`_` 或 `-`；必须是启用城市 |
| `typeId` | string | 否 | 无 | 正整数字符串，转换为内部 `Long` |
| `keyword` | string | 否 | 无 | 去除首尾空白后按商户名称模糊匹配 |
| `sort` | enum | 否 | `POPULAR` | 仅 `DISTANCE`、`SCORE`、`POPULAR` |
| `page` | integer | 否 | `1` | 不小于 1 |
| `size` | integer | 否 | `10` | 1～100 |
| `longitude` | number | 否 | 无 | 与 `latitude` 成对提供，范围 -180～180 |
| `latitude` | number | 否 | 无 | 与 `longitude` 成对提供，范围 -90～90 |

`ShopVO` 沿用已有字段，并在提交完整坐标时返回 `distance`（米）。不新增、重命名或废弃 JSON 字段。

## 2. 排序、错误码与查询语义

- 结果固定限定为 `shop.city_code=cityCode` 且 `shop.status=1`；分类和关键词在此基础上附加。
- `DISTANCE`：需要完整合法坐标，按 `ST_Distance_Sphere` 米距离、商户 ID 升序排序。
- `SCORE`：按评分降序、商户 ID 升序排序；坐标完整时仍计算并返回距离，但不影响排序。
- `POPULAR`：按销量降序、点评数降序、商户 ID 升序排序；坐标完整时仍计算并返回距离，但不影响排序。
- `CITY_NOT_FOUND`（404）：城市不存在或未启用。
- `INCOMPLETE_COORDINATES`（400）：只提交一项坐标。
- `INVALID_COORDINATES`（400）：坐标不在有效范围。
- `DISTANCE_REQUIRES_COORDINATES`（400）：请求距离排序但未提交完整坐标。
- `INVALID_SHOP_SORT`（400）：服务层收到非约定排序值；HTTP 枚举绑定失败由统一参数错误结构返回 400。

## 3. 实现边界与一致性

- Controller 将字符串 ID 和排序枚举转换为 Service 参数，不直接查询数据库。
- Service 校验启用城市、排序与坐标规则，并统一计算分页偏移量。
- Mapper 使用既有 `shop` 的城市、分类、经营状态字段和空间函数完成同一筛选条件下的数据页与总数统计。
- 不再使用旧 Redis GEO 快捷路径：它无法同时保证城市、关键词、经营状态和三种排序条件的一致性。
- 本阶段没有写操作、缓存失效、事务、异步消息或幂等键。商户更新时既有详情缓存删除逻辑保持不变。

## 4. 数据库与风险

- 不变更 `schema-init.sql`、`seed-dev.sql` 或 `DATABASE_SCHEMA.md` 的实际表结构记录。
- 查询依赖现有 `shop.city_code`、`shop.status` 及 `idx_shop_city_type_status`；关键词模糊查询的性能应在真实数据量下评估。
- 距离计算依赖目标 MySQL 支持 `ST_Distance_Sphere`。在部署或真实库联调前，必须确认实际 MySQL 版本与坐标数据有效性。

## 5. 验证与完成条件

已完成：

- Controller 测试覆盖缺少城市、非法排序、字符串 ID 与完整筛选参数转发。
- Service 测试覆盖单边坐标、距离排序缺坐标、非法排序。
- Reactor 定向测试与编译通过。

完成本阶段前仍需：

- 在隔离数据库验证三种实际 SQL 排序、城市隔离、关键词筛选、`distance` 数值和分页总数。
- 重启服务后校验 `/doc.html` 与 `/v3/api-docs` 中的 `listShops` 参数、枚举、错误响应和 Schema。
- 小程序移除旧 `name` 兼容参数，并完成真实接口联调。
