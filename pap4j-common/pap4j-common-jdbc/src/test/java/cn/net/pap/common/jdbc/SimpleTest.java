package cn.net.pap.common.jdbc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class SimpleTest {

    private static final Logger log = LoggerFactory.getLogger(SimpleTest.class);

    static {
        System.setProperty("hsqldb.method_class_names", "net.ucanaccess.*");
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        } catch (ClassNotFoundException e) {
            log.error("UcanaccessDriver error", e);
        }
    }

    public record TestRecord(String id, String temp) {
    }

    @Test
    @DisplayName("手动初始化并查询数据")
    void testStandaloneJdbcQuery() throws Exception {
        String dbPath = TestResourceUtil.getFile("access.mdb").getAbsolutePath().toString();
        // memory=false（内存模式开关） 它的作用： 控制 HSQLDB 镜像数据库建在哪里。 默认情况下（memory=true），UCanAccess 会把整个数据库加载到 JVM 的**内存（RAM）**中。如果你的 .mdb 文件很小，这没问题，速度极快。
        // 当设置为 memory=false 时，它会将镜像数据库建立在硬盘的临时文件中，而不是塞进内存。

        // singleConnection=true（单连接优化开关） 它的作用： 告诉驱动程序“我现在处于单机、单线程、单连接的独立环境中”。 正常情况下，驱动需要处理多个连接同时读写 Access 文件的复杂锁机制和数据同步问题。
        // 设置为 true 后，UCanAccess 会跳过那些针对并发读写的复杂校验和资源锁定。
        String url = "jdbc:ucanaccess://" + dbPath + ";memory=false;singleConnection=true";

        // 利用 try-with-resources 自动管理生命周期，触发底层的自动清理
        try (Connection conn = DriverManager.getConnection(url)) {
            SingleConnectionDataSource dataSource = new SingleConnectionDataSource(conn, true);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

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
            tableNames.forEach(s -> log.info("{}", s));

            String sql = "SELECT ID as id, null as temp FROM [Users]";
            List<TestRecord> testRecordList = jdbcTemplate.query(sql, new DataClassRowMapper<>(TestRecord.class));
            if (testRecordList != null && !testRecordList.isEmpty()) {
                testRecordList.forEach(s -> log.info("{}", s));
            }
        }
        new File(dbPath).deleteOnExit();

    }

}
