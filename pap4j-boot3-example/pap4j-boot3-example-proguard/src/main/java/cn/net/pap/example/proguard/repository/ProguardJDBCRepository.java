package cn.net.pap.example.proguard.repository;

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

    private final DataSource dataSource;

    public ProguardJDBCRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void dataSourcePrintProguardId() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM proguard")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString("proguard_id"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
