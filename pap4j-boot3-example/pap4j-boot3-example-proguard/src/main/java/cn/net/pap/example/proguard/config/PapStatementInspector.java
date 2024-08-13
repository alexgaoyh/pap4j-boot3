package cn.net.pap.example.proguard.config;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * StatementInspector
 */
public class PapStatementInspector implements StatementInspector {

    @Override
    public String inspect(String sql) {
        System.out.println("PapStatementInspector.inspect = " + sql);
        return sql;
    }

}