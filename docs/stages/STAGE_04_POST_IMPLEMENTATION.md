# 阶段 4：统一动态、媒体绑定与点赞实现记录

```yaml
stage: 4
updatedAt: 2026-09-02
status: 开发中
apiImplementationStatus: 已实现
runtimeDatabaseInitialized: 未实现
legacyMediaConversionStatus: 未实现
legacyLikeConversionStatus: 未实现
```

## 1. 实现范围

阶段 4 已实现阶段 3 冻结的 9 个 Post 接口、数据库持久化模型、媒体事务绑定、点赞事实和 OpenAPI 契约。旧 Blog 接口继续保留，避免在小程序切换前中断现有功能。

已实现：

- `ContentPost`、`PostMedia`、`PostLike` Entity 与对应 Mapper。
- `PostCreateDTO`、`PostUpdateDTO` 和动态卡片、详情、媒体、商户摘要 VO。
- 普通动态自动绑定 `ROAM_DAILY`，探店动态校验分区和商户。
- 创建、编辑、删除、用户动态列表、点赞、取消点赞和点赞用户列表。
- 媒体所有权、状态、有效期、重复绑定和更新移除的事务处理。
- 数据库点赞事实、冗余计数和提交后 Redis 同步。
- 可选 Bearer 读取策略、完整 OpenAPI Schema 和错误响应。

未完成：

- 运行数据库尚未按 `schema-init.sql` 重建，因此不能执行真实 Post CRUD 集成测试。
- 旧 Blog 图片需要读取真实文件后才能生成可靠媒体元数据，当前不伪造。
- 旧点赞用户集合需要在旧接口停写窗口从 Redis 转存，当前不伪造。
- 小程序首页和统一发布器已在阶段 5 切换到 Post 契约；旧 Blog 详情和兼容页面等待阶段 11 统一退役。

## 2. 事务与一致性结果

### 创建和更新

- 创建动态前按媒体 ID 升序锁定记录，校验所有权、临时状态、绑定字段和过期时间。
- 动态、媒体关系和媒体 `BOUND` 状态在一个数据库事务内提交；任一步骤失败全部回滚。
- 编辑时允许保留当前动态已有媒体；新增媒体重新校验并绑定；移除媒体标记 `DELETED`，提交后删除物理文件。
- 普通动态不允许提交分区和商户，服务端按 `ROAM_DAILY` 编码解析默认分区。
- 探店动态的城市只取启用商户的 `cityCode`，客户端不能覆盖。

### 删除和点赞

- 动态删除为 `status=DELETED` 逻辑删除，保留媒体、点赞和后续评论审计关系。
- 点赞使用 `post_like(post_id,user_id)` 唯一索引和 `INSERT IGNORE` 收敛并发重复请求。
- 只有实际新增或删除点赞事实时才修改 `liked_count`。
- Redis `post:liked:{postId}` 在事务提交后同步；失败只记录不含用户敏感信息的告警，不回滚数据库事实。
- 关注流投递使用 `feed:following:{userId}`，在发布事务提交后执行；失败由后续信息流补偿阶段处理。

## 3. HTTP 与鉴权结果

| 接口组 | 鉴权行为 |
|---|---|
| 创建、更新、删除、我的动态、点赞和取消点赞 | 必须携带有效 Bearer Token |
| 动态详情、用户动态、点赞用户列表 | 允许匿名；携带 Token 时必须有效 |

成功状态遵循契约：创建 201，查询和更新 200，删除与点赞切换 204。所有 ID 在 JSON 和 OpenAPI 中保持字符串。

## 4. 验证记录

- Reactor `mvn test`：43 项，0 失败，10 项外部环境测试默认跳过。
- Reactor `mvn -DskipTests compile`：通过。
- 真实 OpenAPI/Sa-Token 测试：5 项通过，强制 `spring.sql.init.mode=never`。
- 已验证 9 个 Post operationId、Bearer 边界、403/409 错误响应、Schema 注册和全部 `$ref` 可解析。
- Java 规范扫描、Markdown 本地链接检查和 `git diff --check`：通过。
- 未执行 `schema-init.sql`，未清空或修改运行数据库。

## 5. 完成条件

阶段 4 保持“开发中”，直到满足：

- 用户明确允许重建隔离开发数据库并完成 17 张表初始化。
- Post 创建、编辑、删除、媒体绑定和点赞执行真实数据库集成测试。
- 旧 Blog 与 Post 的行数、ID、字段、媒体和点赞事实核对完成。
- 对无法读取的旧图片形成明确核对清单。

源码与 HTTP 能力已经实现，后续阶段可以在不改动契约的前提下开始小程序页面迁移；上线前仍必须完成以上数据库验收。
