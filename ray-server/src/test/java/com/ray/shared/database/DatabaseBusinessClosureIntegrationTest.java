package com.ray.shared.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ray.service.VoucherSettlementService;
import com.ray.shared.config.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 在显式授权的开发 MySQL 与隔离 Redis DB 15 上验收完整 Demo 业务闭环。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@IntegrationTest
@EnabledIfEnvironmentVariable(named = "RUN_DATABASE_INTEGRATION_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseBusinessClosureIntegrationTest {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private VoucherSettlementService settlementService;

    @BeforeAll
    void rebuildAuthorizedDevelopmentDatabase() {
        flushRedis();
        rebuildSnapshot();
    }

    @AfterAll
    void restoreSeedOnlyState() {
        rebuildSnapshot();
        flushRedis();
    }

    @Test
    @Order(1)
    void snapshotHasTwentyOneCurrentTablesAndConsistentSeedFacts() {
        var tableNames = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = database() order by table_name",
                String.class);
        assertEquals(21, tableNames.size(), "当前表=" + tableNames);
        Integer legacyCount = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = database() "
                        + "and table_name in ('blog','blog_comments','voucher','seckill_voucher')",
                Integer.class);
        assertEquals(0, legacyCount);
        assertEquals(1, indexCount("section_follow", "uk_section_follow_user_section"));
        assertEquals(1, indexCount("shop_review", "uk_review_shop_user"));
        assertEquals(1, indexCount("user_voucher", "uk_user_voucher_order"));
        assertEquals(1, indexCount("admin_user", "uk_admin_user_username"));
        assertEquals(1, indexCount("operation_audit_log", "idx_audit_actor_time"));
        assertEquals(1, count("select count(*) from admin_user where username='admin' "
                + "and role='PLATFORM_ADMIN' and status='ACTIVE' and force_password_change=1"));

        assertEquals(0, count("select count(*) from post p where p.liked_count <> "
                + "(select count(*) from post_like pl where pl.post_id=p.id)"));
        assertEquals(0, count("select count(*) from post_comment c where c.liked_count <> "
                + "(select count(*) from post_comment_like l where l.comment_id=c.id)"));
        assertEquals(0, count("select count(*) from shop s where s.comments <> "
                + "(select count(*) from shop_review r where r.shop_id=s.id and r.status=0)"));
        assertEquals(0, count("select count(*) from shop s where s.comments > 0 and s.score <> "
                + "(select round(avg(r.score)*10) from shop_review r where r.shop_id=s.id and r.status=0)"));
        assertEquals(198, count("select available_stock from voucher_product where id=3001"));
        assertEquals(1, count("select sold_count from voucher_product where id=3001"));
        assertEquals(1, count("select count(*) from user_voucher where order_id=6002 and status='UNUSED'"));
        assertEquals(1, count("select count(*) from post_media pm join media_asset m on m.id=pm.media_asset_id "
                + "where pm.post_id=1001 and m.status=1 and m.bound_type=1 and m.bound_id=1001"));
    }

    @Test
    @Order(2)
    void realAdminFlowClosesPasswordAccountSessionProtectionAndAudit() throws Exception {
        String consumerToken = login("13686869696");
        String initialAdminToken = loginAdmin("admin", "Roamly123");

        assertEquals(HttpStatus.UNAUTHORIZED,
                exchange("/v1/admin/auth/me", HttpMethod.GET, null, consumerToken).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED,
                exchange("/v1/users/me", HttpMethod.GET, null, initialAdminToken).getStatusCode());
        ResponseEntity<String> forced = exchange(
                "/v1/admin/users", HttpMethod.GET, null, initialAdminToken);
        assertEquals(HttpStatus.FORBIDDEN, forced.getStatusCode());
        assertEquals("PASSWORD_CHANGE_REQUIRED", objectMapper.readTree(forced.getBody()).path("code").asText());

        assertEquals(HttpStatus.NO_CONTENT, exchange(
                        "/v1/admin/auth/password",
                        HttpMethod.PUT,
                        Map.of("currentPassword", "Roamly123", "newPassword", "AdminPass9"),
                        initialAdminToken)
                .getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED,
                exchange("/v1/admin/auth/me", HttpMethod.GET, null, initialAdminToken).getStatusCode());

        String adminToken = loginAdmin("admin", "AdminPass9");
        ResponseEntity<String> lastPlatformAdmin = exchange(
                "/v1/admin/users/1",
                HttpMethod.PUT,
                Map.of("displayName", "Roamly 管理员", "role", "FINANCE", "version", 1),
                adminToken);
        assertEquals(HttpStatus.CONFLICT, lastPlatformAdmin.getStatusCode());
        assertEquals("LAST_PLATFORM_ADMIN_REQUIRED", objectMapper.readTree(lastPlatformAdmin.getBody()).path("code").asText());

        ResponseEntity<String> created = exchange(
                "/v1/admin/users",
                HttpMethod.POST,
                Map.of(
                        "username", "reviewer.one",
                        "displayName", "审核同学",
                        "role", "MERCHANT_REVIEWER",
                        "initialPassword", "Reviewer8"),
                adminToken);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        String reviewerId = data(created).path("id").asText();
        String reviewerToken = loginAdmin("reviewer.one", "Reviewer8");

        assertEquals(HttpStatus.NO_CONTENT, exchange(
                        "/v1/admin/users/" + reviewerId + "/password-reset",
                        HttpMethod.POST,
                        Map.of("newPassword", "Reviewer9", "version", 0),
                        adminToken)
                .getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED,
                exchange("/v1/admin/auth/me", HttpMethod.GET, null, reviewerToken).getStatusCode());
        reviewerToken = loginAdmin("reviewer.one", "Reviewer9");

        assertEquals(HttpStatus.NO_CONTENT, exchange(
                        "/v1/admin/users/" + reviewerId + "/disablement",
                        HttpMethod.POST,
                        Map.of("version", 1),
                        adminToken)
                .getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED,
                exchange("/v1/admin/auth/me", HttpMethod.GET, null, reviewerToken).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, exchange(
                        "/v1/admin/users/" + reviewerId + "/activation",
                        HttpMethod.POST,
                        Map.of("version", 2),
                        adminToken)
                .getStatusCode());

        assertEquals(1, count("select count(*) from admin_user where username='reviewer.one' "
                + "and role='MERCHANT_REVIEWER' and status='ACTIVE' and force_password_change=1 and version=3"));
        assertTrue(count("select count(*) from operation_audit_log where actor_type='ADMIN' "
                + "and action in ('ADMIN_PASSWORD_CHANGE','ADMIN_USER_CREATE','ADMIN_PASSWORD_RESET',"
                + "'ADMIN_USER_DISABLE','ADMIN_USER_ACTIVATE') and result='SUCCEEDED'") >= 5);
        assertEquals(0, count("select count(*) from operation_audit_log where reason like '%Password%' "
                + "or reason like '%Reviewer%' or reason like '%admin-token%'"));

        ResponseEntity<String> secondPlatform = exchange(
                "/v1/admin/users",
                HttpMethod.POST,
                Map.of(
                        "username", "platform.two",
                        "displayName", "平台管理员二号",
                        "role", "PLATFORM_ADMIN",
                        "initialPassword", "Platform8"),
                adminToken);
        assertEquals(HttpStatus.CREATED, secondPlatform.getStatusCode());
        String secondPlatformId = data(secondPlatform).path("id").asText();
        String secondPlatformToken = loginAdmin("platform.two", "Platform8");
        assertEquals(HttpStatus.NO_CONTENT, exchange(
                        "/v1/admin/auth/password",
                        HttpMethod.PUT,
                        Map.of("currentPassword", "Platform8", "newPassword", "Platform9"),
                        secondPlatformToken)
                .getStatusCode());
        secondPlatformToken = loginAdmin("platform.two", "Platform9");

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            String finalSecondPlatformToken = secondPlatformToken;
            var first = executor.submit(() -> concurrentRoleChange("1", 1, adminToken, ready, start));
            var second = executor.submit(() ->
                    concurrentRoleChange(secondPlatformId, 1, finalSecondPlatformToken, ready, start));
            ready.await();
            start.countDown();
            List<HttpStatus> statuses = List.of(first.get(), second.get());
            assertEquals(1, statuses.stream().filter(HttpStatus.OK::equals).count());
            assertEquals(1, statuses.stream().filter(HttpStatus.CONFLICT::equals).count());
        }
        assertEquals(1, count("select count(*) from admin_user where role='PLATFORM_ADMIN' and status='ACTIVE'"));
        logout(consumerToken);
    }

    @Test
    @Order(3)
    void realHttpClosesAuthSectionPostCommentAndThreeFeedFlows() throws Exception {
        String authorToken = login("13686869696");
        String readerToken = login("13838411438");

        assertEquals(HttpStatus.NO_CONTENT, exchange(
                        "/v1/users/me/section-follows/4", HttpMethod.PUT, null, authorToken)
                .getStatusCode());
        assertEquals(1, count("select count(*) from section_follow where user_id=1 and section_id=4"));

        ResponseEntity<String> created = exchange(
                "/v1/posts",
                HttpMethod.POST,
                Map.of("title", "数据库闭环", "content", "真实 HTTP 发布动态", "shopVisit", false),
                authorToken);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        String postId = data(created).path("id").asText();
        assertFalse(postId.isBlank());

        ResponseEntity<String> updated = exchange(
                "/v1/posts/" + postId,
                HttpMethod.PUT,
                Map.of("title", "数据库闭环已更新", "content", "真实 HTTP 修改动态", "shopVisit", false),
                authorToken);
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertEquals("数据库闭环已更新", data(updated).path("title").asText());
        assertEquals(HttpStatus.NO_CONTENT,
                exchange("/v1/posts/" + postId + "/like", HttpMethod.PUT, null, readerToken).getStatusCode());

        ResponseEntity<String> root = exchange(
                "/v1/posts/" + postId + "/comments",
                HttpMethod.POST,
                Map.of("content", "根评论"),
                readerToken);
        assertEquals(HttpStatus.CREATED, root.getStatusCode());
        String rootId = data(root).path("id").asText();
        ResponseEntity<String> reply = exchange(
                "/v1/comments/" + rootId + "/replies",
                HttpMethod.POST,
                Map.of("content", "作者回复"),
                authorToken);
        assertEquals(HttpStatus.CREATED, reply.getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT,
                exchange("/v1/comments/" + rootId, HttpMethod.DELETE, null, readerToken).getStatusCode());
        assertEquals(1, count("select status from post_comment where id=" + rootId));
        assertEquals(0, count("select count(*) from post_comment where id=" + rootId + " and content is not null"));

        assertEquals(HttpStatus.OK,
                http.getForEntity("/v1/feeds/recommended?cityCode=330100", String.class).getStatusCode());
        assertEquals(HttpStatus.OK,
                exchange("/v1/feeds/following", HttpMethod.GET, null, authorToken).getStatusCode());
        assertEquals(HttpStatus.OK,
                http.getForEntity("/v1/sections/1/posts?cityCode=330100", String.class).getStatusCode());

        assertEquals(HttpStatus.NO_CONTENT,
                exchange("/v1/posts/" + postId, HttpMethod.DELETE, null, authorToken).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, exchange(
                        "/v1/users/me/section-follows/4", HttpMethod.DELETE, null, authorToken)
                .getStatusCode());
        logout(authorToken);
        logout(readerToken);
    }

    @Test
    @Order(4)
    void realTradeFlowClosesShopReviewStockSettlementWalletExpiryAndIsolation() throws Exception {
        String firstToken = login("13686869696");
        String buyerToken = login("13456789011");

        ResponseEntity<String> shops = http.getForEntity(
                "/v1/shops?cityCode=330100&sort=DISTANCE&longitude=120.15&latitude=30.32", String.class);
        assertEquals(HttpStatus.OK, shops.getStatusCode());
        assertTrue(data(shops).path("items").get(0).path("distance").isNumber());

        ResponseEntity<String> review = exchange(
                "/v1/shops/3/reviews",
                HttpMethod.POST,
                Map.of("score", 5, "content", "真实数据库点评"),
                firstToken);
        assertEquals(HttpStatus.CREATED, review.getStatusCode());
        assertEquals(1, count("select comments from shop where id=3"));
        assertEquals(50, count("select score from shop where id=3"));
        assertEquals(HttpStatus.CONFLICT, exchange(
                        "/v1/shops/3/reviews",
                        HttpMethod.POST,
                        Map.of("score", 4, "content", "重复点评"),
                        firstToken)
                .getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT,
                exchange("/v1/shops/3/reviews/me", HttpMethod.DELETE, null, firstToken).getStatusCode());
        assertEquals(0, count("select comments from shop where id=3"));

        ResponseEntity<String> pending = exchange(
                "/v1/voucher-products/3002/orders",
                HttpMethod.POST,
                Map.of("quantity", 1),
                firstToken);
        assertEquals(HttpStatus.CREATED, pending.getStatusCode());
        String pendingId = data(pending).path("id").asText();
        assertEquals(79, count("select available_stock from voucher_product where id=3002"));
        assertEquals(HttpStatus.NO_CONTENT, exchange(
                        "/v1/users/me/orders/" + pendingId, HttpMethod.DELETE, null, firstToken)
                .getStatusCode());
        assertEquals(80, count("select available_stock from voucher_product where id=3002"));

        ResponseEntity<String> paid = exchange(
                "/v1/voucher-products/3002/orders",
                HttpMethod.POST,
                Map.of("quantity", 1),
                buyerToken);
        assertEquals(HttpStatus.CREATED, paid.getStatusCode());
        String paidId = data(paid).path("id").asText();
        settlementService.confirmPaid(Long.valueOf(paidId), LocalDateTime.of(2026, 9, 4, 12, 0));
        settlementService.confirmPaid(Long.valueOf(paidId), LocalDateTime.of(2026, 9, 4, 12, 0));
        assertEquals(1, count("select count(*) from user_voucher where order_id=" + paidId));
        assertEquals(1, count("select sold_count from voucher_product where id=3002"));
        assertEquals(HttpStatus.OK,
                exchange("/v1/users/me/vouchers?status=UNUSED", HttpMethod.GET, null, buyerToken).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                exchange("/v1/users/me/orders/" + paidId, HttpMethod.GET, null, firstToken).getStatusCode());

        jdbc.update("insert into user_voucher(id,user_id,order_id,product_id,shop_id,voucher_code,status," +
                        "valid_begin_time,expire_time) values(7999,1,6001,3001,1,'ROAMLYEXPIRED0000001','UNUSED'," +
                        "'2020-01-01 00:00:00','2020-01-02 00:00:00')");
        assertEquals(HttpStatus.OK,
                exchange("/v1/users/me/vouchers", HttpMethod.GET, null, firstToken).getStatusCode());
        assertEquals("EXPIRED", jdbc.queryForObject("select status from user_voucher where id=7999", String.class));

        logout(firstToken);
        logout(buyerToken);
    }

    private String login(String phone) throws Exception {
        ResponseEntity<String> code = exchange(
                "/v1/auth/sms-codes", HttpMethod.POST, Map.of("phone", phone), null);
        assertEquals(HttpStatus.NO_CONTENT, code.getStatusCode());
        ResponseEntity<String> session = exchange(
                "/v1/auth/sessions",
                HttpMethod.POST,
                Map.of("phone", phone, "code", "123456"),
                null);
        assertEquals(HttpStatus.OK, session.getStatusCode());
        String token = data(session).path("accessToken").asText();
        assertFalse(token.isBlank());
        return token;
    }

    private String loginAdmin(String username, String password) throws Exception {
        ResponseEntity<String> response = exchange(
                "/v1/admin/auth/login", HttpMethod.POST, Map.of("username", username, "password", password), null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String token = data(response).path("token").asText();
        assertFalse(token.isBlank());
        return token;
    }

    private HttpStatus concurrentRoleChange(
            String adminId,
            int version,
            String token,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return (HttpStatus) exchange(
                        "/v1/admin/users/" + adminId,
                        HttpMethod.PUT,
                        Map.of("displayName", "并发管理员 " + adminId, "role", "FINANCE", "version", version),
                        token)
                .getStatusCode();
    }

    private void logout(String token) {
        assertEquals(HttpStatus.NO_CONTENT,
                exchange("/v1/auth/session", HttpMethod.DELETE, null, token).getStatusCode());
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode data(ResponseEntity<String> response) throws Exception {
        assertNotNull(response.getBody());
        return objectMapper.readTree(response.getBody()).path("data");
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private int indexCount(String table, String index) {
        Integer value = jdbc.queryForObject(
                "select count(distinct index_name) from information_schema.statistics "
                        + "where table_schema=database() and table_name=? and index_name=?",
                Integer.class,
                table,
                index);
        return value == null ? 0 : value;
    }

    private void rebuildSnapshot() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("schema-init.sql"), new ClassPathResource("seed-dev.sql"));
        populator.setContinueOnError(false);
        DatabasePopulatorUtils.execute(populator, dataSource);
    }

    private void flushRedis() {
        var factory = redis.getConnectionFactory();
        assertNotNull(factory);
        try (var connection = factory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }
}
