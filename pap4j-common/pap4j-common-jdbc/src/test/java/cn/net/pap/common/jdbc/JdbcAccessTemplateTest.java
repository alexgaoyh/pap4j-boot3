package cn.net.pap.common.jdbc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JdbcAccessTemplateTest.Config.class)
public class JdbcAccessTemplateTest {

    static {
        System.setProperty("hsqldb.method_class_names", "net.ucanaccess.*");
    }

    @Configuration
    static class Config {

        @Bean
        DataSource dataSource() {
            return DataSourceBuilder.create().driverClassName("net.ucanaccess.jdbc.UcanaccessDriver")
                    .url("jdbc:ucanaccess://" + "C:\\Users\\86181\\Desktop\\test.mdb" + ";memory=false;singleConnection=true").build();
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

    public record TestRecord(String id, String temp) {
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    // @Test
    @DisplayName("查询数据")
    void testJdbcStreamingQuery() throws SQLException {
        try {
            String sql = "SELECT ID as id, 文字 as temp FROM [tableName]";
            List<TestRecord> testRecordList = jdbcTemplate.query(sql, new DataClassRowMapper<>(TestRecord.class));
            if (testRecordList != null && testRecordList.size() > 0) {
                for (TestRecord testRecord : testRecordList) {
                    System.out.println(testRecord);
                }
            }
        } catch (Exception e) {
        }
    }

    // @Test
    @DisplayName("创建新 mdb 数据库并插入数据")
    void testCreateNewDatabaseAndInsertData() {
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";
        File newDbFile = new File(desktop + File.separator + "new_test.mdb");

        // 如果文件已存在，先删除，确保每次测试都是从零开始创建
        if (newDbFile.exists()) {
            boolean deleted = newDbFile.delete();
            System.out.println("清理旧的测试文件: " + deleted);
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
            System.out.println("正在创建新表 [Users]...");
            String createTableSql = "CREATE TABLE Users (" +
                    "id COUNTER PRIMARY KEY, " +
                    "username VARCHAR(50), " +
                    "age INT, " +
                    "status TEXT(100)" +
                    ")";
            localJdbcTemplate.execute(createTableSql);
            System.out.println("建表成功！");

            // 5. 插入数据
            System.out.println("正在插入数据...");
            String insertSql = "INSERT INTO Users (username, age, status) VALUES (?, ?, ?)";
            localJdbcTemplate.update(insertSql, "张三", 25, "活跃");
            localJdbcTemplate.update(insertSql, "李四", 30, "离线");
            localJdbcTemplate.update(insertSql, "王五", 28, "活跃");
            System.out.println("数据插入成功！");

            // 6. 查询并验证数据
            System.out.println("查询刚插入的数据:");
            List<Map<String, Object>> results = localJdbcTemplate.queryForList("SELECT * FROM Users");
            for (Map<String, Object> row : results) {
                System.out.println(row);
            }

        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
