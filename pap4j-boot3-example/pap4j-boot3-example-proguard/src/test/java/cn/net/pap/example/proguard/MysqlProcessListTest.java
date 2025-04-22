package cn.net.pap.example.proguard;

import org.junit.Test;

import java.sql.*;

public class MysqlProcessListTest {

    // @Test
    public void processListTest() throws SQLException {
        // 启用游标获取 设置默认获取大小
        String url = "jdbc:mysql://192.168.1.115:3306/test?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8"
                + "&useCursorFetch=true"
                + "&defaultFetchSize=1000"
                + "&profileSQL=true&logger=com.mysql.cj.log.StandardLogger";

        try (Connection connection = DriverManager.getConnection(url, "root", "alexgaoyh")) {
            String sql = "SELECT * FROM information_schema.processlist WHERE COMMAND NOT IN ('Sleep', 'Binlog Dump', 'Daemon')  AND TIME >= 3  ORDER BY TIME DESC";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        String info = rs.getString("INFO");
                        System.out.println(info);
                    }
                }
            } finally {
                connection.setReadOnly(false);
            }
        }

    }

}
