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
import java.sql.SQLException;
import java.util.List;

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

}
