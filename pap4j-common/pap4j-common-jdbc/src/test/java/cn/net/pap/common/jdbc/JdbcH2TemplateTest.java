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
@ContextConfiguration(classes = JdbcH2TemplateTest.Config.class)
class JdbcH2TemplateTest {

    @Configuration
    static class Config {

        @Bean
        DataSource dataSource() {
            return DataSourceBuilder.create()
                    .driverClassName("org.h2.Driver")
                    .url("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
                    .username("sa")
                    .password("")
                    .build();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource ds) {
            return new JdbcTemplate(ds);
        }
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void testUpdate1() {
        jdbcTemplate.execute(
        """
            CREATE TABLE user_info (
                id BIGINT PRIMARY KEY,
                name VARCHAR(50),
                age VARCHAR(50),
                status VARCHAR(20)
            )
            """
        );

        jdbcTemplate.update("INSERT INTO user_info VALUES (?, ?, ?, ?)", 1L, "alexgaoyh", "36", "INIT");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM user_info");

        String template = "UPDATE %s SET %s = '%s' WHERE %s = '%s';";
        for (Map<String, Object> row : rows) {
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
