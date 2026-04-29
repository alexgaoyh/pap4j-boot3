package cn.net.pap.example.proguard;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class MysqlBatchTest {

    @BeforeAll
    public static void checkMysqlAvailable() {
        boolean isUp = false;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 3306), 1000);
            isUp = true;
        } catch (Exception e) {
            // connection failed
        }
        Assumptions.assumeTrue(isUp, "mysql is not running on 127.0.0.1:3306. Skipping tests.");
    }

    @Test
    public void batchTest() throws SQLException {
        String url = "jdbc:mysql://127.0.0.1:3306/cf?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8";

        try (Connection connection = DriverManager.getConnection(url, "root", "alexgaoyh")) {
            connection.setAutoCommit(false);
            try (Statement clearStmt = connection.createStatement()) {
                clearStmt.executeUpdate("TRUNCATE TABLE t_ad");
                System.out.println("History data cleared!");
            }
            String insertSql = "INSERT INTO t_ad (AD_ID, AD_CODE) VALUES (?, ?)";
            //  diff batchSize setting
            int batchSize = 1000;
            try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                for (int i = 1; i <= 10000; i++) {
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

    @Test
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
                for (int i = 1; i <= 10000; i++) {
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

    /**
     * CREATE TABLE triples (
     * id INT AUTO_INCREMENT PRIMARY KEY,
     * subject VARCHAR(50) NOT NULL,
     * predicate VARCHAR(50) NOT NULL,
     * object VARCHAR(50) NOT NULL,
     * INDEX idx_subject(subject),
     * INDEX idx_object(object),
     * INDEX idx_predicate(predicate)
     * );
     * INSERT INTO triples(subject, predicate, object) VALUES
     * ('A', 'friend', 'B'),
     * ('A', 'friend', 'C'),
     * ('B', 'friend', 'D'),
     * ('B', 'friend', 'E'),
     * ('C', 'friend', 'F'),
     * ('D', 'friend', 'G'),
     * ('E', 'friend', 'H'),
     * ('F', 'friend', 'I');
     *
     * @throws SQLException
     */
    @Test
    public void cteTest() throws SQLException {
        String url = "jdbc:mysql://127.0.0.1:3306/cf?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8";

        // 从节点A开头，小于3跳的所有目标节点，这样拿着这些目标节点，去查找关联到的节点，这样关系就出来了。
        String cteSql = """
                        WITH RECURSIVE path_cte(subject, object, depth, full_path) AS (
                        SELECT subject, object, 1 AS depth,
                            CONCAT(subject, '->', object) AS full_path
                        FROM triples
                        WHERE subject = ?
                
                        UNION ALL
                
                        SELECT p.subject, t.object, p.depth + 1,
                            CONCAT(p.full_path, '->', t.object) AS full_path
                        FROM path_cte p
                        JOIN triples t ON p.object = t.subject
                        WHERE FIND_IN_SET(t.object, REPLACE(p.full_path,'->',',')) = 0
                          AND p.depth < 3
                    )
                    SELECT * FROM path_cte ORDER BY depth ASC, subject ASC, object ASC LIMIT ?, ?;
                """;

        try (Connection connection = DriverManager.getConnection(url, "root", "alexgaoyh");
             PreparedStatement pstmt = connection.prepareStatement(cteSql)) {

            // 设置参数
            pstmt.setString(1, "A");
            int page = 1;
            int pageSize = 10;
            int offset = (page - 1) * pageSize;
            pstmt.setInt(2, offset);
            pstmt.setInt(3, pageSize);

            try (ResultSet rs = pstmt.executeQuery()) {
                System.out.println("subject\tobject\tdepth\tfull_path");
                while (rs.next()) {
                    String subject = rs.getString("subject");
                    String object = rs.getString("object");
                    int depth = rs.getInt("depth");
                    String full_path = rs.getString("full_path");
                    System.out.println(subject + "\t" + object + "\t" + depth + "\t" + full_path);
                }
            }

        }

    }


}
