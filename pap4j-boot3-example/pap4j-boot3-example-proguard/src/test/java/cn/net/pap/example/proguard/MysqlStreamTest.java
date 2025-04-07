package cn.net.pap.example.proguard;

import java.sql.*;

public class MysqlStreamTest {

    // @Test
    public void mStreamTest() throws SQLException {
        // 启用游标获取 设置默认获取大小
        String url = "jdbc:mysql://127.0.0.1:3306/cf?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8"
                + "&useCursorFetch=true"
                + "&defaultFetchSize=1000"
                + "&profileSQL=true&logger=com.mysql.cj.log.StandardLogger";

        try (Connection connection = DriverManager.getConnection(url, "root", "alexgaoyh")) {

            long startTime = System.currentTimeMillis();

            connection.setReadOnly(true);
            String sql = "SELECT * FROM transe_train_data_similarity_jaccard";
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try (PreparedStatement statement = connection.prepareStatement(
                    sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                statement.setFetchSize(10000);

                try (ResultSet rs = statement.executeQuery()) {
                    System.gc();
                    long beforeMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        if (count % 1000 == 0) {
                            printMemoryUsage(beforeMem, count);
                        }
//                        String one = rs.getString(1);
//                        System.out.println(one);
                    }
                }

                System.out.printf("Total time for fetchSize %d: %d ms%n",
                        statement.getFetchSize(), System.currentTimeMillis() - startTime);
            } finally {
                connection.setReadOnly(false);
            }
        }

    }

    private void printMemoryUsage(long baselineMem, int rowCount) {
        Runtime runtime = Runtime.getRuntime();
        long usedMem = runtime.totalMemory() - runtime.freeMemory() - baselineMem;
        System.out.printf("Row %6d - Used memory: %6d KB%n",
                rowCount, usedMem / 1024);
    }

}
