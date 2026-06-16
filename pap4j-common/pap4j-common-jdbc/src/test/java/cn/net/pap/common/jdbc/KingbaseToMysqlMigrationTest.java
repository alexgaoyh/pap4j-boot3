package cn.net.pap.common.jdbc;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Kingbase V8R6 -> MySQL 8.0 全量Schema迁移测试
 */
public class KingbaseToMysqlMigrationTest {

    private static final Logger log = LoggerFactory.getLogger(KingbaseToMysqlMigrationTest.class);

    private static final String KINGBASE_URL = "";

    private static final String KINGBASE_USER = "";
    private static final String KINGBASE_PASSWORD = "";

    private static final String MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&rewriteBatchedStatements=true&serverTimezone=Asia/Shanghai";

    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "alexgaoyh";

    /**
     * 需要迁移的schema
     */
    private static final String SCHEMA = "test";

    private static final int BATCH_SIZE = 100;

    private static final String KINGBASE_DRIVER = "com.kingbase8.Driver";
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";

    private static final String DISABLE_FOREIGN_KEY_CHECKS = "SET FOREIGN_KEY_CHECKS=0";
    private static final String ENABLE_FOREIGN_KEY_CHECKS = "SET FOREIGN_KEY_CHECKS=1";

    @Test
    void migrateDatabase() throws Exception {

        Class.forName(KINGBASE_DRIVER);
        Class.forName(MYSQL_DRIVER);

        try {
            try (Connection source = DriverManager.getConnection(KINGBASE_URL, KINGBASE_USER, KINGBASE_PASSWORD); Connection target = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD)) {

                target.setAutoCommit(false);

                try {
                    execute(target, DISABLE_FOREIGN_KEY_CHECKS);
                    List<String> tables = listTables(source);
                    log.info("发现表数量: {}", tables.size());
                    long start = System.currentTimeMillis();
                    int index = 1;

                    for (String table : tables) {
                        log.info("[{}/{}] 开始迁移: {}", index++, tables.size(), table);
                        createTable(source, target, table);
                        migrateTable(source, target, table);
                        log.info("完成: {}", table);
                    }

                    log.info("全部完成,耗时: {} 秒", (System.currentTimeMillis() - start) / 1000);
                } finally {
                    try {
                        execute(target, ENABLE_FOREIGN_KEY_CHECKS);
                    } catch (Exception e) {
                    }
                }
            }
        } catch (Exception e) {

        }
    }


    private List<String> listTables(Connection conn) throws SQLException {

        String sql = """
                select table_name
                from information_schema.tables
                where table_schema=?
                and table_type='BASE TABLE'
                order by table_name
                """;

        List<String> result = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, SCHEMA);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        }

        return result;
    }


    private void createTable(Connection source, Connection target, String table) throws Exception {
        String sql = "select * from %s where 1=2".formatted(quote(table));

        try (Statement st = source.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            StringBuilder ddl = new StringBuilder();
            ddl.append("create table if not exists ").append(quoteMysql(table)).append("(");
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                if (i > 1) {
                    ddl.append(",");
                }
                ddl.append(quoteMysql(meta.getColumnName(i))).append(" ").append(mapType(meta.getColumnType(i), meta.getColumnDisplaySize(i), meta.getPrecision(i), meta.getScale(i)));
            }
            ddl.append(")");
            execute(target, ddl.toString());
        }
    }


    private void migrateTable(Connection source, Connection target, String table) throws Exception {
        String query = "select * from %s".formatted(quote(table));

        boolean originalAutoCommit = source.getAutoCommit();
        if (originalAutoCommit) {
            source.setAutoCommit(false);
        }
        try {
            try (Statement st = source.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                st.setFetchSize(BATCH_SIZE);
                log.info("正在执行 SQL 查询并准备流式拉取数据...");
                try (ResultSet rs = st.executeQuery(query)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columns = meta.getColumnCount();
                    int[] columnTypes = new int[columns + 1];
                    for (int i = 1; i <= columns; i++) {
                        columnTypes[i] = meta.getColumnType(i);
                    }
                    String insert = buildInsert(table, meta);
                    try (PreparedStatement ps = target.prepareStatement(insert)) {
                        long current = 0;
                        log.info("开始从 Kingbase 流式读取并批量写入 MySQL...");
                        while (rs.next()) {
                            for (int i = 1; i <= columns; i++) {
                                if (columnTypes[i] == Types.OTHER) {
                                    ps.setString(i, rs.getString(i));
                                } else {
                                    ps.setObject(i, rs.getObject(i));
                                }
                            }
                            ps.addBatch();
                            current++;
                            if (current % BATCH_SIZE == 0) {
                                ps.executeBatch();
                                target.commit();
                                printProgress(current);
                            }
                        }
                        ps.executeBatch();
                        target.commit();
                        printProgress(current);
                    }
                }
            }
            if (originalAutoCommit) {
                source.commit();
            }
        } finally {
            if (originalAutoCommit) {
                source.setAutoCommit(true);
            }
        }
    }

    private String buildInsert(String table, ResultSetMetaData meta) throws SQLException {

        List<String> cols = new ArrayList<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            cols.add(quoteMysql(meta.getColumnName(i)));
        }
        String columnsStr = String.join(",", cols);
        String placeholders = cols.stream().map(x -> "?").collect(Collectors.joining(","));
        return "insert into %s(%s) values(%s)".formatted(quoteMysql(table), columnsStr, placeholders);
    }


    private String mapType(int jdbcType, int length, int precision, int scale) {
        return switch (jdbcType) {
            case Types.VARCHAR, Types.CHAR -> {

                int size = length > 0 ? length : 255;
                if (size > 65535) {
                    yield "text";
                }
                yield "varchar(%d)".formatted(size);
            }
            case Types.LONGVARCHAR -> "longtext";
            case Types.INTEGER -> "int";
            case Types.BIGINT -> "bigint";
            case Types.NUMERIC, Types.DECIMAL -> {
                int p = precision > 0 ? precision : 20;
                int s = scale >= 0 ? scale : 0;
                yield "decimal(%d,%d)".formatted(p, s);
            }
            case Types.DATE -> "date";
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "datetime";
            case Types.BOOLEAN, Types.BIT -> "tinyint(1)";
            case Types.BLOB, Types.LONGVARBINARY -> "longblob";
            case Types.VARBINARY -> "varbinary(%d)".formatted(length > 0 ? length : 255);
            case Types.OTHER -> "json";
            default -> "text";
        };
    }

    private void printProgress(long current) {
        log.info("  - 进度: 已迁移 {} 条记录", current);
    }

    private String quote(String name) {
        return "\"" + name + "\"";
    }

    private String quoteMysql(String name) {
        return "`" + name + "`";
    }

    private void execute(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

}
