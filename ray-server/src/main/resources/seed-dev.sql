SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 此文件仅用于可重建的开发数据库，所有样例直接使用当前领域模型。
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
  (`id`, `name`, `type_id`, `city_code`, `images`, `area`, `address`, `x`, `y`, `avg_price`, `sold`, `comments`, `score`, `open_hours`, `status`)
VALUES
  (1, '103 茶餐厅', 1, '330100', 'https://example.com/images/tea-restaurant.jpg', '大关', '金华路锦昌文华苑 29 号', 120.149192, 30.316078, 80, 1, 2, 45, '10:00-22:00', 1),
  (2, '漫游咖啡实验室', 1, '330100', 'https://example.com/images/coffee-lab.jpg', '运河上街', '台州路 2 号', 120.151954, 30.324970, 68, 0, 1, 50, '09:00-21:00', 1),
  (3, '周末放映厅', 2, '330100', 'https://example.com/images/cinema.jpg', '拱宸桥', '丽水路 58 号', 120.146659, 30.312742, 120, 0, 0, 0, '13:00-23:00', 1);

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
  (`id`, `shop_id`, `title`, `sub_title`, `cover`, `rules`, `pay_price`, `original_price`, `deduction_value`, `sale_type`, `total_stock`, `available_stock`, `sold_count`, `purchase_limit`, `validity_type`, `valid_days`, `status`)
VALUES
  (3001, 1, '103 茶餐厅 100 元代金券', '工作日、周末通用', NULL, '不可与店内其他优惠同享', 8000, 10000, 10000, 'NORMAL', 200, 198, 1, 1, 'DAYS_AFTER_PURCHASE', 30, 'ON_SALE'),
  (3002, 2, '漫游咖啡双人套餐', '限店内堂食使用', NULL, '请提前向商户确认可用时间', 6800, 8800, NULL, 'NORMAL', 80, 80, 0, 1, 'DAYS_AFTER_PURCHASE', 15, 'ON_SALE');

INSERT INTO `voucher_order`
  (`id`, `user_id`, `product_id`, `shop_id`, `product_title`, `unit_price`, `quantity`, `total_amount`, `pay_amount`, `pay_type`, `status`, `create_time`, `pay_time`, `update_time`)
VALUES
  (6001, 1, 3001, 1, '103 茶餐厅 100 元代金券', 8000, 1, 8000, 8000, 3, 1, '2026-09-04 09:00:00', NULL, '2026-09-04 09:00:00'),
  (6002, 2, 3001, 1, '103 茶餐厅 100 元代金券', 8000, 1, 8000, 8000, 3, 2, '2026-09-03 10:00:00', '2026-09-03 10:02:00', '2026-09-03 10:02:00'),
  (6003, 3, 3001, 1, '103 茶餐厅 100 元代金券', 8000, 1, 8000, 8000, 3, 4, '2026-09-03 11:00:00', NULL, '2026-09-03 11:05:00');

INSERT INTO `user_voucher`
  (`id`, `user_id`, `order_id`, `product_id`, `shop_id`, `voucher_code`, `status`, `valid_begin_time`, `expire_time`, `create_time`, `update_time`)
VALUES
  (7001, 2, 6002, 3001, 1, 'ROAMLYDEMO20260903001', 'UNUSED', '2026-09-03 10:02:00', '2026-10-03 10:02:00', '2026-09-03 10:02:00', '2026-09-03 10:02:00');

SET FOREIGN_KEY_CHECKS = 1;
