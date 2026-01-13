package cn.net.pap.common.jdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JdbcMySQLTemplateTest.Config.class)
class JdbcMySQLTemplateTest {

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
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    private boolean isDatabaseConnected() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void testUpdate1() {
        if (!isDatabaseConnected()) {
            return;
        }

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_info (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(50),
                    age INT,
                    status VARCHAR(20)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.update("INSERT INTO user_info VALUES (?, ?, ?, ?)", 1L, "alexgaoyh", 36, "INIT");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM user_info");

        for (Map<String, Object> row : rows) {
            Object whereValue = row.get("id");
            String template = "UPDATE %s SET %s = '%s' WHERE %s = '%s';";
            String table = "user_info";
            String setColumn = "status";
            String setValue = "UPDATED";
            String whereColumn = "id";
            String updateSql = String.format(template, table, setColumn, setValue, whereColumn, whereValue);
            System.out.println(updateSql);
        }

        jdbcTemplate.update("DROP TABLE IF EXISTS user_info");

    }
}