package cn.net.pap.example.proguard.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * call JDBC
 */
@Repository
public class ProguardJDBCRepository {

    private static final Logger log = LoggerFactory.getLogger(ProguardJDBCRepository.class);

    private final DataSource dataSource;

    public ProguardJDBCRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void dataSourcePrintProguardId() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM proguard")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    log.info("{}", rs.getString("proguard_id"));
                }
            }
        } catch (SQLException e) {
            log.error("查询 proguard 表失败", e);
            throw new RuntimeException(e);
        }
    }

}
