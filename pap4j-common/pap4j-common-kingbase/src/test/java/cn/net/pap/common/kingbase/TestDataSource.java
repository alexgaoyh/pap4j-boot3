package cn.net.pap.common.kingbase;

import org.junit.jupiter.api.Test;

import java.sql.*;

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
}
