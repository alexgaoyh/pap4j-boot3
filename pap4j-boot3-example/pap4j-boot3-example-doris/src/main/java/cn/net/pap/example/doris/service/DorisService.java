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
            stmt.addBatch("INSERT INTO doris(id, doris_name) VALUES (11, '1')");
            stmt.addBatch("INSERT INTO doris(id, doris_name) VALUES (22, '2')");
            // 如果过长的话，会统一回退；
            stmt.addBatch("INSERT INTO doris(id, doris_name) VALUES (33, 'largelargelargelarge')");
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

}
