package cn.net.pap.common.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JdbcAccessTemplateTest.Config.class)
@org.springframework.test.context.TestConstructor(autowireMode = org.springframework.test.context.TestConstructor.AutowireMode.ALL)
public class JdbcAccessTemplateTest {
    private static final Logger log = LoggerFactory.getLogger(JdbcAccessTemplateTest.class);

    static {
        System.setProperty("hsqldb.method_class_names", "net.ucanaccess.*");
    }

    @Configuration
    static class Config implements DisposableBean {

        private String mdbPath;

        @Bean
        DataSource dataSource() {
            String mdb = TestResourceUtil.getFile("access.mdb").getAbsolutePath().toString();
            this.mdbPath = mdb;
            return DataSourceBuilder.create().driverClassName("net.ucanaccess.jdbc.UcanaccessDriver")
                    .url("jdbc:ucanaccess://" + mdb + ";memory=false;singleConnection=true").build();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource ds) {
            return new JdbcTemplate(ds);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource ds) {
            return new DataSourceTransactionManager(ds);
        }

        @Override
        public void destroy(){
            log.info("正在关闭数据库连接并清理文件...");
            // 1. Ucanaccess 在所有连接关闭后会自动释放文件锁
            // 2. 执行删除逻辑
            if (mdbPath != null) {
                File file = new File(mdbPath);
                if (file.exists()) {
                    boolean deleted = file.delete();
                    log.info("{}", "临时数据库文件删除状态: " + deleted);
                }
            }
        }
    }

    public record TestRecord(String id, String temp) {
    }

    private final JdbcTemplate jdbcTemplate;

    public JdbcAccessTemplateTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("查询数据")
    void testJdbcStreamingQuery() throws SQLException {
        try {
            String sql = "SELECT ID as id, null as temp FROM [Users]";
            List<TestRecord> testRecordList = jdbcTemplate.query(sql, new DataClassRowMapper<>(TestRecord.class));
            if (testRecordList != null && testRecordList.size() > 0) {
                for (TestRecord testRecord : testRecordList) {
                    log.info("{}", testRecord);
                }
            }
        } catch (Exception e) {
        }
    }

    @Test
    @DisplayName("创建新 mdb 数据库并插入数据")
    void testCreateNewDatabaseAndInsertData() throws Exception {
        File newDbFile = Files.createTempFile("testCreateNewDatabaseAndInsertData", ".mdb").toFile();

        try {
            // 如果文件已存在，先删除，确保每次测试都是从零开始创建
            if (newDbFile.exists()) {
                boolean deleted = newDbFile.delete();
                log.info("{}", "清理旧的测试文件: " + deleted);
            }

            // 2. 构造 JDBC URL，关键参数：;newdatabaseversion=V2003
            // V2003 代表创建 Access 2002/2003 格式的 .mdb 文件
            // V2010 代表创建 Access 2010 格式的 .accdb 文件
            String url = "jdbc:ucanaccess://" + newDbFile.getAbsolutePath() + ";newdatabaseversion=V2003";

            try {
                // 3. 在测试方法内部创建一个独立的 DataSource 和 JdbcTemplate
                org.springframework.jdbc.datasource.DriverManagerDataSource localDataSource =
                        new org.springframework.jdbc.datasource.DriverManagerDataSource();
                localDataSource.setDriverClassName("net.ucanaccess.jdbc.UcanaccessDriver");
                localDataSource.setUrl(url);

                JdbcTemplate localJdbcTemplate = new JdbcTemplate(localDataSource);

                // 4. 创建新表 (COUNTER 代表 Access 中的自动编号主键)
                log.info("正在创建新表 [Users]...");
                String createTableSql = "CREATE TABLE Users (" +
                        "id COUNTER PRIMARY KEY, " +
                        "username VARCHAR(50), " +
                        "age INT, " +
                        "status TEXT(100)" +
                        ")";
                localJdbcTemplate.execute(createTableSql);
                log.info("建表成功！");

                // 5. 插入数据
                log.info("正在插入数据...");
                String insertSql = "INSERT INTO Users (username, age, status) VALUES (?, ?, ?)";
                localJdbcTemplate.update(insertSql, "张三", 25, "活跃");
                localJdbcTemplate.update(insertSql, "李四", 30, "离线");
                localJdbcTemplate.update(insertSql, "王五", 28, "活跃");
                log.info("数据插入成功！");

                // 6. 查询并验证数据
                log.info("查询刚插入的数据:");
                List<Map<String, Object>> results = localJdbcTemplate.queryForList("SELECT * FROM Users");
                for (Map<String, Object> row : results) {
                    log.info("{}", row);
                }

            } catch (Exception e) {
                System.err.println("操作失败: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            if (newDbFile != null && newDbFile.exists()) {
                newDbFile.delete();
            }
        }
    }

}
