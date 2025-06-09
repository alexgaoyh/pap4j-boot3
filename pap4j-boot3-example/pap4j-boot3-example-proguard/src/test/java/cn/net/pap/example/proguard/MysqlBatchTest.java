package cn.net.pap.example.proguard;

import org.junit.jupiter.api.Test;

import java.sql.*;

public class MysqlBatchTest {

    // @Test
    public void batchTest() throws SQLException {
        String url = "jdbc:mysql://127.0.0.1:3306/cf?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8";

        try (Connection connection = DriverManager.getConnection(url, "root", "alexgaoyh")) {
            connection.setAutoCommit(false);
            String insertSql = "INSERT INTO t_ad (AD_ID, AD_CODE) VALUES (?, ?)";
            //  diff batchSize setting
            int batchSize = 1000;
            try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                for (int i = 1; i <= 100000; i++) {
                    statement.setString(1, "" + i);
                    statement.setString(2, "name_" + i);
                    statement.addBatch();
                    if (i % batchSize == 0) {
                        statement.executeBatch();
                        connection.commit();
                        System.out.println("Committed batch up to: " + i);
                    }
                }
                // 提交剩余未满 batchSize 的部分
                statement.executeBatch();
                connection.commit();
                System.out.println("Final batch committed!");
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }

    }

}
