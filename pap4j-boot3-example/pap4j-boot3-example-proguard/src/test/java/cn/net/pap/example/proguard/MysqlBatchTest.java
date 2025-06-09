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

    // @Test
    public void batchTest2() throws SQLException {
        String url = "jdbc:mysql://127.0.0.1:3306/cf?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8";

        try (Connection connection = DriverManager.getConnection(url, "root", "alexgaoyh")) {
            connection.setAutoCommit(false);
            String insertSql = "INSERT INTO t_ad (AD_ID, AD_CODE) VALUES (?, ?)";
            //  diff batchSize setting
            int batchSize = 1000;
            try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                // 记录是否有批处理失败
                boolean batchFailed = false;
                for (int i = 1; i <= 100000; i++) {
                    statement.setString(1, "" + i);
                    if(i == 99999) {
                        statement.setString(2, "alexgaoyh".repeat(10));
                    } else {
                        statement.setString(2, "name_" + i);
                    }

                    statement.addBatch();

                    if (i % batchSize == 0) {
                        try {
                            int[] batchResults = statement.executeBatch();
                            // 只在批次全部成功时才会提交
                            System.out.println("批次提交成功: " + i);
                        } catch (BatchUpdateException e) {
                            batchFailed = true;
                            connection.rollback();
                            System.out.println("批量更新失败，回滚事务。失败位置：" + i);
                            break;
                        }
                    }
                }

                // 提交最后一个批次（如果它没有被提交）
                if (!batchFailed) {
                    try {
                        statement.executeBatch();
                        connection.commit();  // 提交所有数据
                        System.out.println("最后批次提交成功！");
                    } catch (BatchUpdateException e) {
                        connection.rollback();  // 回滚最后的批次
                        System.out.println("最后批次提交失败，事务回滚。");
                    }
                }
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }


}
