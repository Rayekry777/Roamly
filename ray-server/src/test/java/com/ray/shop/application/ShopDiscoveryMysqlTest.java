package com.ray.shop.application;
import static org.junit.jupiter.api.Assertions.*;
import com.ray.mapper.ShopDiscoveryMapper;
import com.ray.service.discovery.*;
import com.ray.service.impl.ShopDiscoveryServiceImpl;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
/** 显式启用后仅创建和销毁随机命名的独立库，永远不初始化现有开发库。 */
@EnabledIfSystemProperty(named="discovery.mysql", matches="true")
class ShopDiscoveryMysqlTest {
    @Test void verifiesActualMysqlSeedIsolationPagingAndVoucherSelection() throws Exception {
        var properties = new Properties();
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve(".env"))) root = root.getParent();
        assertNotNull(root,"Missing local environment file");
        try (var reader = Files.newBufferedReader(root.resolve(".env"))) { properties.load(reader); }
        String host = properties.getProperty("DB_HOST"), port = properties.getProperty("DB_PORT","3306");
        String user = properties.getProperty("DB_USERNAME","root"), password = properties.getProperty("DB_PASSWORD");
        String database = "roamly_discovery_test_" + UUID.randomUUID().toString().replace("-","");
        assertTrue(database.matches("roamly_discovery_test_[a-f0-9]{32}"));
        String server = "jdbc:mysql://"+host+":"+port+"/";
        String options = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&connectTimeout=5000&socketTimeout=60000";
        try (var admin = DriverManager.getConnection(server+options,user,password); var statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `"+database+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            try {
                var ds = new DriverManagerDataSource(server+database+options,user,password);
                var initializer = new ResourceDatabasePopulator(new ClassPathResource("schema-init.sql"),new ClassPathResource("seed-dev.sql")); initializer.setSqlScriptEncoding("UTF-8"); initializer.execute(ds);
                var jdbc = new JdbcTemplate(ds);
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM shop s JOIN shop_type t ON t.id=s.type_id WHERE t.parent_id IS NULL",Integer.class));
                assertEquals(14,jdbc.queryForObject("SELECT COUNT(*) FROM shop_type WHERE parent_id IS NOT NULL",Integer.class));
                for (String sql : List.of(
                        "SELECT COUNT(*) FROM voucher_order o LEFT JOIN voucher_product p ON p.id=o.product_id LEFT JOIN shop s ON s.id=o.shop_id LEFT JOIN user u ON u.id=o.user_id WHERE p.id IS NULL OR s.id IS NULL OR u.id IS NULL OR p.shop_id<>o.shop_id",
                        "SELECT COUNT(*) FROM user_voucher v LEFT JOIN voucher_order o ON o.id=v.order_id WHERE o.id IS NULL OR v.product_id<>o.product_id OR v.shop_id<>o.shop_id OR v.user_id<>o.user_id",
                        "SELECT COUNT(*) FROM voucher_product p LEFT JOIN (SELECT product_id,SUM(quantity) sold FROM voucher_order WHERE status IN ('PAID','COMPLETED') GROUP BY product_id) o ON o.product_id=p.id WHERE p.id BETWEEN 400001 AND 403000 AND (p.sold_count<>COALESCE(o.sold,0) OR p.available_stock+p.sold_count<>p.total_stock)",
                        "SELECT COUNT(*) FROM shop s LEFT JOIN (SELECT shop_id,COUNT(*) c,ROUND(AVG(score)*10) score FROM shop_review WHERE status=0 GROUP BY shop_id) r ON r.shop_id=s.id WHERE s.id BETWEEN 300001 AND 300300 AND (s.comments<>COALESCE(r.c,0) OR s.score<>COALESCE(r.score,0))",
                        "SELECT COUNT(*) FROM voucher_order WHERE id BETWEEN 500001 AND 510000 AND pay_time<create_time")) {
                    assertEquals(0,jdbc.queryForObject(sql,Integer.class),sql);
                }
                var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
                var queryCounter = new QueryCounter(); configuration.addInterceptor(queryCounter);
                configuration.addMapper(ShopDiscoveryMapper.class);
                var factory = new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(configuration);
                factory.setMapperLocations(new ClassPathResource("mapper/ShopDiscoveryMapper.xml"));
                try (var session = factory.getObject().openSession()) {
                    var mapper = session.getMapper(ShopDiscoveryMapper.class);
                    var food = query(1L,null,"RECOMMENDED",0,10);var leisure = query(2L,null,"RECOMMENDED",0,100);
                    long start = System.nanoTime(); var first = mapper.page(food); var next = mapper.page(query(1L,null,"RECOMMENDED",10,10));
                    assertEquals(10,first.size());assertEquals(10,next.size());
                    assertTrue(Collections.disjoint(first.stream().map(DiscoveryShopRow::getId).toList(),next.stream().map(DiscoveryShopRow::getId).toList()));
                    assertTrue(first.stream().allMatch(s -> s.getCategoryId()==1L && s.getCityCode().equals("630100")));
                    var before = mapper.page(leisure).stream().map(DiscoveryShopRow::getId).toList();
                    jdbc.update("UPDATE voucher_product p JOIN shop s ON s.id=p.shop_id JOIN shop_type t ON t.id=s.type_id SET p.sold_count=p.sold_count+1000 WHERE t.parent_id=1");
                    session.clearCache();
                    assertEquals(before,mapper.page(leisure).stream().map(DiscoveryShopRow::getId).toList());
                    var noCoupon = mapper.page(query(1L,null,"SALES",0,100)).stream().filter(s -> s.getId()==300001L).findFirst();
                    assertTrue(noCoupon.isPresent());
                    var candidate = mapper.vouchers(List.of(300001L,300003L,300005L,300009L),food);
                    var allIds = jdbc.queryForList("SELECT id FROM shop WHERE id BETWEEN 300001 AND 300300", Long.class);
                    var saleRows = mapper.vouchers(allIds, food);
                    for (var row : saleRows) {
                        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM voucher_product WHERE id=? AND review_status='APPROVED' AND sale_status='ON_SALE' AND available_stock>0 AND sale_begin_time<=NOW() AND sale_end_time>=NOW()", Integer.class, row.getId()));
                    }
                    assertTrue(candidate.stream().noneMatch(v -> v.getShopId()==300001L));
                    assertEquals(1,candidate.stream().filter(v -> v.getShopId()==300003L).count());
                    assertEquals(2,candidate.stream().filter(v -> v.getShopId()==300005L).count());
                    assertTrue(candidate.size()<=16);
                    for (var id:List.of(300003L,300005L,300009L)) {
                        var vouchers = ShopDiscoveryServiceImpl.selectVouchers(candidate.stream().filter(v -> v.getShopId().equals(id)).toList());
                        assertTrue(vouchers.size()<=2);assertEquals(vouchers.size(),vouchers.stream().map(DiscoveryVoucherRow::getId).distinct().count());
                    }
                    assertTrue(mapper.page(query(1L,"不存在的关键词__test__","RECOMMENDED",0,10)).isEmpty());
                    assertTrue(mapper.count(food)>20);
                    for(var sort:List.of("DISTANCE","SCORE","SALES","RECOMMENDED")) assertFalse(mapper.page(query(2L,null,sort,0,10)).isEmpty());
                    var measurements = new StringBuilder();
                    for (int size : List.of(1, 10, 50)) {
                        long[] samples = new long[10];
                        for (int run = 0; run < samples.length; run++) {
                            session.clearCache(); queryCounter.count = 0;
                            var measured = query(2L, null, "RECOMMENDED", 0, size);
                            long began = System.nanoTime();
                            var rows = mapper.page(measured); mapper.count(measured);
                            mapper.vouchers(rows.stream().map(DiscoveryShopRow::getId).toList(), measured);
                            samples[run] = (System.nanoTime() - began) / 1_000_000;
                            assertEquals(3, queryCounter.count, "Page/count/vouchers must use three queries regardless of page size");
                        }
                        Arrays.sort(samples);
                        measurements.append("Page size ").append(size).append(": 3 SQL queries, median ").append(samples[5]).append(" ms, max ").append(samples[9]).append(" ms\n");
                    }
                    var mapped = configuration.getMappedStatement("com.ray.mapper.ShopDiscoveryMapper.page");
                    Map<String,Object> parameters = Map.of("q", food);
                    var bound = mapped.getBoundSql(parameters);
                    try (var connection = ds.getConnection(); var explain = connection.prepareStatement("EXPLAIN " + bound.getSql())) {
                        new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(mapped, parameters, bound).setParameters(explain);
                        try (var result = explain.executeQuery()) {
                            while (result.next()) measurements.append("EXPLAIN ").append(result.getString("table")).append(" type=").append(result.getString("type")).append(" key=").append(result.getString("key")).append(" rows=").append(result.getString("rows")).append('\n');
                        }
                    }
                    Files.createDirectories(Path.of("target"));
                    Files.writeString(Path.of("target/discovery-mysql-verification.txt"),"Schema and seed succeeded; city/category isolation; 0/1/2/many vouchers; pagination; all sorts.\n" + measurements + "Scenario elapsed ms: "+(System.nanoTime()-start)/1_000_000);
                }
                if (Boolean.getBoolean("discovery.runtime")) verifyRuntime(ds, root);
            } finally { statement.execute("DROP DATABASE `"+database+"`"); }
        }
    }
    private void verifyRuntime(DriverManagerDataSource ds, Path root) throws Exception {
        var application = new org.springframework.boot.builder.SpringApplicationBuilder(com.ray.RayRoamlyApplication.class)
            .initializers(context -> context.addBeanFactoryPostProcessor(factory -> {
                var registry = (org.springframework.beans.factory.support.BeanDefinitionRegistry) factory;
                String scheduled = "org.springframework.context.annotation.internalScheduledAnnotationProcessor";
                if (registry.containsBeanDefinition(scheduled)) registry.removeBeanDefinition(scheduled);
                if (registry.containsBeanDefinition("cacheManager")) registry.removeBeanDefinition("cacheManager");
                factory.registerSingleton("cacheManager", new org.springframework.cache.concurrent.ConcurrentMapCacheManager());
            }));
        try (var context = application.run("--spring.profiles.active=test", "--spring.sql.init.mode=never",
                "--spring.config.import=optional:file:"+root.resolve(".env").toString().replace('\\','/')+"[.properties]",
                "--spring.datasource.url="+ds.getUrl(), "--spring.datasource.username="+ds.getUsername(),
                "--spring.datasource.password="+ds.getPassword(), "--server.port=18081",
                "--ray.upload.image-dir=./target/discovery-runtime-media", "--ray.storage.local.root=./target/discovery-runtime-objects",
                "--spring.main.banner-mode=off", "--logging.level.root=WARN")) {
            var client = java.net.http.HttpClient.newHttpClient();
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String base = "http://127.0.0.1:18081";
            var doc = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/v3/api-docs")).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200,doc.statusCode());
            var document = mapper.readTree(doc.body());
            assertTrue(document.path("paths").has("/v1/shops/discovery"));
            assertTrue(document.path("paths").has("/v1/shop-types/tree"));
            assertFalse(document.path("paths").has("/v1/voucher-products"));
            var subsidy = document.at("/paths/~1v1~1admin~1voucher-reviews~1{productId}~1platform-subsidy/put");
            assertEquals("AdminVoucherReviewController_updatePlatformSubsidy", subsidy.path("operationId").asText());
            assertTrue(subsidy.path("security").toString().contains("BearerAuth"));
            assertTrue(subsidy.path("responses").has("400"));
            assertTrue(document.at("/components/schemas/PlatformSubsidyUpdateDTO/required").toString().contains("platformDiscountAmount"));
            assertFalse(document.at("/components/schemas/MerchantVoucherProductUpdateDTO/properties").has("platformDiscountAmount"));
            assertEquals("#/components/schemas/ShopDiscoveryResult", document.at("/paths/~1v1~1shops~1discovery/get/responses/200/content/application~1json/schema/$ref").asText());
            assertEquals("#/components/schemas/ShopDiscoveryVO", document.at("/components/schemas/ShopDiscoveryPage/properties/items/items/$ref").asText());
            assertTrue(document.at("/components/schemas/ShopDiscoveryVO/properties").has("vouchers"));
            for (String route : List.of("/doc.html","/v1/shop-types/tree","/v1/shops/discovery?cityCode=330100&categoryId=1","/v1/shops/discovery?cityCode=630100&categoryId=2")) {
                var response=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+route)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(200,response.statusCode(),route+" "+response.body());
            }
            for(String route:List.of("/v1/shops/discovery?cityCode=330100&categoryId=1&page=0", "/v1/shops/discovery?cityCode=330100&categoryId=2&typeId=104", "/v1/shops/discovery?cityCode=330100&categoryId=1&sort=DISTANCE")) {
                assertEquals(400,client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+route)).build(),java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
            }
            Files.writeString(Path.of("target/discovery-openapi.json"),doc.body());
            var shopVouchers = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                    base + "/v1/shops/300032/voucher-products")).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200,shopVouchers.statusCode(),shopVouchers.body());
            var products = mapper.readTree(shopVouchers.body()).path("data");
            assertTrue(products.isArray() && !products.isEmpty(),"日志中的门店应能返回可售券");
            for (var product : products) {
                var detail = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                        base + "/v1/voucher-products/" + product.path("id").asText())).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(200,detail.statusCode(),detail.body());
            }
            var jdbc = new JdbcTemplate(ds);
            for (String validity : jdbc.queryForList("SELECT DISTINCT validity_type FROM voucher_product WHERE validity_type IS NOT NULL",String.class)) {
                assertDoesNotThrow(() -> com.ray.enums.VoucherValidityType.valueOf(validity));
            }
            verifySeedRefundExecution(context, ds);
            verifyPlatformSubsidyHttp(context, ds, base);
            if (Boolean.getBoolean("discovery.ui")) {
                Path stop = Path.of("target/discovery-ui.stop"); Files.deleteIfExists(stop);
                Files.writeString(Path.of("target/discovery-ui.ready"),"http://127.0.0.1:18081");
                long deadline=System.nanoTime()+java.time.Duration.ofMinutes(15).toNanos();
                while (!Files.exists(stop) && System.nanoTime()<deadline) Thread.sleep(500);
                Files.deleteIfExists(Path.of("target/discovery-ui.ready"));
            }
        }
    }

    private DiscoveryQuery query(Long category,String keyword,String sort,int offset,int size) {
        return new DiscoveryQuery("630100",category,null,keyword,sort,101.749746,36.742782,"630105",offset,size,LocalDateTime.now());
    }

    /** 使用真实事务、Mock 渠道和账务实现验证种子退款，而不是只检查静态关联。 */
    private void verifyPlatformSubsidyHttp(org.springframework.context.ConfigurableApplicationContext context,
            DriverManagerDataSource ds, String base) throws Exception {
        var jdbc = new JdbcTemplate(ds);
        long adminId = 8000000000000L + new java.security.SecureRandom().nextInt(1000000000);
        jdbc.update("INSERT INTO admin_user (id,username,password_hash,display_name,role,status,force_password_change,version) SELECT ?,?,password_hash,'补贴隔离测试','PLATFORM_ADMIN','ACTIVE',0,0 FROM admin_user WHERE id=1", adminId, "subsidy-"+adminId);
        var auth = context.getBean("adminStpLogic", cn.dev33.satoken.stp.StpLogic.class);
        String token = auth.createLoginSession(adminId);
        try {
            long id = jdbc.queryForObject("SELECT MIN(id) FROM voucher_product WHERE price_amount>1000 AND merchant_subsidy_amount < price_amount-500", Long.class);
            int version = jdbc.queryForObject("SELECT version FROM voucher_product WHERE id=?", Integer.class, id);
            long oldOrders = jdbc.queryForObject("SELECT COALESCE(SUM(pay_amount),0) FROM voucher_order", Long.class);
            var client = java.net.http.HttpClient.newHttpClient();
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String path = base+"/v1/admin/voucher-reviews/"+id+"/platform-subsidy";
            for (long amount : new long[]{-1, 100000001}) {
                var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(path)).header("Authorization", "Bearer "+token)
                        .header("Content-Type", "application/json").PUT(java.net.http.HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("version",version,"platformDiscountAmount",amount)))).build();
                assertEquals(400, client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            }
            String body = mapper.writeValueAsString(Map.of("version",version,"platformDiscountAmount",500));
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(path)).header("Authorization", "Bearer "+token)
                    .header("Content-Type", "application/json").PUT(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
            var result = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200,result.statusCode(),result.body());
            assertEquals(500L,jdbc.queryForObject("SELECT platform_discount_amount FROM voucher_product WHERE id=?",Long.class,id));
            assertEquals(version+1,jdbc.queryForObject("SELECT version FROM voucher_product WHERE id=?",Integer.class,id));
            assertEquals(oldOrders,jdbc.queryForObject("SELECT COALESCE(SUM(pay_amount),0) FROM voucher_order",Long.class));
            assertEquals(409,client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            var anonymous = java.net.http.HttpRequest.newBuilder(java.net.URI.create(path)).header("Content-Type", "application/json")
                    .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
            assertEquals(401,client.send(anonymous,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
        } finally {
            auth.logoutByTokenValue(token);
        }
    }

    private void verifySeedRefundExecution(org.springframework.context.ConfigurableApplicationContext context,
            DriverManagerDataSource ds) {
        var jdbc = new JdbcTemplate(ds);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM voucher_refund r JOIN user_voucher v ON v.id=r.voucher_id WHERE r.idempotency_key=CONCAT('seed-refund-',v.id) AND v.status<>IF(r.execution_status='SUCCESS','REFUNDED','REFUNDING')", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM voucher_refund_attempt a JOIN voucher_refund r ON r.id=a.refund_id WHERE r.decision_status='PENDING_REVIEW' AND a.status IN ('WAITING','PROCESSING','RETRY_WAITING')", Integer.class));
        var ids = jdbc.queryForList("SELECT MIN(id) FROM voucher_refund_attempt WHERE idempotency_key LIKE 'seed-refund-attempt-%' AND status IN ('RETRY_WAITING','PROCESSING') GROUP BY status", Long.class);
        assertEquals(2, ids.size());
        var worker = context.getBean(com.ray.refund.RefundExecutionWorker.class);
        for (Long id : ids) {
            assertTrue(worker.execute(id, "seed-regression"));
            assertEquals("SUCCESS",jdbc.queryForObject("SELECT status FROM voucher_refund_attempt WHERE id=?",String.class,id));
            assertEquals("REFUNDED",jdbc.queryForObject("SELECT v.status FROM user_voucher v JOIN voucher_refund r ON r.voucher_id=v.id JOIN voucher_refund_attempt a ON a.refund_id=r.id WHERE a.id=?",String.class,id));
            assertFalse(worker.execute(id, "seed-regression"), "成功任务不能重复执行");
        }
    }

    @org.apache.ibatis.plugin.Intercepts(@org.apache.ibatis.plugin.Signature(type=org.apache.ibatis.executor.Executor.class, method="query", args={org.apache.ibatis.mapping.MappedStatement.class,Object.class,org.apache.ibatis.session.RowBounds.class,org.apache.ibatis.session.ResultHandler.class}))
    static class QueryCounter implements org.apache.ibatis.plugin.Interceptor {
        int count;
        @Override public Object intercept(org.apache.ibatis.plugin.Invocation invocation) throws Throwable {
            count++;
            return invocation.proceed();
        }
    }
}
