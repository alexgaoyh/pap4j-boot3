package cn.net.pap.example.doris.service;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.Statement;

@Service("dorisService")
public class DorisService {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    public int transactionalTest() {
        SqlSession session = sqlSessionFactory.openSession(false);
        Connection conn = session.getConnection();
        try (Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);

            stmt.addBatch("begin");
            stmt.addBatch("INSERT INTO doris(id, doris_name, doris_remark) VALUES (11, '1', '1')");
            stmt.addBatch("INSERT INTO doris(id, doris_name, doris_remark) VALUES (22, '2', '2')");
            // 使用 insert 完成列数据的更新
            stmt.addBatch("INSERT INTO doris(id, doris_name, doris_remark) VALUES (22, '2-update', '2-update')");
            // 如果过长的话，会统一回退；
            stmt.addBatch("INSERT INTO doris(id, doris_name, doris_remark) VALUES (33, 'largelargelargelarge', null)");
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
            stmt.execute("INSERT INTO doris(id, doris_remark) VALUES (33,  'remark')");

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

}
