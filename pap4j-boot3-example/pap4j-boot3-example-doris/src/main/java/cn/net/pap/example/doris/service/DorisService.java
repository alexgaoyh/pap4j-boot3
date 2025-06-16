package cn.net.pap.example.doris.service;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.*;

@Service("dorisService")
public class DorisService {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private DataSource dataSource;

    public int transactionalTest() {
        SqlSession session = sqlSessionFactory.openSession(false);
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);

            stmt.addBatch("begin");
            stmt.addBatch("INSERT INTO doris(id, doris_name, doris_remark, doris_json) VALUES (11, '1', '1', '{\"name\":\"name11\",\"age\":\"age\",\"ext\":{\"detail\":{\"dName\":\"dName\",\"dAge\":\"dAge\",\"dList\":[{\"d1\":\"d1\",\"d2\":\"d2\"},{\"d1\":\"d3\",\"d2\":\"d4\"}]}},\"list\":[{\"a\":\"1\",\"b\":\"2\",\"ext\":{\"c\":\"12\",\"d\":\"1212\"}},{\"a\":\"3\",\"b\":\"4\",\"ext\":{\"c\":\"34\",\"d\":\"3434\"}}]}')");
            stmt.addBatch("INSERT INTO doris(id, doris_name, doris_remark, doris_json) VALUES (22, '2', '2', null)");
            // 使用 insert 完成列数据的更新
            stmt.addBatch("INSERT INTO doris(id, doris_name, doris_remark, doris_json) VALUES (22, '2-update', '2-update', null)");
            // 如果过长的话，会统一回退；
            stmt.addBatch("INSERT INTO doris(id, doris_name, doris_remark, doris_json) VALUES (33, 'largelargelargelarge', null, null)");
            // 删除操作可以使用 删除状态位 做处理，一方面可以逻辑删除，另一方面可以在事务中。
            stmt.executeBatch();

            // int i = 1/0;

            conn.commit();

            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            try {
                conn.rollback();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        } finally {
            try {
                conn.close();
                session.close();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
        return 0;
    }

    /**
     * 部分字段更新
     *
     * @return
     */
    public int partFieldUpdateTest() {
        SqlSession session = sqlSessionFactory.openSession();
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {

            stmt.execute("SET enable_unique_key_partial_update=true");
            stmt.execute("INSERT INTO doris(id, doris_remark, doris_json) VALUES (33,  'remark', '{\"name\":\"name33\",\"age\":\"age\",\"ext\":{\"detail\":{\"dName\":\"dName\",\"dAge\":\"dAge\",\"dList\":[{\"d1\":\"d1\",\"d2\":\"d2\"},{\"d1\":\"d3\",\"d2\":\"d4\"}]}},\"list\":[{\"a\":\"1\",\"b\":\"2\",\"ext\":{\"c\":\"12\",\"d\":\"1212\"}},{\"a\":\"3\",\"b\":\"4\",\"ext\":{\"c\":\"34\",\"d\":\"3434\"}}]}')");

            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            try {
                conn.rollback();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        } finally {
            try {
                conn.close();
                session.close();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
        return 0;
    }

    /**
     * 验证如果外部调用者使用 @Transactional 注解控制事务的处理， connection 的获取方式对比
     * @return
     */
    public int updateTestThrowExceptionInMysqlDB() {
        SqlSession session = sqlSessionFactory.openSession();
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            // 获取当前时间戳（格式化为 SQL 可识别的字符串）
            String currentTime = new Timestamp(System.currentTimeMillis()).toString().substring(0, 20);
            stmt.execute("UPDATE doris SET doris_remark = '" + currentTime + "' WHERE id = 1");
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            try {
                conn.rollback();
            } catch (SQLException e1) {
                e1.printStackTrace();
            }
        } finally {
            try {
                conn.close();
                session.close();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
        return 0;
    }

    /**
     * 验证如果外部调用者使用 @Transactional 注解控制事务的处理， connection 的获取方式对比
     * @return
     */
    public int updateTestNoExceptionInMysqlDB() {
        // 为了做对比，后续可以改为 try resources 方式
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = dataSource.getConnection();
            pstmt = conn.prepareStatement("UPDATE doris SET doris_remark = ? WHERE id = 1");
            pstmt.setString(1, new Timestamp(System.currentTimeMillis()).toString().substring(0, 20));
            pstmt.executeUpdate();
            return 1;
        } catch (SQLException e) {
            throw new RuntimeException("Update failed", e);
        }finally {
            try {
                pstmt.close();
                conn.close();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
    }

}
