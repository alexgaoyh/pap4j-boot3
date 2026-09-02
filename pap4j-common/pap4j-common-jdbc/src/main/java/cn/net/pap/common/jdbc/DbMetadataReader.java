package cn.net.pap.common.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物理数据库元数据读取工具。
 * 支持 MySQL、Kingbase 以及通用数据库（如 H2）的表和字段元数据读取，能够自动适配并提取字段注释。
 */
public final class DbMetadataReader {

    private static final Logger log = LoggerFactory.getLogger(DbMetadataReader.class);

    private DbMetadataReader() {
        // 工具类私有构造函数
    }

    /**
     * 表元数据只读记录类。
     */
    public record TableMetadata(String tableName, String remarks) {
    }

    /**
     * 字段元数据只读记录类。
     */
    public record ColumnMetadata(String columnName, String typeName, int dataType, int size, boolean nullable,
                                 String remarks) {
    }

    /**
     * 读取指定 Schema 下的所有表元数据。
     *
     * @param conn   数据库连接
     * @param schema 数据库 Schema / Catalog 名称
     * @return 表元数据列表
     * @throws SQLException SQL 异常
     */
    public static List<TableMetadata> readTables(Connection conn, String schema) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        String dbType = metaData.getDatabaseProductName().toLowerCase();
        List<TableMetadata> tables = new ArrayList<>();

        try (ResultSet rs = metaData.getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String remarks = rs.getString("REMARKS");
                tables.add(new TableMetadata(tableName, remarks));
            }
        }

        if (dbType.contains("mysql")) {
            return enrichMysqlTableComments(conn, schema, tables);
        } else if (dbType.contains("kingbase")) {
            return enrichKingbaseTableComments(conn, schema, tables);
        }
        return tables;
    }

    /**
     * 读取指定表的所有字段元数据。
     *
     * @param conn      数据库连接
     * @param schema    数据库 Schema / Catalog 名称
     * @param tableName 表名称
     * @return 字段元数据列表
     * @throws SQLException SQL 异常
     */
    public static List<ColumnMetadata> readColumns(Connection conn, String schema, String tableName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        String dbType = metaData.getDatabaseProductName().toLowerCase();
        List<ColumnMetadata> columns = new ArrayList<>();

        try (ResultSet rs = metaData.getColumns(null, schema, tableName, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String typeName = rs.getString("TYPE_NAME");
                int dataType = rs.getInt("DATA_TYPE");
                int size = rs.getInt("COLUMN_SIZE");
                boolean nullable = (rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                String remarks = rs.getString("REMARKS");

                columns.add(new ColumnMetadata(columnName, typeName, dataType, size, nullable, remarks));
            }
        }

        if (dbType.contains("mysql")) {
            return enrichMysqlColumnComments(conn, schema, tableName, columns);
        } else if (dbType.contains("kingbase")) {
            return enrichKingbaseColumnComments(conn, schema, tableName, columns);
        }
        return columns;
    }

    private static List<TableMetadata> enrichMysqlTableComments(Connection conn, String schema, List<TableMetadata> tables) {
        String sql = """
                SELECT TABLE_NAME, TABLE_COMMENT 
                FROM information_schema.TABLES 
                WHERE TABLE_SCHEMA = ?
                """;
        Map<String, String> commentMap = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    commentMap.put(rs.getString("TABLE_NAME"), rs.getString("TABLE_COMMENT"));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query MySQL table comments: ", e);
            return tables;
        }

        return tables.stream().map(t -> {
            String comment = commentMap.get(t.tableName());
            return (comment != null && !comment.isBlank()) ? new TableMetadata(t.tableName(), comment) : t;
        }).toList();
    }

    private static List<TableMetadata> enrichKingbaseTableComments(Connection conn, String schema, List<TableMetadata> tables) {
        String sql = """
                SELECT c.relname AS table_name, d.description AS table_comment
                FROM pg_description d
                JOIN pg_class c ON d.objoid = c.oid
                JOIN pg_namespace n ON c.relnamespace = n.oid
                WHERE n.nspname = ? AND d.objsubid = 0
                """;
        Map<String, String> commentMap = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    commentMap.put(rs.getString("table_name"), rs.getString("table_comment"));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query Kingbase table comments: ", e);
            return tables;
        }

        return tables.stream().map(t -> {
            String comment = commentMap.get(t.tableName());
            return (comment != null && !comment.isBlank()) ? new TableMetadata(t.tableName(), comment) : t;
        }).toList();
    }

    private static List<ColumnMetadata> enrichMysqlColumnComments(Connection conn, String schema, String tableName, List<ColumnMetadata> columns) {
        String sql = """
                SELECT COLUMN_NAME, COLUMN_COMMENT 
                FROM information_schema.COLUMNS 
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                """;
        Map<String, String> commentMap = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    commentMap.put(rs.getString("COLUMN_NAME"), rs.getString("COLUMN_COMMENT"));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query MySQL column comments for table {}: ", tableName, e);
            return columns;
        }

        return columns.stream().map(c -> {
            String comment = commentMap.get(c.columnName());
            return (comment != null && !comment.isBlank()) ? new ColumnMetadata(c.columnName(), c.typeName(), c.dataType(), c.size(), c.nullable(), comment) : c;
        }).toList();
    }

    private static List<ColumnMetadata> enrichKingbaseColumnComments(Connection conn, String schema, String tableName, List<ColumnMetadata> columns) {
        String sql = """
                SELECT a.attname AS column_name, d.description AS column_comment
                FROM pg_attribute a
                JOIN pg_class c ON a.attrelid = c.oid
                JOIN pg_namespace n ON c.relnamespace = n.oid
                LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = a.attnum
                WHERE n.nspname = ? AND c.relname = ? AND a.attnum > 0 AND NOT a.attisdropped
                """;
        Map<String, String> commentMap = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    commentMap.put(rs.getString("column_name"), rs.getString("column_comment"));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query Kingbase column comments for table {}: ", tableName, e);
            return columns;
        }

        return columns.stream().map(c -> {
            String comment = commentMap.get(c.columnName());
            return (comment != null && !comment.isBlank()) ? new ColumnMetadata(c.columnName(), c.typeName(), c.dataType(), c.size(), c.nullable(), comment) : c;
        }).toList();
    }
}
