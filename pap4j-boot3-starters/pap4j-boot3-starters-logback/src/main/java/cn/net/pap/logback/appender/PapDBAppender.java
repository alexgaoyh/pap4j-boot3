package cn.net.pap.logback.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 *    配合 LogbackInitializer.java 类
 *
 *     @Autowired
 *     private DataSource dataSource;
 *
 *     private static final org.slf4j.Logger dbLogger = LoggerFactory.getLogger("dbLogger");
 *
 *     @GetMapping("/dbAppender")
 *     public ResponseEntity<String> dbAppender() {
 *         String dateStr = new Date().toString();
 *         dbLogger.warn(dateStr);
 *         return ResponseEntity.ok(dateStr);
 *     }
 */
public class PapDBAppender extends AppenderBase<ILoggingEvent> {

    private final DataSource dataSource;

    public PapDBAppender(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        if(dataSource != null) {
            // maybe batch insert ?
            String sql = """
                INSERT INTO log(level) VALUES (?)
                """;
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, iLoggingEvent.getLevel().levelStr);
                ps.executeUpdate();
            } catch (SQLException e) {
                addError("Failed to insert log event into DB", e);
            }
        }
    }

}