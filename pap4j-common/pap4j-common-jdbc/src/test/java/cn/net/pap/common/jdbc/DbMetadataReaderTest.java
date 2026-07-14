package cn.net.pap.common.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbMetadataReaderTest {

    private static final Logger log = LoggerFactory.getLogger(DbMetadataReaderTest.class);
    private static final String H2_URL = "jdbc:h2:mem:test_metadata;DATABASE_TO_UPPER=true;DB_CLOSE_DELAY=-1";
    private static final String H2_USER = "sa";
    private static final String H2_PASSWORD = "";

    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection(H2_URL, H2_USER, H2_PASSWORD);
        try (Statement stmt = connection.createStatement()) {
            // 创建测试表
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS test_member (
                        id BIGINT PRIMARY KEY,
                        nickname VARCHAR(100) NOT NULL,
                        age INT,
                        score DECIMAL(10, 2)
                    )
                    """);
            // 添加表和字段注释
            stmt.execute("COMMENT ON TABLE test_member IS '会员信息表'");
            stmt.execute("COMMENT ON COLUMN test_member.nickname IS '会员昵称'");
            stmt.execute("COMMENT ON COLUMN test_member.age IS '年龄'");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS test_member");
            } finally {
                connection.close();
            }
        }
    }

    @Test
    @DisplayName("验证 H2 数据库下的表级元数据读取")
    void testReadTablesInH2() throws Exception {
        List<DbMetadataReader.TableMetadata> tables = DbMetadataReader.readTables(connection, "PUBLIC");
        assertNotNull(tables);
        assertFalse(tables.isEmpty());

        // 验证表名和备注
        DbMetadataReader.TableMetadata memberTable = tables.stream().filter(t -> "TEST_MEMBER".equalsIgnoreCase(t.tableName())).findFirst().orElse(null);

        assertNotNull(memberTable);
        assertEquals("TEST_MEMBER", memberTable.tableName());
        assertEquals("会员信息表", memberTable.remarks());
        log.info("Successfully read table: {}", memberTable);
    }

    @Test
    @DisplayName("验证 H2 数据库下的字段级元数据读取")
    void testReadColumnsInH2() throws Exception {
        List<DbMetadataReader.ColumnMetadata> columns = DbMetadataReader.readColumns(connection, "PUBLIC", "TEST_MEMBER");
        assertNotNull(columns);
        assertEquals(4, columns.size());

        // 验证各字段属性
        DbMetadataReader.ColumnMetadata idCol = getColumn(columns, "ID");
        assertNotNull(idCol);
        assertTrue(idCol.typeName().contains("BIGINT") || idCol.typeName().contains("INT"));
        assertFalse(idCol.nullable());

        DbMetadataReader.ColumnMetadata nameCol = getColumn(columns, "NICKNAME");
        assertNotNull(nameCol);
        assertTrue(nameCol.typeName().contains("VARCHAR") || nameCol.typeName().contains("CHARACTER VARYING"));
        assertEquals(100, nameCol.size());
        assertFalse(nameCol.nullable());
        assertEquals("会员昵称", nameCol.remarks());

        DbMetadataReader.ColumnMetadata ageCol = getColumn(columns, "AGE");
        assertNotNull(ageCol);
        assertTrue(ageCol.nullable());
        assertEquals("年龄", ageCol.remarks());

        for (DbMetadataReader.ColumnMetadata col : columns) {
            log.info("Read column: {}", col);
        }
    }

    private DbMetadataReader.ColumnMetadata getColumn(List<DbMetadataReader.ColumnMetadata> columns, String name) {
        return columns.stream().filter(c -> name.equalsIgnoreCase(c.columnName())).findFirst().orElse(null);
    }
}
