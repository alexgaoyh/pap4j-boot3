package cn.net.pap.example.proguard;

import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MysqlLargeQueryTest {

    // @Test
    public void largeQueryTest() throws SQLException {
        String url = "jdbc:mysql://192.168.1.115:3306/test?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8";

        try (Connection connection = DriverManager.getConnection(url, "root", "alexgaoyh")) {
            List<Map<String, Object>> allResultList = new ArrayList<>();
            for (int idx = 0; idx < 1000; idx++) {
                String sql = "SELECT * FROM test limit 0, 1000";

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

    // @Test
    public void largeFieldTest() throws SQLException {
        String url = "jdbc:mysql://127.0.0.1:3306/test?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8";

        try (Connection connection = DriverManager.getConnection(url, "root", "alexgaoyh")) {
            testQuery(connection, "SELECT FILE_NAME, RECO_RESULT FROM _large_field", "With RECO_RESULT");

            testQuery(connection, "SELECT FILE_NAME FROM _large_field", "Without RECO_RESULT");
        }
    }

    private void testQuery(Connection connection, String sql, String testName) throws SQLException {
        System.out.println("\n========== Starting test: " + testName + " ==========");
        long totalQueryTime = 0;
        long totalNetworkTime = 0;
        long totalProcessingTime = 0;
        long totalMappingTime = 0;
        long totalRowProcessingTime = 0;
        int rowCount = 0;
        long totalBytesTransferred = 0;

        for (int idx = 0; idx < 100; idx++) {
            // 测量整个查询执行时间
            long queryStart = System.currentTimeMillis();

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                // 测量网络传输和数据库执行时间
                long executeStart = System.currentTimeMillis();
                try (ResultSet rs = statement.executeQuery()) {
                    long executeEnd = System.currentTimeMillis();

                    // 测量结果集处理时间
                    long processingStart = System.currentTimeMillis();
                    List<Map<String, Object>> resultList = new ArrayList<>();
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    while (rs.next()) {
                        long rowStart = System.currentTimeMillis();
                        Map<String, Object> rowMap = new HashMap<>();

                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            long getValueStart = System.currentTimeMillis();
                            Object value = rs.getObject(i);
                            long getValueEnd = System.currentTimeMillis();

                            // 估算传输的数据量(粗略估算)
                            if (value != null) {
                                totalBytesTransferred += value.toString().getBytes().length;
                            }

                            rowMap.put(columnName, value);
                        }

                        resultList.add(rowMap);
                        long rowEnd = System.currentTimeMillis();
                        totalRowProcessingTime += (rowEnd - rowStart);
                        rowCount++;
                    }

                    long processingEnd = System.currentTimeMillis();
                    totalProcessingTime += (processingEnd - processingStart);
                    totalNetworkTime += (executeEnd - executeStart);
                }
            }

            long queryEnd = System.currentTimeMillis();
            totalQueryTime += (queryEnd - queryStart);

            System.out.printf("Iteration %d - Total: %dms | Network/DB: %dms | Processing: %dms | Rows: %d%n",
                    idx, (queryEnd - queryStart), totalNetworkTime, totalProcessingTime, rowCount);
        }

        // 打印统计信息
        System.out.println("\n========== Test Results: " + testName + " ==========");
        System.out.println("Average total time: " + (totalQueryTime / 100) + "ms");
        System.out.println("Average network/DB time: " + (totalNetworkTime / 100) + "ms");
        System.out.println("Average processing time: " + (totalProcessingTime / 100) + "ms");
        System.out.println("Average row processing time: " + (rowCount > 0 ? (totalRowProcessingTime / rowCount) + "ms/row" : "N/A"));
        System.out.println("Total rows processed: " + rowCount);
        System.out.println("Estimated total data transferred: " + formatBytes(totalBytesTransferred));
        System.out.println("Average data per query: " + formatBytes(totalBytesTransferred / 100));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }
}
