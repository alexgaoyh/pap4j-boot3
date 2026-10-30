package cn.net.pap.common.jdbc;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DuckDB 读取本地 Parquet 文件的集成测试。
 *
 * <p>本地文件不存在时通过 {@link Assumptions} 跳过，便于在无测试数据的机器上执行构建。</p>
 */
public class JdbcDuckDBParquetTest {

    private static final Logger log = LoggerFactory.getLogger(JdbcDuckDBParquetTest.class);

    /**
     * 读取本地 Parquet 文件，输出记录总数并预览部分记录内容。
     *
     * @throws SQLException 连接 DuckDB 或执行查询失败时抛出
     */
    @Test
    @DisplayName("DuckDB 读取本地 Parquet 文件")
    public void readTest() throws SQLException {
        Path parquetFile = Paths.get("rank_00037.parquet");
        Assumptions.assumeTrue(Files.isRegularFile(parquetFile), "本地 Parquet 文件不存在，跳过测试: " + parquetFile);
        String parquetUrl = parquetFile.toAbsolutePath().toString().replace("\\", "/");

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:"); Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT _id, text FROM '" + parquetUrl + "'")) {
                int index = 0;
                while (index < 5 && rs.next()) {
                    index++;
                    log.info("第 {} 条记录, _id: {}", index, rs.getString("_id"));
                }
            }
        }
    }

}
