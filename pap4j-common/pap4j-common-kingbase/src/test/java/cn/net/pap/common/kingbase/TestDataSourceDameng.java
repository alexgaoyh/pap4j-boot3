package cn.net.pap.common.kingbase;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class TestDataSourceDameng {

    @Test
    public void initTest() throws SQLException {
        DriverManager.registerDriver(new dm.jdbc.driver.DmDriver());
        Connection conn = DriverManager.getConnection("jdbc:dm://192.168.1.180:5236", "dmtest1", "Dameng123");
        Statement statement = conn.createStatement();
        ResultSet resultSet = statement.executeQuery("select * from TEST_TABLE");
        if (resultSet.next()) {
            System.out.println(resultSet.getString(1));
        }

    }

    // @Test
    public void prepareStatementTest() throws SQLException {
        try {
            DriverManager.registerDriver(new dm.jdbc.driver.DmDriver());
            Connection conn = DriverManager.getConnection("jdbc:dm://192.168.1.180:5236", "SYSDBA", "Dameng123");

            List<PreparedStatement> pstmtList = new ArrayList<>();
            for(int i = 10000; i < 20010; i++) {
                System.out.println(i);
                // 默认 dm.ini 里面，MAX_SESSION_STATEMENT = 10000, 所以这里当出现10000条数据之后，就抛出来了异常。
                String insert = "INSERT INTO SYS_CONFIG(CONFIG_ID) VALUES("+i+")";
                PreparedStatement pstmt = conn.prepareStatement(insert);
                pstmt.addBatch(); // 改为addBatch
                pstmtList.add(pstmt);
            }
            for (PreparedStatement p : pstmtList) {
                p.executeBatch();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    public void curdTest() throws Exception {
        DriverManager.registerDriver(new dm.jdbc.driver.DmDriver());
        Connection conn = DriverManager.getConnection("jdbc:dm://192.168.1.180:5236", "dmtest1", "Dameng123");

        deleteData(conn, "TEST_TABLE", "1");

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("id", 1);
        insertData(conn, "TEST_TABLE", data);

        Statement statement = conn.createStatement();
        ResultSet resultSet = statement.executeQuery("select * from TEST_TABLE ");
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
     * @param conn      数据库连接
     * @param tableName 表名
     * @param data      要插入的数据，键为列名，值为列值
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
