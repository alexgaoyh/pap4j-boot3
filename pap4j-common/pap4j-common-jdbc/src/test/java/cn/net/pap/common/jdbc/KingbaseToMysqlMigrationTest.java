package cn.net.pap.common.jdbc;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mysql.cj.jdbc.JdbcStatement;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Kingbase V8R6 -> MySQL 8.0 全量Schema迁移测试
 */
public class KingbaseToMysqlMigrationTest {

    private static final Logger log = LoggerFactory.getLogger(KingbaseToMysqlMigrationTest.class);

    private static final String KINGBASE_URL = "";

    private static final String KINGBASE_USER = "";
    private static final String KINGBASE_PASSWORD = "";

    private static final String MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&serverTimezone=Asia/Shanghai&allowLoadLocalInfile=true";

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
        if (KINGBASE_URL == null || KINGBASE_URL.isBlank() || MYSQL_URL == null || MYSQL_URL.isBlank()) {
            log.warn("数据库连接配置（KINGBASE_URL / MYSQL_URL）为空，跳过数据迁移测试。");
            return;
        }

        Class.forName(KINGBASE_DRIVER);
        Class.forName(MYSQL_DRIVER);

        // 尝试动态启用 MySQL 服务端的 local_infile 参数
        try (Connection target = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD)) {
            try (Statement st = target.createStatement()) {
                st.execute("SET GLOBAL local_infile = 1");
                log.info("成功动态启用 MySQL 服务端 local_infile 参数");
            }
        } catch (Exception e) {
            log.warn("尝试启用 MySQL 服务端 local_infile 失败（可能缺少权限），如果服务端未手动开启，迁移可能会失败: ", e);
        }

        List<String> tables;
        try (Connection source = DriverManager.getConnection(KINGBASE_URL, KINGBASE_USER, KINGBASE_PASSWORD)) {
            tables = listTables(source);
        }

        log.info("发现表数量: {}", tables.size());
        if (tables.isEmpty()) {
            return;
        }

        long start = System.currentTimeMillis();
        runParallelMigration(tables);
        log.info("全部完成,耗时: {} 秒", (System.currentTimeMillis() - start) / 1000);
    }

    private void runParallelMigration(List<String> tables) throws Exception {
        int threadCount = Math.min(4, tables.size());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threadCount,
                threadCount,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(tables.size() + 1),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "migration-worker-" + counter.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int total = tables.size();
        for (int i = 0; i < total; i++) {
            String table = tables.get(i);
            int index = i + 1;
            CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> migrateSingleTable(table, index, total),
                    executor
            );
            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.error("部分表迁移失败: ", e);
            throw new RuntimeException("部分表迁移失败", e);
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    private void migrateSingleTable(String table, int index, int total) {
        log.info("[{}/{}] [线程 {}] 开始迁移表: {}", index, total, Thread.currentThread().getName(), table);
        try (Connection source = DriverManager.getConnection(KINGBASE_URL, KINGBASE_USER, KINGBASE_PASSWORD);
             Connection target = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD)) {

            execute(target, DISABLE_FOREIGN_KEY_CHECKS);
            target.setAutoCommit(false);

            createTable(source, target, table);
            migrateTable(source, target, table);

            log.info("[{}/{}] [线程 {}] 完成表迁移: {}", index, total, Thread.currentThread().getName(), table);
        } catch (Exception e) {
            log.error("迁移表 {} 发生错误: ", table, e);
            throw new RuntimeException("表 " + table + " 迁移失败", e);
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
        // 1. 动态构建字段列表，构造 LOAD DATA SQL
        List<String> colNames = new ArrayList<>();
        int columns = 0;
        try (Statement st = source.createStatement();
             ResultSet rs = st.executeQuery("select * from %s where 1=2".formatted(quote(table)))) {
            ResultSetMetaData meta = rs.getMetaData();
            columns = meta.getColumnCount();
            for (int i = 1; i <= columns; i++) {
                colNames.add(quoteMysql(meta.getColumnName(i)));
            }
        }
        String colNamesCsv = String.join(",", colNames);

        // 使用默认的 TSV 格式（制表符分隔，反斜杠转义）
        String sql = "LOAD DATA LOCAL INFILE '' INTO TABLE %s FIELDS TERMINATED BY '\\t' ESCAPED BY '\\\\' LINES TERMINATED BY '\\n' (%s)"
                .formatted(quoteMysql(table), colNamesCsv);

        // 2. 创建管道流（设置 1MB 缓冲区）
        PipedInputStream pin = new PipedInputStream(1024 * 1024);
        PipedOutputStream pout = new PipedOutputStream(pin);

        // 确保 Kingbase 连接关闭 AutoCommit，以便 setFetchSize(1000) 能够生效，进行真正的游标流式拉取
        boolean originalAutoCommit = source.getAutoCommit();
        if (originalAutoCommit) {
            source.setAutoCommit(false);
        }

        try {
            int finalColumns = columns;
            String query = "select * from %s".formatted(quote(table));

            // 3. 异步生产者线程：读取 Kingbase ResultSet 并向管道流中写入转义后的 TSV 数据
            CompletableFuture<Void> producerTask = CompletableFuture.runAsync(() -> {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(pout, StandardCharsets.UTF_8));
                     Statement st = source.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {

                    // 宽表场景下，Fetch Size 设为 1000 较合适，平衡吞吐和内存
                    st.setFetchSize(1000);
                    try (ResultSet rs = st.executeQuery(query)) {
                        long current = 0;
                        while (rs.next()) {
                            for (int i = 1; i <= finalColumns; i++) {
                                if (i > 1) {
                                    writer.write('\t');
                                }
                                Object val = rs.getObject(i);
                                if (val == null) {
                                    writer.write("\\N");
                                } else {
                                    String strVal = rs.getString(i);
                                    writer.write(escapeTsv(strVal));
                                }
                            }
                            writer.write('\n');
                            current++;
                            if (current % BATCH_SIZE == 0) {
                                printProgress(table, current);
                            }
                        }
                        printProgress(table, current);
                    }
                } catch (Exception e) {
                    log.error("生产者转换 TSV 写入流时发生异常: ", e);
                    throw new RuntimeException("TSV streaming generation failed", e);
                }
            });

            // 4. 消费者：MySQL 驱动从 PipedInputStream 读取流执行 LOAD DATA
            try (Statement st = target.createStatement()) {
                JdbcStatement mysqlSt = st.unwrap(JdbcStatement.class);
                mysqlSt.setLocalInfileInputStream(pin);

                log.info("[{}] 开始执行流式 LOAD DATA LOCAL INFILE...", table);
                mysqlSt.execute(sql);
                target.commit(); // 必须显式提交事务，因为 target 连接在 migrateSingleTable 中被设置为了 autoCommit = false
                log.info("[{}] 表 {} 流式迁移成功并已提交事务", table, table);
            }

            // 等待生产者线程完全结束，并捕获可能的异常
            producerTask.join();
        } finally {
            if (originalAutoCommit) {
                source.setAutoCommit(true);
            }
        }
    }

    private String escapeTsv(String value) {
        if (value == null) {
            return "\\N";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
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
                // MySQL 的 DECIMAL 最大精度限制为 65，超过 65 需要强制截断为 65
                if (p > 65) {
                    p = 65;
                }
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

    private void printProgress(String table, long current) {
        log.info("表 [{}] 进度: 已迁移 {} 条记录", table, current);
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
