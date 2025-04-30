package cn.net.pap.example.proguard;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MysqlLargeQueryTest {

    // @Test
    public void largeQueryTest() throws SQLException {
        String url = "jdbc:mysql://192.168.1.115:3306/arc_tianjin?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8";

        try (Connection connection = DriverManager.getConnection(url, "root", "alexgaoyh")) {
            List<Map<String, Object>> allResultList = new ArrayList<>();
            for (int idx = 0; idx < 1000; idx++) {
                String sql = "SELECT * FROM archives_34_file limit 0, 1000";

                long start = System.currentTimeMillis();

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    try (ResultSet rs = statement.executeQuery()) {
                        List<Map<String, Object>> resultList = new ArrayList<>();
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        while (rs.next()) {
                            Map<String, Object> rowMap = new HashMap<>();

                            for (int i = 1; i <= columnCount; i++) {
                                String columnName = metaData.getColumnName(i);
                                Object value = rs.getObject(i);
                                rowMap.put(columnName, value);
                            }
                            resultList.add(rowMap);
                        }
                        allResultList.addAll(resultList);
                    }
                } finally {
                }
                long endTime = System.currentTimeMillis();
                // 可以看这个最后的时间，时间越来越长，越来越长，然后 OutOfMemoryError: Java heap space
                System.out.println(endTime - start);
            }
        }
    }

}
