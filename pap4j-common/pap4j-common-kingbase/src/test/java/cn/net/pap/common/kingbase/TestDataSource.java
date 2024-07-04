package cn.net.pap.common.kingbase;

import org.junit.jupiter.api.Test;

import java.sql.*;

public class TestDataSource {

    @Test
    public void initTest() throws SQLException {
        DriverManager.registerDriver(new com.kingbase8.Driver());
        Connection conn = DriverManager.getConnection("jdbc:kingbase8://192.168.1.115:54321/test", "system", "alexgaoyh");
        Statement statement = conn.createStatement();
        ResultSet resultSet = statement.executeQuery("select 1");
        if (resultSet.next()) {
            System.out.println(resultSet.getInt(1));
        }

    }
}
