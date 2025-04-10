package cn.net.pap.example.proguard.config;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StatementInspector
 */
public class PapStatementInspector implements StatementInspector {

    private static final Logger log = LoggerFactory.getLogger(PapStatementInspector.class);

    @Override
    public String inspect(String sql) {
        if(log.isDebugEnabled()) {
            System.out.println("PapStatementInspector.inspect = " + sql);
        }
        return sql;
    }

}