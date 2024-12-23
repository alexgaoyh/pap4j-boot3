package cn.net.pap.common.kingbase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class TestDataSource {

    @Test
    public void initTest() throws SQLException {
        DriverManager.registerDriver(new com.kingbase8.Driver());
        // currentSchema 指定查找顺序，如果遇到同名表的话，会按照顺序进行查找。
        Connection conn = DriverManager.getConnection("jdbc:kingbase8://192.168.1.115:54321/test?currentSchema=test,public,sys_catalog", "system", "alexgaoyh");
        Statement statement = conn.createStatement();
        ResultSet resultSet = statement.executeQuery("select * from sys_config");
        if (resultSet.next()) {
            System.out.println(resultSet.getString(1));
        }

    }

    @Test
    public void curdTest() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        DriverManager.registerDriver(new com.kingbase8.Driver());
        Connection conn = DriverManager.getConnection("jdbc:kingbase8://192.168.1.115:54321/test?currentSchema=test,public,sys_catalog", "system", "alexgaoyh");

        deleteData(conn, "test_json", "1");

        Map<String, Object> insertMap = new HashMap<>();
        insertMap.put("string", "string");
        insertMap.put("int", Integer.MAX_VALUE);
        insertMap.put("boolean", Boolean.FALSE);

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("id", 1);
        data.put("test_json", objectMapper.writeValueAsString(insertMap));
        insertData(conn, "test_json", data);

        Statement statement = conn.createStatement();
        ResultSet resultSet = statement.executeQuery("select * from test_json where test_json ->> 'string' like  concat('%str%') ");
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();

        while (resultSet.next()) {
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i);
                String columnValue = resultSet.getString(i);
                System.out.println(columnName + " : " + columnValue);
            }
        }
    }

    /**
     * 插入数据到指定的表中。
     *
     * @param conn 数据库连接
     * @param tableName 表名
     * @param data 要插入的数据，键为列名，值为列值
     * @throws SQLException 如果发生SQL异常
     */
    public static int insertData(Connection conn, String tableName, java.util.Map<String, Object> data) throws SQLException {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName).append(" (");
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (String key : data.keySet()) {
            if (columns.length() > 0) {
                columns.append(", ");
                values.append(", ");
            }
            columns.append(key);
            values.append("?");
        }
        sql.append(columns).append(") VALUES (").append(values).append(")");
        PreparedStatement pstmt = conn.prepareStatement(sql.toString());
        int index = 1;
        for (Object value : data.values()) {
            pstmt.setObject(index++, value);
        }
        int rowsAffected = pstmt.executeUpdate();
        pstmt.close();
        return rowsAffected;
    }

    public static int deleteData(Connection conn, String tableName, String id) throws SQLException {
        StringBuilder sql = new StringBuilder("DELETE FROM ");
        sql.append(tableName).append(" WHERE ID = '" + id + "'");
        PreparedStatement pstmt = conn.prepareStatement(sql.toString());
        int rowsAffected = pstmt.executeUpdate();
        pstmt.close();
        return rowsAffected;
    }

}
