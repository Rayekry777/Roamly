SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 此文件仅用于可重建的开发数据库，所有样例直接使用当前领域模型。
-- Demo 管理账号统一密码为 Roamly123，仅用于可重建的开发库。
-- admin 可直接完成全量管理端演示；reviewer.demo 与 finance.demo 用于角色隔离演示。
INSERT INTO `admin_user`
  (`id`, `username`, `password_hash`, `display_name`, `role`, `status`, `force_password_change`, `version`)
VALUES
  (1, 'admin', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', 'Roamly 管理员', 'PLATFORM_ADMIN', 'ACTIVE', 0, 0),
  (2, 'reviewer.demo', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '审核演示员', 'MERCHANT_REVIEWER', 'ACTIVE', 0, 0),
  (3, 'finance.demo', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '财务演示员', 'FINANCE', 'ACTIVE', 0, 0),
  (4, 'reviewer.disabled', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '已停用审核员', 'MERCHANT_REVIEWER', 'DISABLED', 0, 1),
  (5, 'service.demo', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '客服演示员', 'CUSTOMER_SERVICE', 'ACTIVE', 0, 0);

INSERT INTO `city` (`id`, `code`, `name`, `status`, `sort`)
VALUES
  (1, '330100', '杭州', 1, 1),
  (2, '630100', '西宁', 1, 2);

INSERT INTO `district`
  (`id`, `code`, `city_code`, `name`, `center_longitude`, `center_latitude`, `service_radius_km`, `status`, `sort`)
VALUES
  (1, '330105', '330100', '拱墅区', 120.149192, 30.316078, 35.00, 1, 1),
  (2, '630105', '630100', '城北区', 101.749746, 36.742782, 35.00, 1, 1);

INSERT INTO `content_section`
  (`id`, `code`, `name`, `description`, `allow_shop_visit`, `status`, `sort`)
VALUES
  (1, 'ROAM_DAILY', '漫游日常', '记录日常生活与城市漫游', 0, 1, 1),
  (2, 'FOOD_DISCOVERY', '美食探店', '分享餐厅、小吃与本地美食体验', 1, 1, 10),
  (3, 'COFFEE_DESSERT', '咖啡甜品', '分享咖啡馆、烘焙与甜品体验', 1, 1, 20),
  (4, 'WEEKEND_ESCAPE', '周末去哪', '发现周末游玩和城市休闲去处', 1, 1, 30),
  (5, 'VALUE_DEALS', '省钱团购', '发现值得购买的本地团购', 1, 1, 40);

INSERT INTO `shop_type` (`id`, `name`, `icon`, `sort`) VALUES
  (1, '美食', '/types/ms.png', 1),
  (2, '休闲娱乐', '/types/leisure.png', 2),
  (3, '运动健身', '/types/sport.png', 3);

-- Demo 消费者账号统一密码为 Roamly123。
INSERT INTO `user` (`id`, `phone`, `password_hash`, `nick_name`, `icon`) VALUES
  (1, '13686869696', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '小鱼同学', '/imgs/blogs/blog1.jpg'),
  (2, '13838411438', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '可可今天不吃肉', '/imgs/icons/kkjtbcr.jpg'),
  (3, '13456789011', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '漫游测试员', '');

INSERT INTO `user_profile` (`user_id`, `gender`, `birthday`, `current_city_code`) VALUES
  (1, 'FEMALE', '2000-06-18', '330100'),
  (2, 'FEMALE', '1998-10-02', '330100'),
  (3, 'UNDISCLOSED', NULL, '330100');

INSERT INTO `shop`
  (`id`, `name`, `type_id`, `city_code`, `district_code`, `images`, `area`, `address`, `x`, `y`, `avg_price`, `sold`, `comments`, `score`, `open_hours`, `status`, `source_application_id`, `business_hours_json`, `activated_at`, `version`)
VALUES
  (1, '103 茶餐厅', 1, '330100', '330105', 'https://example.com/images/tea-restaurant.jpg', '大关', '金华路锦昌文华苑 29 号', 120.149192, 30.316078, 80, 1, 2, 45, '10:00-22:00', 'ACTIVE', 9101, '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"SUNDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]}]', '2026-08-01 10:00:00', 0),
  (2, '漫游咖啡实验室', 1, '330100', '330105', 'https://example.com/images/coffee-lab.jpg', '运河上街', '台州路 2 号', 120.151954, 30.324970, 68, 1, 2, 45, '09:00-21:00', 'ACTIVE', 9102, '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"SUNDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]}]', '2026-08-02 10:00:00', 0),
  (3, '周末放映厅', 2, '330100', '330105', 'https://example.com/images/cinema.jpg', '拱宸桥', '丽水路 58 号', 120.146659, 30.312742, 120, 0, 0, 0, '13:00-23:00', 'ACTIVE', 9103, '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"SUNDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]}]', '2026-08-03 10:00:00', 0),
  (4, '城北校园漫游咖啡', 1, '630100', '630105', 'https://example.com/images/xining-coffee.jpg', '城北区', '青海师范大学（城北校区）附近', 101.749746, 36.742782, 32, 12, 0, 0, '09:00-21:00', 'ACTIVE', 9104, '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"SUNDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]}]', '2026-09-10 10:00:00', 0);

INSERT INTO `merchant_account`
  (`id`, `phone`, `password_hash`, `nickname`, `avatar_media_id`, `role`, `status`, `shop_id`, `disabled_source`, `disabled_reason`, `disabled_at`, `version`)
VALUES
  (1, '13900000001', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '茶餐厅租户', NULL, 'TENANT', 'ACTIVE', 1, NULL, NULL, NULL, 0),
  (2, '13900000002', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '待入驻游客', NULL, 'VISITOR', 'NOT_APPLIED', NULL, NULL, NULL, NULL, 0),
  (3, '13900000003', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '审核中游客', NULL, 'VISITOR', 'PENDING', NULL, NULL, NULL, NULL, 0),
  (4, '13900000004', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '待修改游客', NULL, 'VISITOR', 'REJECTED', NULL, NULL, NULL, NULL, 0),
  (5, '13900000005', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '已停用租户', NULL, 'TENANT', 'DISABLED', 2, 'ACCOUNT_GOVERNANCE', '平台账号治理样例', '2026-09-01 09:00:00', 1),
  (6, '13900000006', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '放映厅租户', NULL, 'TENANT', 'ACTIVE', 3, NULL, NULL, NULL, 0),
  (31, '13900000031', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '茶餐厅店长', NULL, 'MANAGER', 'ACTIVE', 1, NULL, NULL, NULL, 0),
  (32, '13900000032', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '茶餐厅核销员', NULL, 'VERIFIER', 'ACTIVE', 1, NULL, NULL, NULL, 0),
  (33, '13900000033', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '已停用店长', NULL, 'MANAGER', 'DISABLED', 1, 'STAFF_MANAGEMENT', '租户停用员工样例', '2026-09-02 09:00:00', 1),
  (34, '13900000034', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', '待接受邀请游客', NULL, 'VISITOR', 'NOT_APPLIED', NULL, NULL, NULL, NULL, 0);

INSERT INTO `merchant_staff_invitation`
  (`id`, `shop_id`, `inviter_account_id`, `credential_digest`, `target_phone`, `target_role`, `status`,
   `expire_time`, `accepted_time`, `revoked_time`, `accepted_account_id`,
   `issue_idempotency_key`, `issue_request_fingerprint`, `acceptance_idempotency_key`, `create_time`)
VALUES
  (10001, 1, 1, '983e91a7597b062ef7dfe0e3ac2a2bca97d7974d391dc36180510dafe6c4c84a', '13900000031', 'MANAGER', 'ACCEPTED',
   '2026-09-01 09:01:00', '2026-09-01 09:00:30', NULL, 31, 'demo-accepted-10001', SHA2('13900000031|MANAGER', 256), 'demo-accept-10001', '2026-09-01 09:00:00'),
  (10002, 1, 1, 'e068b57f0f9f0d2c302000210c74ae19a90c72518dd5d005b98383149b426940', '13900000034', 'VERIFIER', 'PENDING',
   DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 60 SECOND), NULL, NULL, NULL, 'demo-pending-10002', SHA2('13900000034|VERIFIER', 256), NULL, CURRENT_TIMESTAMP),
  (10003, 1, 1, '3b39afc24e87c7696c345178df7fbafe3257580bed88f5d717870751cb1c2ffb', '13900000011', 'MANAGER', 'REVOKED',
   '2026-09-04 09:31:00', NULL, '2026-09-04 09:30:30', NULL, 'demo-revoked-10003', SHA2('13900000011|MANAGER', 256), NULL, '2026-09-04 09:30:00'),
  (10004, 1, 1, '651907f25e66ed4c482e320156c4c3346cd8082b6a33b19d516d5f889bbba875', '13900000012', 'VERIFIER', 'EXPIRED',
   '2026-08-31 09:01:00', NULL, NULL, NULL, 'demo-expired-10004', SHA2('13900000012|VERIFIER', 256), NULL, '2026-08-31 09:00:00');

INSERT INTO `business_media_asset`
  (`id`, `uploader_merchant_account_id`, `purpose`, `status`, `bucket_name`, `object_key`, `original_filename`, `mime_type`, `byte_size`, `width`, `height`, `owner_type`, `owner_id`, `sort_order`, `bound_at`, `expires_at`)
VALUES
  (8001, 3, 'LICENSE', 'BOUND', 'roamly-business-local', 'seed/onboarding/license/application-3-license.png', '营业执照.png', 'image/png', 543, 400, 400, 'MERCHANT_APPLICATION', 9001, 0, '2026-09-04 10:00:00', NULL),
  (8002, 4, 'LICENSE', 'BOUND', 'roamly-business-local', 'seed/onboarding/license/application-4-license.png', '营业执照.png', 'image/png', 543, 400, 400, 'MERCHANT_APPLICATION', 9002, 0, '2026-09-03 10:00:00', NULL),
  (8101, 1, 'LICENSE', 'BOUND', 'roamly-business-local', 'seed/onboarding/license/application-9101-license.png', '营业执照.png', 'image/png', 543, 400, 400, 'MERCHANT_APPLICATION', 9101, 0, '2026-08-01 10:00:00', NULL),
  (8102, 5, 'LICENSE', 'BOUND', 'roamly-business-local', 'seed/onboarding/license/application-9102-license.png', '营业执照.png', 'image/png', 543, 400, 400, 'MERCHANT_APPLICATION', 9102, 0, '2026-08-02 10:00:00', NULL),
  (8103, 6, 'LICENSE', 'BOUND', 'roamly-business-local', 'seed/onboarding/license/application-9103-license.png', '营业执照.png', 'image/png', 543, 400, 400, 'MERCHANT_APPLICATION', 9103, 0, '2026-08-03 10:00:00', NULL),
  (8201, 1, 'VOUCHER_COVER', 'BOUND', 'roamly-business-local', 'seed/voucher/cover/voucher-3105-cover.png', '套餐券封面.png', 'image/png', 543, 400, 400, 'VOUCHER_PRODUCT', 3105, 0, '2026-09-04 16:00:00', NULL);

INSERT INTO merchant_application
  (id, merchant_account_id, status, shop_name, license_number, legal_representative, contact_name, contact_phone, shop_type_id, city_code, district, address, longitude, latitude, business_hours_json, license_media_id, gallery_media_ids_json, settlement_account_name, settlement_bank_name, settlement_account_suffix, rejection_reason, submission_idempotency_key, review_decision, review_idempotency_key, review_request_fingerprint, submitted_at, reviewed_at, reviewer_admin_id, approved_shop_id, version)
VALUES
  (9001, 3, 'PENDING', '审核中的漫游小馆', '91330100MA2DEMO003', '周小路', '周小路', '13900000003', 1, '330100', '拱墅区', '运河路 18 号', 120.149100, 30.316000, '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"09:00","close":"22:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"SUNDAY","closed":true,"periods":[]}]', 8001, '[]', '杭州漫游餐饮有限公司', 'Roamly Mock 银行', '0003', NULL, 'stage18-seed-pending', NULL, NULL, NULL, '2026-09-04 10:00:00', NULL, NULL, NULL, 1),
  (9002, 4, 'REJECTED', '待修改的城市小店', '91330100MA2DEMO004', '陈小满', '陈小满', '13900000004', 2, '330100', '拱墅区', '丽水路 66 号', 120.146600, 30.312700, '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"10:00","close":"20:00"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"10:00","close":"20:00"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"10:00","close":"20:00"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"10:00","close":"20:00"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"10:00","close":"21:00"}]},{"dayOfWeek":"SATURDAY","closed":true,"periods":[]},{"dayOfWeek":"SUNDAY","closed":true,"periods":[]}]', 8002, '[]', '陈小满', 'Roamly Mock 银行', '0004', '营业执照边缘不完整，请重新上传清晰图片', 'stage18-seed-rejected', 'REJECTION', 'stage19-seed-rejected', '2a3f6a8f64f45661398b91fe97da155e9757d16a596c0ff712b8ca8cef028980', '2026-09-03 10:00:00', '2026-09-03 15:00:00', 1, NULL, 2),
  (9101, 1, 'APPROVED', '103 茶餐厅', '91330100MA2SHOP001', '林一川', '林一川', '13900000001', 1, '330100', '拱墅区', '金华路锦昌文华苑 29 号', 120.149192, 30.316078, '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"SUNDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]}]', 8101, '[]', '杭州茶餐厅有限公司', 'Roamly Mock 银行', '1001', NULL, 'stage19-shop-1-submit', 'APPROVAL', 'stage19-shop-1-approval', 'f32df70205078a91d56a87b355f2736a2f29b2824100f2a48007926908ba1b48', '2026-08-01 09:00:00', '2026-08-01 10:00:00', 1, 1, 2),
  (9102, 5, 'APPROVED', '漫游咖啡实验室', '91330100MA2SHOP002', '许漫漫', '许漫漫', '13900000005', 1, '330100', '拱墅区', '台州路 2 号', 120.151954, 30.324970, '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"SUNDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]}]', 8102, '[]', '杭州漫游咖啡有限公司', 'Roamly Mock 银行', '1002', NULL, 'stage19-shop-2-submit', 'APPROVAL', 'stage19-shop-2-approval', 'cfb343a1696d9baac4bdf3f3e88951cfb26e05c4046d393b7c8a646d62eb678b', '2026-08-02 09:00:00', '2026-08-02 10:00:00', 1, 2, 2),
  (9103, 6, 'APPROVED', '周末放映厅', '91330100MA2SHOP003', '沈周末', '沈周末', '13900000006', 2, '330100', '拱墅区', '丽水路 58 号', 120.146659, 30.312742, '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"SUNDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]}]', 8103, '[]', '杭州周末文化有限公司', 'Roamly Mock 银行', '1003', NULL, 'stage19-shop-3-submit', 'APPROVAL', 'stage19-shop-3-approval', '2cda2e3eb0b1aee04e91088d4ff702554b0404a84fb6de62b2cfdf0e03bc17d1', '2026-08-03 09:00:00', '2026-08-03 10:00:00', 1, 3, 2);

INSERT INTO `section_follow` (`id`, `user_id`, `section_id`, `create_time`) VALUES
  (1, 1, 2, '2026-09-01 09:00:00'),
  (2, 1, 3, '2026-09-01 09:01:00'),
  (3, 2, 1, '2026-09-01 09:02:00');

INSERT INTO `follow` (`id`, `user_id`, `follow_user_id`, `create_time`) VALUES
  (1, 1, 2, '2026-09-01 09:10:00'),
  (2, 2, 1, '2026-09-01 09:11:00'),
  (3, 3, 1, '2026-09-01 09:12:00');

INSERT INTO `media_asset`
  (`id`, `owner_user_id`, `storage_path`, `mime_type`, `file_size`, `width`, `height`, `status`, `bound_type`, `bound_id`, `expire_time`, `create_time`, `update_time`)
VALUES
  (501, 1, 'seed/posts/canal-walk.jpg', 'image/jpeg', 128000, 1200, 900, 1, 1, 1001, NULL, '2026-09-01 18:20:00', '2026-09-01 18:30:00'),
  (502, 2, 'seed/reviews/tea-set.jpg', 'image/jpeg', 96000, 1080, 1080, 1, 2, 4001, NULL, '2026-09-02 13:00:00', '2026-09-02 13:10:00');

INSERT INTO `post`
  (`id`, `user_id`, `section_id`, `shop_visit`, `shop_id`, `city_code`, `title`, `content`, `liked_count`, `comment_count`, `status`, `create_time`, `update_time`)
VALUES
  (1001, 1, 1, 0, NULL, '330100', '傍晚的运河散步', '下班后沿着运河走了一圈，风很舒服。', 2, 1, 0, '2026-09-01 18:30:00', '2026-09-01 18:30:00'),
  (1002, 2, 2, 1, 1, '330100', '一顿很满足的茶餐厅晚餐', '菠萝油和奶茶都很适合周末慢慢吃。', 2, 2, 0, '2026-09-02 12:10:00', '2026-09-02 12:10:00'),
  (1003, 3, 4, 1, 3, '330100', '周末的电影和朋友', '适合和朋友一起放空半天的小地方。', 1, 0, 0, '2026-09-02 16:20:00', '2026-09-02 16:20:00'),
  (1004, 1, 1, 1, 4, '630100', '城北校区附近的咖啡时间', '在青海师范大学城北校区附近找到一家适合自习的咖啡店。', 0, 0, 0, '2026-09-10 12:00:00', '2026-09-10 12:00:00');

INSERT INTO `post_media` (`id`, `post_id`, `media_asset_id`, `sort`, `create_time`)
VALUES (1, 1001, 501, 0, '2026-09-01 18:30:00');

INSERT INTO `post_like` (`id`, `post_id`, `user_id`, `create_time`) VALUES
  (1, 1001, 2, '2026-09-01 18:40:00'),
  (2, 1001, 3, '2026-09-01 18:41:00'),
  (3, 1002, 1, '2026-09-02 12:15:00'),
  (4, 1002, 3, '2026-09-02 12:16:00'),
  (5, 1003, 1, '2026-09-02 16:30:00');

INSERT INTO `post_comment`
  (`id`, `post_id`, `user_id`, `root_id`, `parent_id`, `reply_to_user_id`, `content`, `liked_count`, `reply_count`, `author_replied`, `status`, `create_time`, `update_time`)
VALUES
  (2001, 1001, 2, NULL, NULL, NULL, '这个时间去最舒服。', 2, 0, 0, 0, '2026-09-01 19:00:00', '2026-09-01 19:00:00'),
  (2002, 1002, 1, NULL, NULL, NULL, '已经收藏，周末去试试。', 2, 1, 1, 0, '2026-09-02 12:20:00', '2026-09-02 12:20:00'),
  (2003, 1002, 2, 2002, 2002, 1, '记得试试菠萝油。', 1, 0, 0, 0, '2026-09-02 12:25:00', '2026-09-02 12:25:00');

INSERT INTO `post_comment_like` (`id`, `comment_id`, `user_id`, `create_time`) VALUES
  (1, 2001, 1, '2026-09-01 19:10:00'),
  (2, 2001, 3, '2026-09-01 19:11:00'),
  (3, 2002, 2, '2026-09-02 12:30:00'),
  (4, 2002, 3, '2026-09-02 12:31:00'),
  (5, 2003, 1, '2026-09-02 12:32:00');

INSERT INTO `shop_review`
  (`id`, `shop_id`, `user_id`, `verified_user_voucher_id`, `score`, `content`, `status`, `create_time`, `update_time`)
VALUES
  (4001, 1, 2, NULL, 5, '菠萝油酥脆，奶茶也很顺口。', 0, '2026-09-02 13:10:00', '2026-09-02 13:10:00'),
  (4002, 1, 3, NULL, 4, '出餐很快，周末人会多一些。', 0, '2026-09-03 11:20:00', '2026-09-03 11:20:00'),
  (4003, 2, 1, NULL, 5, '环境安静，双人套餐很适合聊天。', 0, '2026-09-03 15:00:00', '2026-09-03 15:00:00'),
  (4004, 2, 2, 7010, 4, '核销顺利，套餐内容与页面一致。', 0, '2026-09-04 18:20:00', '2026-09-04 18:20:00');

INSERT INTO `shop_review_media` (`id`, `review_id`, `media_asset_id`, `sort`, `create_time`)
VALUES (1, 4001, 502, 0, '2026-09-02 13:10:00');

INSERT INTO `voucher_product`
  (`id`, `shop_id`, `product_type`, `title`, `sub_title`, `cover_media_id`, `detail_media_ids_json`,
   `price_amount`, `market_amount`, `merchant_subsidy_amount`, `platform_discount_amount`, `face_value_amount`, `minimum_spend_amount`, `total_use_count`, `total_stock`, `available_stock`, `sold_count`,
   `purchase_limit`, `sale_begin_time`, `sale_end_time`, `validity_type`, `valid_begin_time`,
   `valid_end_time`, `valid_days`, `usage_rules_json`, `excluded_dates_json`, `reservation_required`,
   `reservation_notice`, `stackable`, `refund_anytime`, `refund_expired`, `review_status`,
   `sale_status`, `rejection_reason`, `submission_idempotency_key`, `submission_request_fingerprint`,
   `submitted_at`, `version`)
VALUES
  (3001, 1, 'CASH', '103 茶餐厅 100 元代金券', '工作日、周末通用', NULL, '[]',
   8000, 10000, 500, 300, 10000, 10000, NULL, 200, 198, 1, 1,
   '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'DAYS_AFTER_PURCHASE', NULL, NULL, 30,
   '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"SUNDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]}]',
   '[]', 0, NULL, 0, 1, 1, 'APPROVED', 'ON_SALE', NULL, NULL, NULL, NULL, 0),
  (3002, 2, 'PACKAGE', '漫游咖啡双人套餐', '限店内堂食使用', NULL, '[]',
   6800, 8800, 300, 200, NULL, NULL, NULL, 80, 79, 1, 1,
   '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'DAYS_AFTER_PURCHASE', NULL, NULL, 15,
   '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"SUNDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]}]',
   '[]', 1, '请至少提前一小时预约', 0, 1, 0, 'APPROVED', 'ON_SALE', NULL, NULL, NULL, NULL, 0),
  (3101, 1, 'PACKAGE', '下午茶双人套餐', '商户草稿，可继续编辑', NULL, '[]',
   5200, 7200, 0, 0, NULL, NULL, NULL, 60, 60, 0, 2,
   '2026-09-10 10:00:00', '2026-12-31 22:00:00', 'DAYS_AFTER_PURCHASE', NULL, NULL, 20,
   '[]', '[]', 0, NULL, 0, 1, 0, 'DRAFT', NULL, NULL, NULL, NULL, NULL, 0),
  (3102, 1, 'CASH', '50 元代金券', '商户草稿，可继续编辑', NULL, '[]',
   4200, 5000, 0, 0, 5000, 5000, NULL, 100, 100, 0, 1,
   '2026-09-10 10:00:00', '2026-12-31 22:00:00', 'DAYS_AFTER_PURCHASE', NULL, NULL, 30,
   '[]', '[]', 0, NULL, 0, 1, 1, 'DRAFT', NULL, NULL, NULL, NULL, NULL, 0),
  (3103, 1, 'DISCOUNT', '到店核销券', '商户草稿，可继续编辑', NULL, '[]',
   100, 100, 0, 0, NULL, NULL, NULL, 120, 120, 0, 1,
   '2026-09-10 10:00:00', '2026-12-31 22:00:00', 'DAYS_AFTER_PURCHASE', NULL, NULL, 15,
   '[]', '[]', 0, NULL, 0, 0, 0, 'DRAFT', NULL, NULL, NULL, NULL, NULL, 0),
  (3104, 1, 'MULTI_USE', '精品咖啡 5 次卡', '商户草稿，可继续编辑', NULL, '[]',
   12800, 15000, 0, 0, NULL, NULL, 5, 40, 40, 0, 1,
   '2026-09-10 10:00:00', '2026-12-31 22:00:00', 'DAYS_AFTER_PURCHASE', NULL, NULL, 60,
   '[]', '[]', 1, '使用前请预约座位', 0, 1, 0, 'DRAFT', NULL, NULL, NULL, NULL, NULL, 0),
  (3105, 1, 'PACKAGE', '秋日招牌双人餐', '阶段 21 券审核样例', 8201, '[]',
   8800, 11800, 0, 0, NULL, NULL, NULL, 100, 100, 0, 2,
   '2026-09-10 10:00:00', '2026-12-31 22:00:00', 'DAYS_AFTER_PURCHASE', NULL, NULL, 30,
   '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"10:00","close":"21:30"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"10:00","close":"21:30"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"10:00","close":"21:30"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"10:00","close":"21:30"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"SUNDAY","closed":false,"periods":[{"open":"10:00","close":"21:30"}]}]',
   '["2026-10-01"]', 1, '周末请提前两小时预约', 0, 1, 1, 'PENDING', NULL, NULL,
   'stage20-seed-pending', 'fa0bb3e35f8801224c57ed87a1e1f44b1d73db24a40c73653ecb27eeebac0d02',
   '2026-09-04 16:00:00', 1);

-- 消费者可购买的另外两种券，以及管理端可筛选的审核/销售状态样例。
INSERT INTO `voucher_product`
  (`id`, `shop_id`, `product_type`, `title`, `sub_title`, `cover_media_id`, `detail_media_ids_json`,
   `price_amount`, `market_amount`, `merchant_subsidy_amount`, `platform_discount_amount`, `face_value_amount`, `minimum_spend_amount`, `total_use_count`, `total_stock`, `available_stock`, `sold_count`,
   `purchase_limit`, `sale_begin_time`, `sale_end_time`, `validity_type`, `valid_begin_time`,
   `valid_end_time`, `valid_days`, `usage_rules_json`, `excluded_dates_json`, `reservation_required`,
   `reservation_notice`, `stackable`, `refund_anytime`, `refund_expired`, `review_status`,
   `sale_status`, `rejection_reason`, `submission_idempotency_key`, `submission_request_fingerprint`,
   `submitted_at`, `review_decision`, `review_idempotency_key`, `review_request_fingerprint`,
   `reviewed_at`, `reviewer_admin_id`, `version`)
VALUES
  (3003, 1, 'DISCOUNT', '八折折扣券', '商户填写适用范围与使用时段', NULL, '[]',
   1000, 1000, 50, 50, NULL, NULL, NULL, 100, 91, 9, 5,
   '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'DAYS_AFTER_PURCHASE', NULL, NULL, 30,
   '[]', '[]', 0, NULL, 0, 1, 1, 'APPROVED', 'ON_SALE', NULL,
   'seed-product-3003-submit', REPEAT('3', 64), '2026-08-01 09:00:00', 'APPROVAL',
   'seed-product-3003-approve', REPEAT('a', 64), '2026-08-01 10:00:00', 2, 2),
  (3004, 1, 'MULTI_USE', '精品咖啡 5 次卡', '核销两次后仍可继续使用', NULL, '[]',
   12800, 15000, 800, 200, NULL, NULL, 5, 50, 49, 1, 2,
   '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'DAYS_AFTER_PURCHASE', NULL, NULL, 60,
   '[]', '[]', 1, '使用前请预约座位', 0, 1, 0, 'APPROVED', 'ON_SALE', NULL,
   'seed-product-3004-submit', REPEAT('4', 64), '2026-08-01 09:10:00', 'APPROVAL',
   'seed-product-3004-approve', REPEAT('b', 64), '2026-08-01 10:10:00', 2, 2),
  (3106, 1, 'CASH', '资料不完整的代金券', '管理端驳回历史样例', NULL, '[]',
   3600, 5000, 0, 0, 5000, 5000, NULL, 30, 30, 0, 1,
   '2026-09-01 00:00:00', '2026-12-31 23:59:59', 'DAYS_AFTER_PURCHASE', NULL, NULL, 30,
   '[]', '[]', 0, NULL, 0, 1, 1, 'REJECTED', NULL, '使用规则说明不完整',
   'seed-product-3106-submit', REPEAT('5', 64), '2026-09-02 09:00:00', 'REJECTION',
   'seed-product-3106-reject', REPEAT('c', 64), '2026-09-02 10:00:00', 2, 2),
  (3107, 1, 'CASH', '国庆预约代金券', '已审核，等待开售', NULL, '[]',
   8800, 10000, 0, 0, 10000, 10000, NULL, 50, 50, 0, 2,
   '2026-10-01 00:00:00', '2026-12-31 23:59:59', 'DAYS_AFTER_PURCHASE', NULL, NULL, 30,
   '[]', '[]', 0, NULL, 0, 1, 1, 'APPROVED', 'SCHEDULED', NULL,
   'seed-product-3107-submit', REPEAT('6', 64), '2026-09-02 11:00:00', 'APPROVAL',
   'seed-product-3107-approve', REPEAT('d', 64), '2026-09-02 12:00:00', 2, 2),
  (3108, 1, 'CASH', '商户主动下架代金券', '已审核，当前下架', NULL, '[]',
   7600, 10000, 0, 0, 10000, 10000, NULL, 50, 48, 2, 2,
   '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'DAYS_AFTER_PURCHASE', NULL, NULL, 30,
   '[]', '[]', 0, NULL, 0, 1, 1, 'APPROVED', 'OFF_SALE', NULL,
   'seed-product-3108-submit', REPEAT('7', 64), '2026-08-01 11:00:00', 'APPROVAL',
   'seed-product-3108-approve', REPEAT('e', 64), '2026-08-01 12:00:00', 2, 2),
  (3109, 1, 'CASH', '限量售罄代金券', '售罄状态展示样例', NULL, '[]',
   5000, 6000, 0, 0, 6000, 6000, NULL, 20, 0, 20, 1,
   '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'DAYS_AFTER_PURCHASE', NULL, NULL, 15,
   '[]', '[]', 0, NULL, 0, 0, 0, 'APPROVED', 'SOLD_OUT', NULL,
   'seed-product-3109-submit', REPEAT('8', 64), '2026-08-01 13:00:00', 'APPROVAL',
   'seed-product-3109-approve', REPEAT('f', 64), '2026-08-01 14:00:00', 2, 2),
  (3110, 1, 'CASH', '暑期已结束代金券', '销售周期结束样例', NULL, '[]',
   6600, 8000, 0, 0, 8000, 8000, NULL, 40, 34, 6, 1,
   '2026-07-01 00:00:00', '2026-08-31 23:59:59', 'DAYS_AFTER_PURCHASE', NULL, NULL, 30,
   '[]', '[]', 0, NULL, 0, 0, 0, 'APPROVED', 'ENDED', NULL,
   'seed-product-3110-submit', REPEAT('9', 64), '2026-06-20 09:00:00', 'APPROVAL',
   'seed-product-3110-approve', REPEAT('0', 64), '2026-06-20 10:00:00', 2, 2);

INSERT INTO `voucher_package_item`
  (`id`, `product_id`, `name`, `quantity`, `unit`, `unit_price_amount`, `sort_order`)
VALUES
  (1, 3002, '手冲咖啡', 2, '杯', 3000, 0),
  (2, 3002, '当日甜点', 2, '份', 1400, 1),
  (3, 3101, '港式奶茶', 2, '杯', 1800, 0),
  (4, 3101, '菠萝油', 2, '份', 1800, 1),
  (5, 3104, '精品咖啡任选', 1, '杯/次', 3000, 0),
  (6, 3105, '招牌主食', 2, '份', 3800, 0),
  (7, 3105, '手作饮品', 2, '杯', 2100, 1),
  (8, 3004, '精品咖啡任选', 1, '杯/次', 3000, 0);

INSERT INTO `voucher_product_detail`
  (`product_id`, `section_type`, `title`, `content`, `sort_order`)
VALUES
  (3001, 'BENEFIT', '券面权益', '到店可抵扣门市消费，具体以门店结算规则为准', 0),
  (3002, 'PACKAGE_CONTENT', '套餐包含', '手冲咖啡 2 杯；当日甜点 2 份', 0),
  (3003, 'USAGE_RULE', '使用说明', '到店出示券码核销，具体适用范围以商户说明为准', 0),
  (3004, 'PACKAGE_CONTENT', '服务内容', '精品咖啡任选 5 次', 0);

INSERT INTO `voucher_product_tag`
  (`product_id`, `text`, `icon_key`, `color_token`, `sort_order`)
VALUES
  (3001, '100元代金券', 'coupon', 'pink', 0),
  (3002, '双人套餐', 'coupon', 'pink', 0),
  (3003, '八折折扣券', 'coupon', 'pink', 0),
  (3004, '5次卡', 'refresh', 'pink', 0);

INSERT INTO `voucher_product_cash_rule`
  (`product_id`, `face_value_amount`, `minimum_spend_amount`, `description`)
VALUES (3001, 10000, 10000, '每张券限抵扣一单');

INSERT INTO `voucher_product_discount_rule`
  (`product_id`, `discount_text`, `applicable_scope`, `usage_period_text`, `description`)
VALUES (3003, '八折折扣券', '堂食及指定饮品', '周一至周日 10:00-22:00', '不可与其他优惠叠加');

INSERT INTO `voucher_product_multi_use_rule`
  (`product_id`, `total_use_count`, `use_unit`, `description`)
VALUES (3004, 5, '次', '每次核销一份咖啡');

INSERT INTO `voucher_order`
  (`id`, `user_id`, `product_id`, `shop_id`, `product_title`, `unit_price`, `quantity`, `total_amount`,
   `merchant_subsidy_amount`, `platform_discount_amount`, `pay_amount`, `order_source`, `deal_channel`,
   `pay_type`, `status`, `payment_expire_time`, `idempotency_key`, `request_fingerprint`, `create_time`, `pay_time`, `update_time`)
VALUES
  (6001, 1, 3001, 1, '103 茶餐厅 100 元代金券', 8000, 1, 8000, 500, 300, 7200, 'ROAMLY', 'DIRECT', 3, 'PENDING_PAYMENT', '2027-12-31 23:59:59', 'seed-order-6001', REPEAT('0', 64), '2026-09-04 09:00:00', NULL, '2026-09-04 09:00:00'),
  (6002, 2, 3001, 1, '103 茶餐厅 100 元代金券', 8000, 1, 8000, 500, 300, 7200, 'ROAMLY', 'DIRECT', 3, 'PAID', NULL, 'seed-order-6002', REPEAT('1', 64), '2026-09-03 10:00:00', '2026-09-03 10:02:00', '2026-09-03 10:02:00'),
  (6003, 3, 3001, 1, '103 茶餐厅 100 元代金券', 8000, 1, 8000, 500, 300, 7200, 'ROAMLY', 'DIRECT', 3, 'CANCELED', NULL, 'seed-order-6003', REPEAT('2', 64), '2026-09-03 11:00:00', NULL, '2026-09-03 11:05:00'),
  (6004, 3, 3003, 1, '全场 85 折体验券', 1000, 1, 1000, 50, 50, 900, 'ROAMLY', 'DIRECT', 3, 'PAID', NULL, 'seed-order-6004', REPEAT('3', 64), '2026-09-03 12:00:00', '2026-09-03 12:01:00', '2026-09-03 12:01:00'),
  (6005, 3, 3004, 1, '精品咖啡 5 次卡', 12800, 1, 12800, 800, 200, 11800, 'ROAMLY', 'DIRECT', 3, 'PAID', NULL, 'seed-order-6005', REPEAT('4', 64), '2026-09-03 13:00:00', '2026-09-03 13:01:00', '2026-09-04 11:00:00'),
  (6006, 3, 3003, 1, '全场 85 折体验券', 1000, 1, 1000, 50, 50, 900, 'ROAMLY', 'DIRECT', 3, 'PAID', NULL, 'seed-order-6006', REPEAT('5', 64), '2026-09-03 14:00:00', '2026-09-03 14:01:00', '2026-09-04 12:00:00'),
  (6007, 3, 3003, 1, '全场 85 折体验券', 1000, 1, 1000, 50, 50, 900, 'ROAMLY', 'DIRECT', 3, 'REFUNDED', NULL, 'seed-order-6007', REPEAT('6', 64), '2026-09-03 15:00:00', '2026-09-03 15:01:00', '2026-09-04 13:00:00'),
  (6008, 3, 3003, 1, '全场 85 折体验券', 1000, 1, 1000, 50, 50, 900, 'ROAMLY', 'DIRECT', 3, 'REFUNDING', NULL, 'seed-order-6008', REPEAT('7', 64), '2026-09-03 16:00:00', '2026-09-03 16:01:00', '2026-09-04 14:00:00'),
  (6009, 3, 3003, 1, '全场 85 折体验券', 1000, 1, 1000, 50, 50, 900, 'ROAMLY', 'DIRECT', 3, 'REFUNDING', NULL, 'seed-order-6009', REPEAT('8', 64), '2026-09-03 17:00:00', '2026-09-03 17:01:00', '2026-09-04 15:00:00'),
  (6010, 3, 3003, 1, '全场 85 折体验券', 1000, 1, 1000, 50, 50, 900, 'ROAMLY', 'DIRECT', 3, 'REFUNDING', NULL, 'seed-order-6010', REPEAT('9', 64), '2026-09-03 18:00:00', '2026-09-03 18:01:00', '2026-09-04 16:00:00'),
  (6011, 3, 3003, 1, '全场 85 折体验券', 1000, 1, 1000, 50, 50, 900, 'ROAMLY', 'DIRECT', 3, 'PAID', NULL, 'seed-order-6011', REPEAT('a', 64), '2026-09-03 19:00:00', '2026-09-03 19:01:00', '2026-09-04 17:00:00'),
  (6012, 2, 3002, 2, '漫游咖啡双人套餐', 6800, 1, 6800, 300, 200, 6300, 'ROAMLY', 'DIRECT', 3, 'PAID', NULL, 'seed-order-6012', REPEAT('b', 64), '2026-09-03 20:00:00', '2026-09-03 20:01:00', '2026-09-04 18:00:00'),
  (6013, 3, 3003, 1, '全场 85 折体验券', 1000, 1, 1000, 50, 50, 900, 'ROAMLY', 'DIRECT', 3, 'PAID', NULL, 'seed-order-6013', REPEAT('c', 64), '2026-07-01 09:00:00', '2026-07-01 09:01:00', '2026-08-01 09:01:00'),
  (6014, 3, 3003, 1, '全场 85 折体验券', 1000, 1, 1000, 50, 50, 900, 'ROAMLY', 'DIRECT', 3, 'PAID', NULL, 'seed-order-6014', REPEAT('d', 64), '2026-09-04 09:00:00', '2026-09-04 09:01:00', '2026-09-04 19:00:00');

UPDATE `voucher_order` SET `use_time` = '2026-09-04 12:00:00' WHERE `id` = 6006;
UPDATE `voucher_order` SET `use_time` = '2026-09-04 18:00:00' WHERE `id` = 6012;
UPDATE `voucher_order` SET `refund_time` = '2026-09-04 13:00:00' WHERE `id` = 6007;

INSERT INTO `payment_transaction`
  (`id`, `order_id`, `user_id`, `idempotency_key`, `provider`, `status`, `amount`, `failure_reason`, `created_time`, `updated_time`)
VALUES
  (80001, 6001, 1, 'seed-payment-6001', 'MOCK', 'PENDING', 7200, NULL, '2026-09-04 09:01:00', '2026-09-04 09:01:00'),
  (80002, 6003, 3, 'seed-payment-6003', 'MOCK', 'CLOSED', 8000, '订单已取消', '2026-09-03 11:01:00', '2026-09-03 11:05:00'),
  (80003, 6002, 2, 'seed-payment-6002', 'MOCK', 'SUCCEEDED', 7200, NULL, '2026-09-03 10:01:00', '2026-09-03 10:02:00'),
  (80004, 6004, 3, 'seed-payment-6004-failed', 'MOCK', 'FAILED', 900, '模拟渠道首次失败', '2026-09-03 12:00:30', '2026-09-03 12:00:30'),
  (80005, 6004, 3, 'seed-payment-6004-success', 'MOCK', 'SUCCEEDED', 900, NULL, '2026-09-03 12:01:00', '2026-09-03 12:01:00'),
  (80006, 6005, 3, 'seed-payment-6005', 'MOCK', 'SUCCEEDED', 11800, NULL, '2026-09-03 13:01:00', '2026-09-03 13:01:00'),
  (80007, 6006, 3, 'seed-payment-6006', 'MOCK', 'SUCCEEDED', 900, NULL, '2026-09-03 14:01:00', '2026-09-03 14:01:00'),
  (80008, 6007, 3, 'seed-payment-6007', 'MOCK', 'REFUNDED', 900, NULL, '2026-09-03 15:01:00', '2026-09-04 13:00:00'),
  (80009, 6008, 3, 'seed-payment-6008', 'MOCK', 'PARTIALLY_REFUNDED', 900, NULL, '2026-09-03 16:01:00', '2026-09-04 14:00:00'),
  (80010, 6009, 3, 'seed-payment-6009', 'MOCK', 'SUCCEEDED', 900, NULL, '2026-09-03 17:01:00', '2026-09-03 17:01:00'),
  (80011, 6010, 3, 'seed-payment-6010', 'MOCK', 'SUCCEEDED', 900, NULL, '2026-09-03 18:01:00', '2026-09-03 18:01:00'),
  (80012, 6011, 3, 'seed-payment-6011', 'MOCK', 'SUCCEEDED', 900, NULL, '2026-09-03 19:01:00', '2026-09-03 19:01:00'),
  (80013, 6012, 2, 'seed-payment-6012', 'MOCK', 'SUCCEEDED', 6300, NULL, '2026-09-03 20:01:00', '2026-09-03 20:01:00'),
  (80014, 6013, 3, 'seed-payment-6013', 'MOCK', 'SUCCEEDED', 900, NULL, '2026-07-01 09:01:00', '2026-07-01 09:01:00'),
  (80015, 6014, 3, 'seed-payment-6014', 'MOCK', 'SUCCEEDED', 900, NULL, '2026-09-04 09:01:00', '2026-09-04 09:01:00');

INSERT INTO `user_voucher`
  (`id`, `user_id`, `order_id`, `sequence_no`, `product_id`, `shop_id`, `voucher_code`, `voucher_code_hmac`, `voucher_code_last4`,
   `total_use_count`, `remaining_use_count`, `sale_amount`, `merchant_subsidy_amount`, `platform_discount_amount`, `customer_paid_amount`,
   `status`, `valid_begin_time`, `expire_time`, `create_time`, `update_time`)
VALUES
  (7001, 2, 6002, 1, 3001, 1, '123456789012', SHA2('123456789012',256), '9012', 1, 1, 8000, 500, 300, 7200, 'UNUSED', '2026-09-03 10:02:00', '2026-10-03 10:02:00', '2026-09-03 10:02:00', '2026-09-03 10:02:00'),
  (7002, 3, 6004, 1, 3003, 1, '400000007002', SHA2('400000007002',256), '7002', 1, 1, 1000, 50, 50, 900, 'UNUSED', '2026-09-03 12:01:00', '2026-10-03 12:01:00', '2026-09-03 12:01:00', '2026-09-03 12:01:00'),
  (7003, 3, 6005, 1, 3004, 1, '400000007003', SHA2('400000007003',256), '7003', 5, 3, 12800, 800, 200, 11800, 'PARTIALLY_USED', '2026-09-03 13:01:00', '2026-11-02 13:01:00', '2026-09-03 13:01:00', '2026-09-04 11:00:00'),
  (7004, 3, 6006, 1, 3003, 1, '400000007004', SHA2('400000007004',256), '7004', 1, 0, 1000, 50, 50, 900, 'USED', '2026-09-03 14:01:00', '2026-10-03 14:01:00', '2026-09-03 14:01:00', '2026-09-04 12:00:00'),
  (7005, 3, 6007, 1, 3003, 1, '400000007005', SHA2('400000007005',256), '7005', 1, 1, 1000, 50, 50, 900, 'REFUNDED', '2026-09-03 15:01:00', '2026-10-03 15:01:00', '2026-09-03 15:01:00', '2026-09-04 13:00:00'),
  (7006, 3, 6008, 1, 3003, 1, '400000007006', SHA2('400000007006',256), '7006', 1, 1, 1000, 50, 50, 900, 'REFUNDING', '2026-09-03 16:01:00', '2026-10-03 16:01:00', '2026-09-03 16:01:00', '2026-09-04 14:00:00'),
  (7007, 3, 6009, 1, 3003, 1, '400000007007', SHA2('400000007007',256), '7007', 1, 1, 1000, 50, 50, 900, 'REFUNDING', '2026-09-03 17:01:00', '2026-10-03 17:01:00', '2026-09-03 17:01:00', '2026-09-04 15:00:00'),
  (7008, 3, 6010, 1, 3003, 1, '400000007008', SHA2('400000007008',256), '7008', 1, 1, 1000, 50, 50, 900, 'REFUNDING', '2026-09-03 18:01:00', '2026-10-03 18:01:00', '2026-09-03 18:01:00', '2026-09-04 16:00:00'),
  (7009, 3, 6011, 1, 3003, 1, '400000007009', SHA2('400000007009',256), '7009', 1, 1, 1000, 50, 50, 900, 'UNUSED', '2026-09-03 19:01:00', '2026-10-03 19:01:00', '2026-09-03 19:01:00', '2026-09-04 17:00:00'),
  (7010, 2, 6012, 1, 3002, 2, '400000007010', SHA2('400000007010',256), '7010', 1, 0, 6800, 300, 200, 6300, 'USED', '2026-09-03 20:01:00', '2026-09-18 20:01:00', '2026-09-03 20:01:00', '2026-09-04 18:00:00'),
  (7011, 3, 6013, 1, 3003, 1, '400000007011', SHA2('400000007011',256), '7011', 1, 1, 1000, 50, 50, 900, 'EXPIRED', '2026-07-01 09:01:00', '2026-07-31 09:01:00', '2026-07-01 09:01:00', '2026-08-01 09:01:00'),
  (7012, 3, 6014, 1, 3003, 1, '400000007012', SHA2('400000007012',256), '7012', 1, 1, 1000, 50, 50, 900, 'UNUSED', '2026-09-04 09:01:00', '2026-10-04 09:01:00', '2026-09-04 09:01:00', '2026-09-04 19:00:00');

UPDATE `user_voucher` SET `use_time` = '2026-09-04 12:00:00' WHERE `id` = 7004;
UPDATE `user_voucher` SET `refund_time` = '2026-09-04 13:00:00' WHERE `id` = 7005;
UPDATE `user_voucher` SET `use_time` = '2026-09-04 18:00:00' WHERE `id` = 7010;

INSERT INTO `user_voucher_qr_code`
  (`voucher_id`, `user_id`, `token_key`, `token_version`, `expire_time`, `create_time`, `update_time`)
VALUES
  (7001, 2, '0123456789abcdef0123456789abcdef', 1, '2026-10-03 10:02:00', '2026-09-03 10:02:00', '2026-09-03 10:02:00');

INSERT INTO `voucher_refund`
  (`id`, `voucher_id`, `order_id`, `user_id`, `amount`, `status`, `reason`, `idempotency_key`,
   `requested_time`, `processed_time`, `created_time`, `updated_time`)
VALUES
  (9101, 7005, 6007, 3, 1000, 'SUCCEEDED', '消费者主动退款', 'seed-refund-9101', '2026-09-04 12:55:00', '2026-09-04 13:00:00', '2026-09-04 12:55:00', '2026-09-04 13:00:00'),
  (9102, 7006, 6008, 3, 1000, 'REQUESTED', '等待财务审核', 'seed-refund-9102', '2026-09-04 14:00:00', NULL, '2026-09-04 14:00:00', '2026-09-04 14:00:00'),
  (9103, 7007, 6009, 3, 1000, 'PROCESSING', '支付渠道处理中', 'seed-refund-9103', '2026-09-04 15:00:00', NULL, '2026-09-04 15:00:00', '2026-09-04 15:05:00'),
  (9104, 7008, 6010, 3, 1000, 'FAILED', '模拟渠道暂时不可用', 'seed-refund-9104', '2026-09-04 16:00:00', '2026-09-04 16:05:00', '2026-09-04 16:00:00', '2026-09-04 16:05:00'),
  (9105, 7009, 6011, 3, 1000, 'REJECTED', '该券不符合异常退款条件', 'seed-refund-9105', '2026-09-04 17:00:00', '2026-09-04 17:05:00', '2026-09-04 17:00:00', '2026-09-04 17:05:00');

INSERT INTO `voucher_redemption`
  (`id`, `voucher_id`, `order_id`, `product_id`, `shop_id`, `merchant_account_id`,
   `product_title`, `product_cover`, `shop_name`, `operator_name`, `redemption_method`, `merchant_note`, `use_count`,
   `status`, `sale_amount`, `merchant_subsidy_amount`, `platform_discount_amount`, `customer_paid_amount`,
   `service_fee_base_amount`, `service_fee_rate_bps`, `service_fee_amount`, `estimated_income_amount`,
   `idempotency_key`, `reversal_reason`, `reversed_by_account_id`, `redeemed_time`, `reversed_time`)
VALUES
  (9201, 7003, 6005, 3004, 1, 32, '精品咖啡 5 次卡', NULL, '103 茶餐厅', '茶餐厅核销员', 'MANUAL_CODE', NULL, 1,
   'SUCCEEDED', 2560, 160, 40, 2360, 2400, 650, 166, 2194, 'seed-redemption-9201', NULL, NULL, '2026-09-04 10:00:00', NULL),
  (9202, 7003, 6005, 3004, 1, 32, '精品咖啡 5 次卡', NULL, '103 茶餐厅', '茶餐厅核销员', 'QR_CODE', '午间到店核销', 1,
   'SUCCEEDED', 2560, 160, 40, 2360, 2400, 650, 166, 2194, 'seed-redemption-9202', NULL, NULL, '2026-09-04 11:00:00', NULL),
  (9203, 7004, 6006, 3003, 1, 31, '八折折扣券', NULL, '103 茶餐厅', '茶餐厅店长', 'MANUAL_CODE', NULL, 1,
   'SUCCEEDED', 1000, 50, 50, 900, 950, 650, 61, 839, 'seed-redemption-9203', NULL, NULL, '2026-09-04 12:00:00', NULL),
  (9204, 7010, 6012, 3002, 2, 6, '漫游咖啡双人套餐', NULL, '漫游咖啡实验室', '放映厅租户', 'QR_CODE', NULL, 1,
   'SUCCEEDED', 6800, 300, 200, 6300, 6500, 500, 325, 5975, 'seed-redemption-9204', NULL, NULL, '2026-09-04 18:00:00', NULL),
  (9205, 7012, 6014, 3003, 1, 31, '八折折扣券', NULL, '103 茶餐厅', '茶餐厅店长', 'MANUAL_CODE', NULL, 1,
   'REVERSED', 1000, 50, 50, 900, 950, 650, 61, 839, 'seed-redemption-9205', '顾客与门店确认撤销', 1, '2026-09-04 18:30:00', '2026-09-04 19:00:00');

INSERT INTO `commission_rule`
  (`id`, `shop_id`, `rate_bps`, `effective_from`, `effective_to`, `version`)
VALUES
  (100001, NULL, 500, '2026-09-01 00:00:00', NULL, 0),
  (100002, 1, 650, '2026-08-15 00:00:00', NULL, 0);

INSERT INTO `fund_ledger_entry`
  (`id`, `shop_id`, `order_id`, `voucher_id`, `business_event_id`, `entry_type`, `account_side`,
   `amount`, `commission_rate_bps`, `service_fee_base_amount`, `occurred_time`)
VALUES
  (110001, 1, 6002, NULL, 'ORDER-6002', 'PAYMENT_FROZEN', 'CREDIT', 7200, NULL, NULL, '2026-09-03 10:02:00'),
  (110002, 2, 6012, 7010, 'REDEMPTION-9204', 'REDEMPTION_RECOGNIZED', 'CREDIT', 6300, 500, 6500, '2026-09-04 18:00:00'),
  (110003, 2, 6012, 7010, 'REDEMPTION-9204', 'SERVICE_FEE_RECOGNIZED', 'DEBIT', 325, 500, 6500, '2026-09-04 18:00:00'),
  (110004, 1, 6007, 7005, 'REFUND-9101', 'REFUND_REVERSED', 'DEBIT', -900, NULL, NULL, '2026-09-04 13:00:00'),
  (110005, 1, 6006, 7004, 'REDEMPTION-9203', 'REDEMPTION_RECOGNIZED', 'CREDIT', 900, 650, 950, '2026-09-04 12:00:00'),
  (110006, 1, 6006, 7004, 'REDEMPTION-9203', 'SERVICE_FEE_RECOGNIZED', 'DEBIT', 61, 650, 950, '2026-09-04 12:00:00'),
  (110007, 1, 6014, 7012, 'REDEMPTION-REVERSAL-9205', 'REDEMPTION_REVERSED', 'DEBIT', -900, 650, 950, '2026-09-04 19:00:00'),
  (110010, 1, 6014, 7012, 'REDEMPTION-REVERSAL-9205', 'SERVICE_FEE_REVERSED', 'CREDIT', 61, 650, 950, '2026-09-04 19:00:00'),
  (110011, 1, 6005, 7003, 'REDEMPTION-9201', 'REDEMPTION_RECOGNIZED', 'CREDIT', 2360, 650, 2400, '2026-09-04 10:00:00'),
  (110012, 1, 6005, 7003, 'REDEMPTION-9201', 'SERVICE_FEE_RECOGNIZED', 'DEBIT', 166, 650, 2400, '2026-09-04 10:00:00'),
  (110013, 1, 6005, 7003, 'REDEMPTION-9202', 'REDEMPTION_RECOGNIZED', 'CREDIT', 2360, 650, 2400, '2026-09-04 11:00:00'),
  (110014, 1, 6005, 7003, 'REDEMPTION-9202', 'SERVICE_FEE_RECOGNIZED', 'DEBIT', 166, 650, 2400, '2026-09-04 11:00:00'),
  (110008, 1, NULL, NULL, 'SETTLEMENT-120001', 'SETTLEMENT_POSTED', 'DEBIT', 839, 650, 950, '2026-09-05 02:00:00'),
  (110009, 1, 6007, 7005, 'SETTLEMENT-ADJUSTMENT-9101', 'SETTLEMENT_ADJUSTMENT', 'DEBIT', -900, NULL, NULL, '2026-09-05 02:05:00');

INSERT INTO `settlement_batch`
  (`id`, `shop_id`, `settlement_date`, `status`, `total_amount`, `failure_reason`, `version`, `processed_time`)
VALUES
  (120001, 1, '2026-09-03', 'SUCCEEDED', 935, NULL, 0, '2026-09-04 02:00:00'),
  (120002, 1, '2026-09-04', 'FAILED', -935, '模拟结算渠道不可用，可在管理端重试', 0, '2026-09-05 02:00:00'),
  (120003, 2, '2026-09-04', 'PROCESSING', 6460, NULL, 0, NULL),
  (120004, 3, '2026-09-05', 'SUCCEEDED', 4864, NULL, 0, '2026-09-06 02:00:00');

INSERT INTO `settlement_item` (`id`, `batch_id`, `ledger_entry_id`, `amount`) VALUES
  (130001, 120001, 110005, 1000),
  (130002, 120001, 110006, -65),
  (130003, 120003, 110002, 6800),
  (130004, 120003, 110003, -340),
  (130009, 120002, 110007, -1000),
  (130010, 120002, 110010, 65),
  (130005, 120004, 110011, 2560),
  (130006, 120004, 110012, -128),
  (130007, 120004, 110013, 2560),
  (130008, 120004, 110014, -128);

INSERT INTO `operation_audit_log`
  (`id`, `actor_type`, `actor_id`, `action`, `object_type`, `object_id`, `result`, `reason`, `trace_id`, `create_time`)
VALUES
  (140001, 'ADMIN', 1, 'ADMIN_LOGIN', 'ADMIN_USER', '1', 'SUCCEEDED', NULL, 'seed-trace-admin-login', '2026-09-04 08:00:00'),
  (140002, 'ADMIN', 2, 'MERCHANT_APPLICATION_APPROVED', 'MERCHANT_APPLICATION', '9101', 'SUCCEEDED', NULL, 'seed-trace-application', '2026-08-01 10:00:00'),
  (140003, 'ADMIN', 2, 'VOUCHER_REVIEW_APPROVED', 'VOUCHER_PRODUCT', '3003', 'SUCCEEDED', NULL, 'seed-trace-voucher-review', '2026-08-01 10:00:00'),
  (140004, 'MERCHANT', 1, 'MERCHANT_LOGIN', 'MERCHANT_ACCOUNT', '1', 'SUCCEEDED', NULL, 'seed-trace-merchant-login', '2026-09-04 08:10:00'),
  (140005, 'MERCHANT', 32, 'VOUCHER_REDEEMED', 'USER_VOUCHER', '7004', 'SUCCEEDED', NULL, 'seed-trace-redemption', '2026-09-04 12:00:00'),
  (140006, 'MERCHANT', 1, 'REDEMPTION_REVERSED', 'VOUCHER_REDEMPTION', '9205', 'SUCCEEDED', '顾客与门店确认撤销', 'seed-trace-reversal', '2026-09-04 19:00:00'),
  (140007, 'SYSTEM', NULL, 'REFUND_UPDATED', 'VOUCHER_REFUND', '9101', 'SUCCEEDED', NULL, 'seed-trace-refund', '2026-09-04 13:00:00'),
  (140008, 'SYSTEM', NULL, 'SETTLEMENT_GENERATED', 'SETTLEMENT_BATCH', '120001', 'SUCCEEDED', NULL, 'seed-trace-settlement', '2026-09-04 02:00:00'),
  (140009, 'SYSTEM', NULL, 'SETTLEMENT_GENERATED', 'SETTLEMENT_BATCH', '120002', 'FAILED', '模拟结算渠道不可用', 'seed-trace-settlement-failed', '2026-09-05 02:00:00');

INSERT INTO `customer_service_ticket`
  (`id`, `ticket_no`, `type`, `status`, `priority`, `user_id`, `shop_id`, `order_id`, `voucher_id`, `refund_id`, `subject`, `description`, `created_by_type`, `created_by_id`, `first_response_time`, `last_message_time`)
VALUES
  (150001, 'CS202609040001', 'REFUND', 'OPEN', 'HIGH', 3, 1, 6008, 7006, 9102, '退款进度咨询', '支付渠道处理中，用户咨询预计到账时间', 'CONSUMER', 3, '2026-09-04 14:10:00', '2026-09-04 14:10:00'),
  (150002, 'CS202609040002', 'REDEMPTION', 'WAITING_INTERNAL', 'NORMAL', 2, 2, 6012, 7010, NULL, '核销后金额确认', '请客服协助确认服务费扣除规则', 'MERCHANT', 6, '2026-09-04 18:20:00', '2026-09-04 18:20:00');

INSERT INTO `customer_service_message`
  (`id`, `ticket_id`, `sender_type`, `sender_id`, `visibility`, `message_type`, `content`)
VALUES
  (151001, 150001, 'CONSUMER', 3, 'PUBLIC', 'TEXT', '我的退款什么时候到账？'),
  (151002, 150001, 'ADMIN', 5, 'PUBLIC', 'TEXT', '已为你查询，当前退款正在支付渠道处理中。'),
  (151003, 150001, 'ADMIN', 5, 'INTERNAL', 'TEXT', '明早跟进渠道结果，必要时重试。'),
  (151004, 150002, 'MERCHANT', 6, 'PUBLIC', 'TEXT', '请说明核销后预计收入的计算方式。');

INSERT INTO `customer_service_attachment`
  (`id`, `ticket_id`, `message_id`, `uploader_type`, `uploader_id`, `object_key`, `original_filename`, `mime_type`, `byte_size`)
VALUES
  (152001, 150001, 151001, 'CONSUMER', 3, 'seed/customer-service/150001-refund.png', '退款截图.png', 'image/png', 10240);

SET FOREIGN_KEY_CHECKS = 1;
