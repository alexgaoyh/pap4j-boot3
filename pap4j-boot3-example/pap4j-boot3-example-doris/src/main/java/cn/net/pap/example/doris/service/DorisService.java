package cn.net.pap.example.doris.service;

import cn.net.pap.example.doris.entity.Doris;
import cn.net.pap.example.doris.mapper.DorisMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.*;

@Service("dorisService")
public class DorisService {

    private static final Logger log = LoggerFactory.getLogger(DorisService.class);

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DorisMapper dorisMapper;

    @Autowired
    private PlatformTransactionManager platformTransactionManager;

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
            log.error("transactionalTest", e);
            try {
                conn.rollback();
            } catch (Exception e1) {
                log.error("transactionalTest", e1);
            }
        } finally {
            try {
                conn.close();
                session.close();
            } catch (Exception e1) {
                log.error("transactionalTest", e1);
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
            log.error("partFieldUpdateTest", e);
            try {
                conn.rollback();
            } catch (Exception e1) {
                log.error("partFieldUpdateTest", e1);
            }
        } finally {
            try {
                conn.close();
                session.close();
            } catch (Exception e1) {
                log.error("partFieldUpdateTest", e1);
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
            log.error("updateTestThrowExceptionInMysqlDB", e);
            try {
                conn.rollback();
            } catch (SQLException e1) {
                log.error("updateTestThrowExceptionInMysqlDB", e1);
            }
        } finally {
            try {
                conn.close();
                session.close();
            } catch (Exception e1) {
                log.error("updateTestThrowExceptionInMysqlDB", e1);
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
                log.error("updateTestNoExceptionInMysqlDB", e1);
            }
        }
    }

    /**
     * 做事务的控制与验证
     * @return
     */
    public int updateTestTestInMysqlDB() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_DEFAULT);

        return transactionTemplate.execute(status -> {
            Connection conn = DataSourceUtils.getConnection(dataSource);
            try {
                try (PreparedStatement pstmt = conn.prepareStatement("UPDATE doris SET doris_remark = ? WHERE id = 1")) {
                    pstmt.setString(1, "PAP");
                    pstmt.executeUpdate();
                }

                Doris doris = new Doris();
                doris.setId(9L);
                doris.setDorisName("PAP".repeat(10));
                dorisMapper.insert(doris);

                dorisMapper.updateBySql("update doris set doris_name = 'pap'");

                return 1;
            } catch (SQLException e) {
                status.setRollbackOnly();
                throw new RuntimeException("Update failed", e);
            } finally {
                DataSourceUtils.releaseConnection(conn, dataSource);
            }
        });

    }

    @Transactional
    public int updateTestTestInMysqlDB2() {
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
            try (PreparedStatement pstmt = conn.prepareStatement("UPDATE doris SET doris_remark = ? WHERE id = 1")) {
                pstmt.setString(1, "PAP");
                pstmt.executeUpdate();
            }

            Doris doris = new Doris();
            doris.setId(9L);
            doris.setDorisName("PAP".repeat(10));
            dorisMapper.insert(doris);

            dorisMapper.updateBySql("update doris set doris_name = 'pap'");

            return 1;
        } catch (SQLException e) {
            log.error("updateTestTestInMysqlDB2", e);
            return -1;
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

}
