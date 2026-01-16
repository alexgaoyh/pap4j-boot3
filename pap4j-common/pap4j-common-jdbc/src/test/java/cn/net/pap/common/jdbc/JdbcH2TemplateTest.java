package cn.net.pap.common.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JdbcH2TemplateTest.Config.class)
class JdbcH2TemplateTest {

    @Configuration
    static class Config {

        @Bean
        DataSource dataSource() {
            return DataSourceBuilder.create().driverClassName("org.h2.Driver").url("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL").username("sa").password("").build();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource ds) {
            return new JdbcTemplate(ds);
        }
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DataSource dataSource;

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

            try (Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);){
                // 关键：设置fetchSize启用流式获取
                stmt.setFetchSize(1000);

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
    @DisplayName("测试JdbcTemplate的流式查询")
    void testJdbcTemplateStreaming() {
        // 插入5万条测试数据
        insertLargeTestData(50000);

        AtomicInteger rowCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        // 使用JdbcTemplate的query方法进行流式处理
        jdbcTemplate.query("SELECT * FROM user_info ORDER BY id", new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                rowCount.incrementAndGet();

                // 处理每一行数据
                long id = rs.getLong("id");
                String name = rs.getString("name");
                int age = rs.getInt("age");

                // 验证数据
                assertThat(id).isGreaterThan(0);
                assertThat(name).isNotNull();
                assertThat(age).isBetween(20, 70);

                // 每处理5000条记录输出一次进度
                if (rowCount.get() % 5000 == 0) {
                    System.out.printf("JdbcTemplate处理进度: %d 条记录，当前ID: %d%n", rowCount.get(), id);
                }
            }
        });

        long endTime = System.currentTimeMillis();

        System.out.printf("✅ JdbcTemplate流式查询完成！\n");
        System.out.printf("总记录数: %d\n", rowCount.get());
        System.out.printf("处理时间: %.2f秒\n", (endTime - startTime) / 1000.0);

        // 验证读取了所有记录
        assertEquals(50000, rowCount.get());
    }

    @Test
    @DisplayName("测试流式导出到CSV文件")
    void testStreamingExportToCsv() throws IOException, SQLException {
        // 插入20万条测试数据
        insertLargeTestData(200000);

        // 创建临时CSV文件
        Path tempFile = Files.createTempFile("user_export_", ".csv");
        System.out.println("CSV文件路径: " + tempFile.toAbsolutePath());

        long startTime = System.currentTimeMillis();
        AtomicInteger rowCount = new AtomicInteger(0);

        try (BufferedWriter writer = Files.newBufferedWriter(tempFile); Connection conn = dataSource.getConnection();) {
            try (Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY); ){
                // 设置fetchSize启用流式
                stmt.setFetchSize(2000);
                try (ResultSet rs = stmt.executeQuery("SELECT id, name, age, email, status, created_at FROM user_info ORDER BY id")){
                    // 写入CSV表头
                    writer.write("ID,Name,Age,Email,Status,CreatedAt");
                    writer.newLine();

                    System.out.println("开始流式导出到CSV...");

                    while (rs.next()) {
                        // 构建CSV行
                        String csvLine = String.format("%d,%s,%d,%s,%s,%s", rs.getLong("id"), escapeCsv(rs.getString("name")), rs.getInt("age"), escapeCsv(rs.getString("email")), rs.getString("status"), rs.getTimestamp("created_at"));

                        writer.write(csvLine);
                        writer.newLine();

                        rowCount.incrementAndGet();

                        // 每10000行刷新一次缓冲区
                        if (rowCount.get() % 10000 == 0) {
                            writer.flush();
                            System.out.printf("已导出 %d 条记录到CSV%n", rowCount.get());
                        }
                    }

                    // 最后刷新一次
                    writer.flush();
                }
            }

        } catch (SQLException | IOException e) {
            fail("导出失败: " + e.getMessage());
        }

        long endTime = System.currentTimeMillis();

        // 验证导出结果
        assertEquals(200000, rowCount.get());

        // 验证文件存在且有内容
        assertTrue(Files.exists(tempFile));
        assertTrue(Files.size(tempFile) > 0);

        System.out.printf("✅ CSV导出完成！\n");
        System.out.printf("文件大小: %.2f MB\n", Files.size(tempFile) / (1024.0 * 1024.0));
        System.out.printf("导出时间: %.2f秒\n", (endTime - startTime) / 1000.0);
        System.out.printf("总记录数: %d\n", rowCount.get());

        // 清理临时文件
        Files.deleteIfExists(tempFile);
    }

    @Test
    @DisplayName("测试流式查询的内存效率")
    void testMemoryEfficiency() throws SQLException, InterruptedException {
        // 插入15万条测试数据
        insertLargeTestData(150000);

        // 强制GC，获得基准内存
        System.gc();
        Thread.sleep(1000);

        long baselineMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        System.out.printf("基准内存使用: %.2f MB\n", baselineMemory / (1024.0 * 1024.0));

        try (Connection conn = dataSource.getConnection(); ) {
            try (Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY); ){
                stmt.setFetchSize(500);  // 设置较小的fetchSize
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM user_info")){
                    int totalRows = 0;
                    double maxMemoryIncrease = 0;

                    while (rs.next()) {
                        totalRows++;

                        // 每处理5000条记录检查一次内存
                        if (totalRows % 5000 == 0) {
                            long currentMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                            double memoryIncrease = (currentMemory - baselineMemory) / (1024.0 * 1024.0);

                            maxMemoryIncrease = Math.max(maxMemoryIncrease, memoryIncrease);

                            // 关键断言：内存增长应该有限
                            assertThat(memoryIncrease).withFailMessage("处理 %d 条记录后内存增长 %.2f MB，可能发生了全量加载", totalRows, memoryIncrease).isLessThan(50.0);  // 内存增长不应超过50MB

                            System.out.printf("已处理 %d 条记录，当前内存增量: %.2f MB\n", totalRows, memoryIncrease);
                        }
                    }

                    System.out.printf("✅ 流式查询完成，共处理 %d 条记录，最大内存增量: %.2f MB\n", totalRows, maxMemoryIncrease);

                    assertEquals(150000, totalRows);
                }
            }
        }
    }

    @Test
    @DisplayName("测试流式处理中的分页与流式性能对比")
    void testPaginationVsStreamingPerformance() throws SQLException {
        // 插入8万条测试数据
        insertLargeTestData(80000);

        System.out.println("\n🔬 性能对比测试：分页 vs 流式");

        // 1. 测试分页查询性能
        long paginationStart = System.currentTimeMillis();
        int pageSize = 1000;
        int totalPages = 80; // 80000 / 1000
        int paginationRowCount = 0;

        for (int page = 0; page < totalPages; page++) {
            int offset = page * pageSize;
            String sql = String.format("SELECT * FROM user_info ORDER BY id LIMIT %d OFFSET %d", pageSize, offset);

            jdbcTemplate.query(sql, rs -> {
                // 简单的计数
            });

            paginationRowCount += pageSize;

            if ((page + 1) % 10 == 0) {
                System.out.printf("分页查询进度: %d/%d 页\n", page + 1, totalPages);
            }
        }

        long paginationEnd = System.currentTimeMillis();
        long paginationTime = paginationEnd - paginationStart;

        // 2. 测试流式查询性能
        long streamingStart = System.currentTimeMillis();
        AtomicInteger streamingRowCount = new AtomicInteger(0);

        try (Connection conn = dataSource.getConnection(); ) {
            try (Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY); ) {
                stmt.setFetchSize(1000);
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM user_info ORDER BY id")){
                    while (rs.next()) {
                        streamingRowCount.incrementAndGet();
                    }
                }
                long streamingEnd = System.currentTimeMillis();
                long streamingTime = streamingEnd - streamingStart;

                System.out.println("\n📊 性能对比结果:");
                System.out.printf("分页查询: %d 条记录，耗时 %.2f 秒\n", paginationRowCount, paginationTime / 1000.0);
                System.out.printf("流式查询: %d 条记录，耗时 %.2f 秒\n", streamingRowCount.get(), streamingTime / 1000.0);
                System.out.printf("性能提升: %.1f%%\n", ((double) (paginationTime - streamingTime) / paginationTime) * 100);

                // 验证两种方式都处理了所有记录
                assertEquals(paginationRowCount, streamingRowCount.get());
            }
        }
    }

    /**
     * CSV转义辅助方法
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // 如果包含逗号、引号或换行符，需要用引号包裹
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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
}