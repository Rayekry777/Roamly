# 阶段 14：Demo 直接重构与旧能力退役

状态：已实现（Demo 范围）
更新时间：2026-09-04

旧 Blog、旧评论/点赞、旧图片上传、旧优惠券和旧秒杀的 API、Controller、Service、Mapper、模型、Lua 与表均已移除。当前替代分别为 Post/Comment/MediaAsset 与 VoucherProduct/VoucherOrder/UserVoucher。

Demo 开发库直接执行 19 表快照并重新生成当前模型种子，不迁移旧数据、不双写、不保留兼容路由或转换脚本。生产存量迁移不在本项目当前范围。
