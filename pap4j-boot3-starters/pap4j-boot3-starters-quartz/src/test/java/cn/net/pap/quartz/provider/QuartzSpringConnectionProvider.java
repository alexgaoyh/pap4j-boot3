package cn.net.pap.quartz.provider;

import org.quartz.utils.ConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class QuartzSpringConnectionProvider implements ConnectionProvider {

    private static DataSource dataSource;

    public static void setDataSource(DataSource ds) {
        dataSource = ds;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void shutdown() {
        // nothing
    }

    @Override
    public void initialize() {
        // nothing
    }
}
