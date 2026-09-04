SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 此文件仅用于可重建的开发数据库，所有样例直接使用当前领域模型。
-- Demo 管理员：admin / Roamly123，首次登录后必须修改密码。
INSERT INTO `admin_user`
  (`id`, `username`, `password_hash`, `display_name`, `role`, `status`, `force_password_change`, `version`)
VALUES
  (1, 'admin', '$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO', 'Roamly 管理员', 'PLATFORM_ADMIN', 'ACTIVE', 1, 0);

INSERT INTO `city` (`id`, `code`, `name`, `status`, `sort`)
VALUES (1, '330100', '杭州', 1, 1);

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

INSERT INTO `user` (`id`, `phone`, `password`, `nick_name`, `icon`) VALUES
  (1, '13686869696', '', '小鱼同学', '/imgs/blogs/blog1.jpg'),
  (2, '13838411438', '', '可可今天不吃肉', '/imgs/icons/kkjtbcr.jpg'),
  (3, '13456789011', '', '漫游测试员', '');

INSERT INTO `user_info` (`user_id`, `city`, `city_code`, `introduce`) VALUES
  (1, '杭州', '330100', '记录城市里的新鲜事'),
  (2, '杭州', '330100', '认真生活，认真探店'),
  (3, '杭州', '330100', 'Roamly 开发测试账号');

INSERT INTO `shop`
  (`id`, `name`, `type_id`, `city_code`, `images`, `area`, `address`, `x`, `y`, `avg_price`, `sold`, `comments`, `score`, `open_hours`, `status`, `source_application_id`, `business_hours_json`, `activated_at`, `version`)
VALUES
  (1, '103 茶餐厅', 1, '330100', 'https://example.com/images/tea-restaurant.jpg', '大关', '金华路锦昌文华苑 29 号', 120.149192, 30.316078, 80, 1, 2, 45, '10:00-22:00', 'ACTIVE', 9101, '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"SUNDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]}]', '2026-08-01 10:00:00', 0),
  (2, '漫游咖啡实验室', 1, '330100', 'https://example.com/images/coffee-lab.jpg', '运河上街', '台州路 2 号', 120.151954, 30.324970, 68, 0, 1, 50, '09:00-21:00', 'ACTIVE', 9102, '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"SUNDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]}]', '2026-08-02 10:00:00', 0),
  (3, '周末放映厅', 2, '330100', 'https://example.com/images/cinema.jpg', '拱宸桥', '丽水路 58 号', 120.146659, 30.312742, 120, 0, 0, 0, '13:00-23:00', 'ACTIVE', 9103, '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]},{"dayOfWeek":"SUNDAY","closed":false,"periods":[{"open":"13:00","close":"23:00"}]}]', '2026-08-03 10:00:00', 0);

INSERT INTO `merchant_account`
  (`id`, `phone`, `nickname`, `avatar_url`, `role`, `status`, `shop_id`, `disabled_source`, `disabled_reason`, `disabled_at`, `version`)
VALUES
  (1, '13900000001', '茶餐厅店主', NULL, 'OWNER', 'ACTIVE', 1, NULL, NULL, NULL, 0),
  (2, '13900000002', '待入驻商户', NULL, 'OWNER', 'NOT_APPLIED', NULL, NULL, NULL, NULL, 0),
  (3, '13900000003', '审核中商户', NULL, 'OWNER', 'PENDING', NULL, NULL, NULL, NULL, 0),
  (4, '13900000004', '待修改商户', NULL, 'OWNER', 'REJECTED', NULL, NULL, NULL, NULL, 0),
  (5, '13900000005', '已停用商户', NULL, 'OWNER', 'DISABLED', 2, 'ACCOUNT_GOVERNANCE', '平台账号治理样例', '2026-09-01 09:00:00', 1),
  (6, '13900000006', '放映厅店主', NULL, 'OWNER', 'ACTIVE', 3, NULL, NULL, NULL, 0);

INSERT INTO `business_media_asset`
  (`id`, `uploader_merchant_account_id`, `purpose`, `status`, `bucket_name`, `object_key`, `original_filename`, `mime_type`, `byte_size`, `width`, `height`, `owner_type`, `owner_id`, `sort_order`, `bound_at`, `expires_at`)
VALUES
  (8001, 3, 'LICENSE', 'BOUND', 'roamly-business-local', 'seed/merchant/application-3-license.png', '营业执照.png', 'image/png', 543, 400, 400, 'MERCHANT_APPLICATION', 9001, 0, '2026-09-04 10:00:00', NULL),
  (8002, 4, 'LICENSE', 'BOUND', 'roamly-business-local', 'seed/merchant/application-4-license.png', '营业执照.png', 'image/png', 543, 400, 400, 'MERCHANT_APPLICATION', 9002, 0, '2026-09-03 10:00:00', NULL),
  (8101, 1, 'LICENSE', 'BOUND', 'roamly-business-local', 'seed/merchant/application-9101-license.png', '营业执照.png', 'image/png', 543, 400, 400, 'MERCHANT_APPLICATION', 9101, 0, '2026-08-01 10:00:00', NULL),
  (8102, 5, 'LICENSE', 'BOUND', 'roamly-business-local', 'seed/merchant/application-9102-license.png', '营业执照.png', 'image/png', 543, 400, 400, 'MERCHANT_APPLICATION', 9102, 0, '2026-08-02 10:00:00', NULL),
  (8103, 6, 'LICENSE', 'BOUND', 'roamly-business-local', 'seed/merchant/application-9103-license.png', '营业执照.png', 'image/png', 543, 400, 400, 'MERCHANT_APPLICATION', 9103, 0, '2026-08-03 10:00:00', NULL),
  (8201, 1, 'VOUCHER_COVER', 'BOUND', 'roamly-business-local', 'seed/merchant/voucher-3105-cover.png', '套餐券封面.png', 'image/png', 543, 400, 400, 'VOUCHER_PRODUCT', 3105, 0, '2026-09-04 16:00:00', NULL);

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
  (1003, 3, 4, 1, 3, '330100', '周末的电影和朋友', '适合和朋友一起放空半天的小地方。', 1, 0, 0, '2026-09-02 16:20:00', '2026-09-02 16:20:00');

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
  (4003, 2, 1, NULL, 5, '环境安静，双人套餐很适合聊天。', 0, '2026-09-03 15:00:00', '2026-09-03 15:00:00');

INSERT INTO `shop_review_media` (`id`, `review_id`, `media_asset_id`, `sort`, `create_time`)
VALUES (1, 4001, 502, 0, '2026-09-02 13:10:00');

INSERT INTO `voucher_product`
  (`id`, `shop_id`, `product_type`, `title`, `sub_title`, `cover_media_id`, `detail_media_ids_json`,
   `price_amount`, `market_amount`, `face_value_amount`, `minimum_spend_amount`, `discount_rate_bps`,
   `maximum_discount_amount`, `total_use_count`, `total_stock`, `available_stock`, `sold_count`,
   `purchase_limit`, `sale_begin_time`, `sale_end_time`, `validity_type`, `valid_begin_time`,
   `valid_end_time`, `valid_days`, `usage_rules_json`, `excluded_dates_json`, `reservation_required`,
   `reservation_notice`, `stackable`, `refund_anytime`, `refund_expired`, `review_status`,
   `sale_status`, `rejection_reason`, `submission_idempotency_key`, `submission_request_fingerprint`,
   `submitted_at`, `version`)
VALUES
  (3001, 1, 'CASH', '103 茶餐厅 100 元代金券', '工作日、周末通用', NULL, '[]',
   8000, 10000, 10000, 10000, NULL, NULL, NULL, 200, 198, 1, 1,
   '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'DAYS_AFTER_PURCHASE', NULL, NULL, 30,
   '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"SUNDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]}]',
   '[]', 0, NULL, 0, 1, 1, 'APPROVED', 'ON_SALE', NULL, NULL, NULL, NULL, 0),
  (3002, 2, 'PACKAGE', '漫游咖啡双人套餐', '限店内堂食使用', NULL, '[]',
   6800, 8800, NULL, NULL, NULL, NULL, NULL, 80, 80, 0, 1,
   '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'DAYS_AFTER_PURCHASE', NULL, NULL, 15,
   '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]},{"dayOfWeek":"SUNDAY","closed":false,"periods":[{"open":"09:00","close":"21:00"}]}]',
   '[]', 1, '请至少提前一小时预约', 0, 1, 0, 'APPROVED', 'ON_SALE', NULL, NULL, NULL, NULL, 0),
  (3101, 1, 'PACKAGE', '下午茶双人套餐', '商户草稿，可继续编辑', NULL, '[]',
   5200, 7200, NULL, NULL, NULL, NULL, NULL, 60, 60, 0, 2,
   '2026-09-10 10:00:00', '2026-12-31 22:00:00', 'DAYS_AFTER_PURCHASE', NULL, NULL, 20,
   '[]', '[]', 0, NULL, 0, 1, 0, 'DRAFT', NULL, NULL, NULL, NULL, NULL, 0),
  (3102, 1, 'CASH', '50 元代金券', '商户草稿，可继续编辑', NULL, '[]',
   4200, 5000, 5000, 5000, NULL, NULL, NULL, 100, 100, 0, 1,
   '2026-09-10 10:00:00', '2026-12-31 22:00:00', 'DAYS_AFTER_PURCHASE', NULL, NULL, 30,
   '[]', '[]', 0, NULL, 0, 1, 1, 'DRAFT', NULL, NULL, NULL, NULL, NULL, 0),
  (3103, 1, 'DISCOUNT', '全场 85 折券', '商户草稿，可继续编辑', NULL, '[]',
   100, 100, NULL, 10000, 8500, 3000, NULL, 120, 120, 0, 1,
   '2026-09-10 10:00:00', '2026-12-31 22:00:00', 'DAYS_AFTER_PURCHASE', NULL, NULL, 15,
   '[]', '[]', 0, NULL, 0, 0, 0, 'DRAFT', NULL, NULL, NULL, NULL, NULL, 0),
  (3104, 1, 'MULTI_USE', '精品咖啡 5 次卡', '商户草稿，可继续编辑', NULL, '[]',
   12800, 15000, NULL, NULL, NULL, NULL, 5, 40, 40, 0, 1,
   '2026-09-10 10:00:00', '2026-12-31 22:00:00', 'DAYS_AFTER_PURCHASE', NULL, NULL, 60,
   '[]', '[]', 1, '使用前请预约座位', 0, 1, 0, 'DRAFT', NULL, NULL, NULL, NULL, NULL, 0),
  (3105, 1, 'PACKAGE', '秋日招牌双人餐', '阶段 21 券审核样例', 8201, '[]',
   8800, 11800, NULL, NULL, NULL, NULL, NULL, 100, 100, 0, 2,
   '2026-09-10 10:00:00', '2026-12-31 22:00:00', 'DAYS_AFTER_PURCHASE', NULL, NULL, 30,
   '[{"dayOfWeek":"MONDAY","closed":false,"periods":[{"open":"10:00","close":"21:30"}]},{"dayOfWeek":"TUESDAY","closed":false,"periods":[{"open":"10:00","close":"21:30"}]},{"dayOfWeek":"WEDNESDAY","closed":false,"periods":[{"open":"10:00","close":"21:30"}]},{"dayOfWeek":"THURSDAY","closed":false,"periods":[{"open":"10:00","close":"21:30"}]},{"dayOfWeek":"FRIDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"SATURDAY","closed":false,"periods":[{"open":"10:00","close":"22:00"}]},{"dayOfWeek":"SUNDAY","closed":false,"periods":[{"open":"10:00","close":"21:30"}]}]',
   '["2026-10-01"]', 1, '周末请提前两小时预约', 0, 1, 1, 'PENDING', NULL, NULL,
   'stage20-seed-pending', 'fa0bb3e35f8801224c57ed87a1e1f44b1d73db24a40c73653ecb27eeebac0d02',
   '2026-09-04 16:00:00', 1);

INSERT INTO `voucher_package_item`
  (`id`, `product_id`, `name`, `quantity`, `unit`, `unit_price_amount`, `sort_order`)
VALUES
  (1, 3002, '手冲咖啡', 2, '杯', 3000, 0),
  (2, 3002, '当日甜点', 2, '份', 1400, 1),
  (3, 3101, '港式奶茶', 2, '杯', 1800, 0),
  (4, 3101, '菠萝油', 2, '份', 1800, 1),
  (5, 3104, '精品咖啡任选', 1, '杯/次', 3000, 0),
  (6, 3105, '招牌主食', 2, '份', 3800, 0),
  (7, 3105, '手作饮品', 2, '杯', 2100, 1);

INSERT INTO `voucher_order`
  (`id`, `user_id`, `product_id`, `shop_id`, `product_title`, `unit_price`, `quantity`, `total_amount`, `pay_amount`, `pay_type`, `status`, `payment_expire_time`, `idempotency_key`, `request_fingerprint`, `create_time`, `pay_time`, `update_time`)
VALUES
  (6001, 1, 3001, 1, '103 茶餐厅 100 元代金券', 8000, 1, 8000, 8000, 3, 'PENDING_PAYMENT', '2026-09-04 09:15:00', 'seed-order-6001', REPEAT('0', 64), '2026-09-04 09:00:00', NULL, '2026-09-04 09:00:00'),
  (6002, 2, 3001, 1, '103 茶餐厅 100 元代金券', 8000, 1, 8000, 8000, 3, 'PAID', NULL, 'seed-order-6002', REPEAT('1', 64), '2026-09-03 10:00:00', '2026-09-03 10:02:00', '2026-09-03 10:02:00'),
  (6003, 3, 3001, 1, '103 茶餐厅 100 元代金券', 8000, 1, 8000, 8000, 3, 'CANCELED', NULL, 'seed-order-6003', REPEAT('2', 64), '2026-09-03 11:00:00', NULL, '2026-09-03 11:05:00');

INSERT INTO `user_voucher`
  (`id`, `user_id`, `order_id`, `product_id`, `shop_id`, `voucher_code`, `status`, `valid_begin_time`, `expire_time`, `create_time`, `update_time`)
VALUES
  (7001, 2, 6002, 3001, 1, 'ROAMLYDEMO20260903001', 'UNUSED', '2026-09-03 10:02:00', '2026-10-03 10:02:00', '2026-09-03 10:02:00', '2026-09-03 10:02:00');

SET FOREIGN_KEY_CHECKS = 1;
