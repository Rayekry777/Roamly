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
  (1, '103 茶餐厅', 1, '330100', 'https://example.com/images/tea-restaurant.jpg', '大关', '金华路锦昌文华苑 29 号', 120.149192, 30.316078, 80, 4215, 3035, 37, '10:00-22:00', 1),
  (2, '漫游咖啡实验室', 1, '330100', 'https://example.com/images/coffee-lab.jpg', '运河上街', '台州路 2 号', 120.151954, 30.324970, 68, 1260, 860, 46, '09:00-21:00', 1),
  (3, '周末放映厅', 2, '330100', 'https://example.com/images/cinema.jpg', '拱宸桥', '丽水路 58 号', 120.146659, 30.312742, 120, 680, 420, 45, '13:00-23:00', 1);

INSERT INTO `post`
  (`id`, `user_id`, `section_id`, `shop_visit`, `shop_id`, `city_code`, `title`, `content`, `liked_count`, `comment_count`, `status`, `create_time`, `update_time`)
VALUES
  (1001, 1, 1, 0, NULL, '330100', '傍晚的运河散步', '下班后沿着运河走了一圈，风很舒服。', 3, 1, 0, '2026-09-01 18:30:00', '2026-09-01 18:30:00'),
  (1002, 2, 2, 1, 1, '330100', '一顿很满足的茶餐厅晚餐', '菠萝油和奶茶都很适合周末慢慢吃。', 8, 2, 0, '2026-09-02 12:10:00', '2026-09-02 12:10:00'),
  (1003, 3, 4, 1, 3, '330100', '周末的电影和朋友', '适合和朋友一起放空半天的小地方。', 5, 0, 0, '2026-09-02 16:20:00', '2026-09-02 16:20:00');

INSERT INTO `post_comment`
  (`id`, `post_id`, `user_id`, `root_id`, `parent_id`, `reply_to_user_id`, `content`, `liked_count`, `reply_count`, `author_replied`, `status`, `create_time`, `update_time`)
VALUES
  (2001, 1001, 2, NULL, NULL, NULL, '这个时间去最舒服。', 2, 0, 0, 0, '2026-09-01 19:00:00', '2026-09-01 19:00:00'),
  (2002, 1002, 1, NULL, NULL, NULL, '已经收藏，周末去试试。', 4, 1, 1, 0, '2026-09-02 12:20:00', '2026-09-02 12:20:00'),
  (2003, 1002, 2, 2002, 2002, 1, '记得试试菠萝油。', 1, 0, 0, 0, '2026-09-02 12:25:00', '2026-09-02 12:25:00');

INSERT INTO `voucher_product`
  (`id`, `shop_id`, `title`, `sub_title`, `cover`, `rules`, `pay_price`, `original_price`, `deduction_value`, `sale_type`, `total_stock`, `available_stock`, `sold_count`, `purchase_limit`, `validity_type`, `valid_days`, `status`)
VALUES
  (3001, 1, '103 茶餐厅 100 元代金券', '工作日、周末通用', NULL, '不可与店内其他优惠同享', 8000, 10000, 10000, 'NORMAL', 200, 200, 0, 1, 'DAYS_AFTER_PURCHASE', 30, 'ON_SALE'),
  (3002, 2, '漫游咖啡双人套餐', '限店内堂食使用', NULL, '请提前向商户确认可用时间', 6800, 8800, NULL, 'NORMAL', 80, 80, 0, 1, 'DAYS_AFTER_PURCHASE', 15, 'ON_SALE');

SET FOREIGN_KEY_CHECKS = 1;
