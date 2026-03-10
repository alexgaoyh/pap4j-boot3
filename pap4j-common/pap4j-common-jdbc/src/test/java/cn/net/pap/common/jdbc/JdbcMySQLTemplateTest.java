package cn.net.pap.common.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DataSource dataSource;

    @Autowired
    PlatformTransactionManager transactionManager;

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


}