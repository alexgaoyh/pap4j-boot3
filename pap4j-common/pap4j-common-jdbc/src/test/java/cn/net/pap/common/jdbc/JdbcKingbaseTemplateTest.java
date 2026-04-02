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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JdbcKingbaseTemplateTest.Config.class)
@org.springframework.test.context.TestConstructor(autowireMode = org.springframework.test.context.TestConstructor.AutowireMode.ALL)
class JdbcKingbaseTemplateTest {

    private static final Logger log = LoggerFactory.getLogger(JdbcKingbaseTemplateTest.class);

    @Configuration
    static class Config {

        @Bean
        DataSource dataSource() {
            return DataSourceBuilder.create().driverClassName("com.kingbase8.Driver")
                    .url("jdbc:kingbase8://127.0.0.1:54321/cf?zeroDateTimeBehavior=convertToNull&useUnicode=true&characterEncoding=utf-8")
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

    public JdbcKingbaseTemplateTest(JdbcTemplate jdbcTemplate, DataSource dataSource, PlatformTransactionManager transactionManager) {
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


}