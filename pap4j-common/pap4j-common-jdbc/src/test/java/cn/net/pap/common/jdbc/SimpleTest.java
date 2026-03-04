package cn.net.pap.common.jdbc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class SimpleTest {

    static {
        System.setProperty("hsqldb.method_class_names", "net.ucanaccess.*");
    }

    public record TestRecord(String id, String temp) {
    }

    @Test
    @DisplayName("手动初始化并查询数据")
    void testStandaloneJdbcQuery() throws Exception {
        String dbPath = "C:\\Users\\86181\\Desktop\\test.mdb";
        String url = "jdbc:ucanaccess://" + dbPath + ";sysline=true;memory=false";
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("net.ucanaccess.jdbc.UcanaccessDriver");
        dataSource.setUrl(url);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try {
            // get tableName
            List<String> tableNames = jdbcTemplate.execute((java.sql.Connection con) -> {
                List<String> names = new ArrayList<>();
                DatabaseMetaData metaData = con.getMetaData();
                try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        names.add(rs.getString("TABLE_NAME"));
                    }
                }
                return names;
            });
            tableNames.forEach(System.out::println);

            String sql = "SELECT ID as id, 文字 as temp FROM [CZYX]";
            List<TestRecord> testRecordList = jdbcTemplate.query(sql, new DataClassRowMapper<>(TestRecord.class));
            if (testRecordList != null && !testRecordList.isEmpty()) {
                testRecordList.forEach(System.out::println);
            }
        } finally {
            try {
                jdbcTemplate.execute("SHUTDOWN");
                System.out.println("HSQLDB 引擎已显式关闭，文件锁已释放。");
            } catch (Exception e) {
            }
        }
    }

}
