package cn.net.pap.common.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JdbcMySQLTemplateTest.Config.class)
@org.springframework.test.context.TestConstructor(autowireMode = org.springframework.test.context.TestConstructor.AutowireMode.ALL)
class JdbcMySQLTemplateTest {

    private static final Logger log = LoggerFactory.getLogger(JdbcMySQLTemplateTest.class);

    @Configuration
    static class Config {

        @Bean
        DataSource dataSource() {
            return DataSourceBuilder.create().driverClassName("com.mysql.cj.jdbc.Driver")
                    // 使用MySQL内存模式或测试数据库
                    .url("jdbc:mysql://127.0.0.1:3306/cf?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true")
                    .username("root").password("alexgaoyh").build();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource ds) {
            return new JdbcTemplate(ds);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource ds) {
            return new DataSourceTransactionManager(ds);
        }
    }

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final PlatformTransactionManager transactionManager;

    public JdbcMySQLTemplateTest(JdbcTemplate jdbcTemplate, DataSource dataSource, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.transactionManager = transactionManager;
    }

    private boolean isDatabaseConnected() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void setup() {
        // 创建测试表并插入大数据量
        jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS user_info (
                        id BIGINT PRIMARY KEY,
                        name VARCHAR(50),
                        age INT,
                        email VARCHAR(100),
                        status VARCHAR(20),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);

        // 清空表数据
        jdbcTemplate.update("DELETE FROM user_info");
    }

    /**
     * 插入大量测试数据
     */
    private void insertLargeTestData(int count) {
        // 使用批量插入提高性能
        jdbcTemplate.batchUpdate("INSERT INTO user_info (id, name, age, email, status) VALUES (?, ?, ?, ?, ?)", new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                long id = i + 1L;
                ps.setLong(1, id);
                ps.setString(2, "User-" + id);
                ps.setInt(3, 20 + (i % 50));
                ps.setString(4, "user" + id + "@example.com");
                ps.setString(5, i % 10 == 0 ? "INACTIVE" : "ACTIVE");
            }

            @Override
            public int getBatchSize() {
                return count;
            }
        });

        System.out.println("✅ 插入 " + count + " 条测试数据完成");
    }

    @Test
    @DisplayName("测试JDBC原生流式查询")
    void testJdbcStreamingQuery() throws SQLException {
        // 插入10万条测试数据
        insertLargeTestData(100000);

        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        AtomicInteger rowCount = new AtomicInteger(0);

        // 使用原生JDBC连接进行流式查询
        try (Connection conn = dataSource.getConnection();) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);){
                stmt.setFetchSize(Integer.MIN_VALUE);

                try (ResultSet rs = stmt.executeQuery("select * from user_info")) {
                    System.out.println("开始流式读取数据...");
                    long startTime = System.currentTimeMillis();
                    while (rs.next()) {
                        rowCount.incrementAndGet();

                        // 模拟数据处理
                        long id = rs.getLong("id");
                        String name = rs.getString("name");
                        String status = rs.getString("status");

                        // 验证数据
                        assertNotNull(name);
                        assertNotNull(status);

                        // 每处理10000条记录输出一次进度
                        if (rowCount.get() % 10000 == 0) {
                            System.out.printf("已处理 %d 条记录，当前ID: %d%n", rowCount.get(), id);
                        }
                    }

                    long endTime = System.currentTimeMillis();
                    long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

                    System.out.printf("✅ 流式查询完成！\n");
                    System.out.printf("总记录数: %d\n", rowCount.get());
                    System.out.printf("处理时间: %.2f秒\n", (endTime - startTime) / 1000.0);
                    System.out.printf("内存增量: %.2f MB\n", (endMemory - startMemory) / (1024.0 * 1024.0));

                    // 验证读取了所有记录
                    assertEquals(100000, rowCount.get());
                }
            }

        } catch (SQLException e) {
            fail("流式查询失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("根据数据库中的数据，模板化生成update语句")
    void testUpdate1() {
        jdbcTemplate.update("INSERT INTO user_info (id, name, age, email, status) VALUES (?, ?, ?, ?, ?)", 1L, "alexgaoyh", "36", "alexgaoyh@mail.com", "INIT");

        var rows = jdbcTemplate.queryForList("SELECT * FROM user_info");

        String template = "UPDATE %s SET %s = '%s' WHERE %s = '%s';";
        for (var row : rows) {
            String table = "user_info";
            String setColumn = "status";
            String setValue = "UPDATED";
            String whereColumn = "id";
            Object whereValue = row.get("ID");
            String updateSql = String.format(template, table, setColumn, setValue, whereColumn, whereValue);
            System.out.println(updateSql);
        }
    }

    @Test
    @DisplayName("幻读")
    void phantomReadTest() throws Exception {

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        CountDownLatch t1Ready = new CountDownLatch(1);
        CountDownLatch t2Done = new CountDownLatch(1);

        // 先插入一条行，让 T1 的快照不为空
        jdbcTemplate.update("INSERT INTO user_info (id, name, age, email, status) VALUES (?, ?, ?, ?, ?)", "1", "init-user", "2", "3", "INIT");

        ExecutorService pool = new ThreadPoolExecutor(
                2,
                2,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                r -> new Thread(r, "pool-test-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        try {
            // T1：读取范围
            Future<Integer> diffFuture = pool.submit(() -> tx.execute(status -> {
                // 第一次查询
                List<Long> first = jdbcTemplate.queryForList("SELECT id FROM user_info WHERE status = 'INIT'", Long.class);
                System.out.println("T1 first = " + first);

                t1Ready.countDown();

                try {
                    t2Done.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                // 第二次查询
                List<Long> second = jdbcTemplate.queryForList("SELECT id FROM user_info WHERE status = 'INIT'", Long.class);
                System.out.println("T1 second = " + second);

                return second.size() - first.size();
            }));

            // T2：插入新行
            pool.submit(() -> {
                try {
                    t1Ready.await();
                    tx.execute(status -> {
                        jdbcTemplate.update("INSERT INTO user_info (id, name, age, email, status) VALUES (?, ?, ?, ?, ?)", "2", "insert-user", "2", "3", "INIT");
                        return null;
                    });
                } catch (InterruptedException ignored) {
                } finally {
                    t2Done.countDown();
                }
            });

            Integer diff = diffFuture.get();


            System.out.println("phantom diff = " + diff);
        } finally {
            pool.shutdown();
            try {
                // 等待 2 秒让未完成的任务结束
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    // 超时后强制关闭，这会向所有池中线程发送 Interrupt 信号
                    log.warn("部分线程池任务未在 2 秒内结束，强制关闭");
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭线程池时被中断", e);
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    public void jdbcTemplateTest() throws Exception {
        int fetchSize = jdbcTemplate.getFetchSize();
        int maxRows = jdbcTemplate.getMaxRows();
        System.out.println("fetchSize = " + fetchSize);
        System.out.println("maxRows = " + maxRows);
    }

    /**
     * 本地mysql8 的环境下，关闭其他所有软件（避免其他软件影响）
     * 不完备的测试下，如下的对比不明显，没有说谁比谁更快。
     */
    // @Test
    @DisplayName("分批查询 vs 全量查询的执行时间 (已排除 Java 层拼接与 GC 干扰)")
    void testExecutionEfficiencyFlipOptimized() {

        // 1. 准备测试数据
        int queryCount = 50000;
        List<Long> extremeIds = new java.util.ArrayList<>(queryCount);
        for (long i = 1; i <= queryCount; i++) {
            extremeIds.add(i);
        }

        // 将 ID 拆分为多个批次
        int batchSize = 5000;
        List<List<Long>> batches = new java.util.ArrayList<>();
        for (int i = 0; i < extremeIds.size(); i += batchSize) {
            batches.add(extremeIds.subList(i, Math.min(i + batchSize, extremeIds.size())));
        }

        System.out.println("开始预先构建 SQL 和参数 (剔除测试期间的 JVM 内存分配开销)...");

        // --- 提取全量查询的准备工作 ---
        String fullSql = buildInSql(extremeIds.size());
        Object[] fullArgs = extremeIds.toArray(); // 提前转好数组

        // --- 提取分批查询的准备工作 ---
        List<String> batchSqls = new java.util.ArrayList<>(batches.size());
        List<Object[]> batchArgs = new java.util.ArrayList<>(batches.size());
        for (List<Long> batch : batches) {
            batchSqls.add(buildInSql(batch.size()));
            batchArgs.add(batch.toArray()); // 提前转好数组
        }

        // 统一的 RowMapper
        org.springframework.jdbc.core.RowMapper<Long> rowMapper = (rs, rowNum) -> rs.getLong("id");

        // 3. 预热阶段 (Warm-up)
        System.out.println("开始预热 (让数据进入 MySQL Buffer Pool，并让 JDBC 缓存 PreparedStatement)...");
        for (int i = 0; i < 3; i++) {
            jdbcTemplate.query(fullSql, rowMapper, fullArgs);
            for (int j = 0; j < batchSqls.size(); j++) {
                jdbcTemplate.query(batchSqls.get(j), rowMapper, batchArgs.get(j));
            }
        }
        System.out.println("预热完成，开始正式测速。\n");

        // 4. 正式对比耗时
        int testRounds = 50;

        // --- 方案 A: 单次全量 IN ---
        // 此时循环内纯粹是网络 I/O 和 MySQL 执行的时间
        long startFull = System.nanoTime();
        for (int i = 0; i < testRounds; i++) {
            jdbcTemplate.query(fullSql, rowMapper, fullArgs);
        }
        long timeFull = System.nanoTime() - startFull;

        // --- 方案 B: 分批 IN ---
        // 此时循环内也没有任何多余的字符串或对象创建
        long startBatch = System.nanoTime();
        for (int i = 0; i < testRounds; i++) {
            for (int j = 0; j < batchSqls.size(); j++) {
                jdbcTemplate.query(batchSqls.get(j), rowMapper, batchArgs.get(j));
            }
        }
        long timeBatch = System.nanoTime() - startBatch;

        // 5. 打印结果
        double fullMs = timeFull / 1_000_000.0;
        double batchMs = timeBatch / 1_000_000.0;
        System.out.printf("单次带 %d 个 ID 的全量 IN 查询 (执行 %d 轮) 总耗时: %.2f ms%n", queryCount, testRounds, fullMs);
        System.out.printf("拆分为 %d 批次的 IN 查询 (执行 %d 轮) 总耗时:     %.2f ms%n", batches.size(), testRounds, batchMs);
        System.out.println("=========================================================");
    }

    /**
     * 辅助方法：仅负责生成带占位符的 SQL 字符串
     */
    private String buildInSql(int count) {
        String placeholders = String.join(",", java.util.Collections.nCopies(count, "?"));
        return "SELECT * FROM user_info WHERE id IN (" + placeholders + ")";
    }

    @Test
    @DisplayName("验证 cachePrepStmts=true 的性能差异")
    void testCachePrepStmtsDifference() throws Exception {
        // 1. 插入一点基础数据（保证查询有结果）
        jdbcTemplate.update("INSERT IGNORE INTO user_info (id, name, age) VALUES (1, 'cache-test', 20)");

        // 基础 URL (替换为你真实的密码，这里沿用你 Config 中的配置)
        String baseUrl = "jdbc:mysql://127.0.0.1:3306/cf?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        String username = "root";
        String password = "alexgaoyh";

        // URL 1: 不开启缓存 (默认情况)
        String urlWithoutCache = baseUrl;

        // URL 2: 开启缓存及配套参数
        String urlWithCache = baseUrl + "&cachePrepStmts=true&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048&useServerPrepStmts=true";

        System.out.println("================ 开始测试 cachePrepStmts ===============");

        // 2. 测试不带缓存的性能
        long timeWithoutCache = runIntensiveQueries("未开启缓存 (Without Cache)", urlWithoutCache, username, password);

        // 3. 测试带缓存的性能
        long timeWithCache = runIntensiveQueries("已开启缓存 (With Cache)", urlWithCache, username, password);

        // 4. 输出对比
        System.out.println("================ 测试结果总结 ==========================");
        System.out.printf("未开启缓存耗时: %d ms%n", timeWithoutCache);
        System.out.printf("已开启缓存耗时: %d ms%n", timeWithCache);

        if (timeWithCache < timeWithoutCache) {
            double improve = (double)(timeWithoutCache - timeWithCache) / timeWithoutCache * 100;
            System.out.printf("性能提升: %.2f%%%n", improve);
        } else {
            System.out.println("在当前极为短暂的测试中未体现出明显优势，可能受JIT或网络波动影响，建议增加循环次数。");
        }
    }

    /**
     * 辅助方法：使用指定的 URL 构建独立的数据源，并执行密集查询
     */
    private long runIntensiveQueries(String label, String url, String username, String password) {
        // 使用 HikariCP 手动构建独立的数据源
        com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(1); // 限制池大小，确保复用同一个 Connection

        try (com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource(config)) {
            JdbcTemplate testJt = new JdbcTemplate(ds);
            String sql = "SELECT name FROM user_info WHERE id = ?";

            // 使用 ResultSetExtractor 避免 RowMapper 的对象创建开销，纯测 JDBC 驱动性能
            org.springframework.jdbc.core.ResultSetExtractor<String> extractor = rs -> {
                if (rs.next()) return rs.getString(1);
                return null;
            };

            int warmUpCount = 10000;
            int testCount = 50000;

            System.out.println(label + " -> 正在预热...");
            // 预热：让 JVM JIT 编译器完成字节码到机器码的编译
            for (int i = 0; i < warmUpCount; i++) {
                testJt.query(sql, extractor, 1L);
            }

            System.out.println(label + " -> 正式开始计时...");
            long startTime = System.currentTimeMillis();

            // 正式测试
            for (int i = 0; i < testCount; i++) {
                testJt.query(sql, extractor, 1L);
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            System.out.printf("%s 测试完成，耗时: %d ms%n", label, duration);
            System.out.println("------------------------------------------------------");
            return duration;
        }
    }


}