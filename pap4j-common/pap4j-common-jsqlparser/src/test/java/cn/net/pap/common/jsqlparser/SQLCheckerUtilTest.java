package cn.net.pap.common.jsqlparser;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.insert.Insert;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SQLCheckerUtilTest {

    private static final Logger log = LoggerFactory.getLogger(SQLCheckerUtilTest.class);

    @Test
    public void selectWithWhereClauseTest() {
        // 有查询条件，返回 false
        String sql1 = "SELECT * FROM table_name WHERE column_name = 'value'";
        Boolean sql1Bool = SQLCheckerUtil.isSelectWithNoWhere(sql1);
        assertTrue(!sql1Bool);

        // 无查询条件，返回 true
        String sql2 = "SELECT * FROM table_name order by column_name";
        Boolean sql2Bool = SQLCheckerUtil.isSelectWithNoWhere(sql2);
        assertTrue(sql2Bool);

        // 无查询条件，返回 true
        String sql3 = "SELECT * FROM table_name";
        Boolean sql3Bool = SQLCheckerUtil.isSelectWithNoWhere(sql3);
        assertTrue(sql3Bool);

    }

    @Test
    public void addWhereConditionTest() {
        // 有查询条件，返回 false
        String sql1 = "SELECT * FROM table_name WHERE column_name = 'value'";
        String sql1After = SQLCheckerUtil.addWhereCondition(sql1, "1 = 2");
        log.info("SQL 1 After: {}", sql1After);

        // 无查询条件，返回 true
        String sql2 = "SELECT * FROM table_name order by column_name";
        String sql2After = SQLCheckerUtil.addWhereCondition(sql2, "1 = 2");
        log.info("SQL 2 After: {}", sql2After);

        // 无查询条件，返回 true
        String sql3 = "SELECT * FROM table_name";
        String sql3After = SQLCheckerUtil.addWhereCondition(sql3, "1 = 2");
        log.info("SQL 3 After: {}", sql3After);

    }

    @Test
    public void getTableNameTest() {
        // 表名
        String sql1 = "SELECT * FROM table_name WHERE column_name = 'value'";
        String sql1After = SQLCheckerUtil.getTableName(sql1);
        log.info("Table Name 1: {}", sql1After);

        // 表名
        String sql2 = "SELECT * FROM table_name order by column_name";
        String sql2After = SQLCheckerUtil.getTableName(sql2);
        log.info("Table Name 2: {}", sql2After);

        // 表名
        String sql3 = "SELECT * FROM table_name";
        String sql3After = SQLCheckerUtil.getTableName(sql3);
        log.info("Table Name 3: {}", sql3After);

    }

    @Test
    public void pgSQLCheckTest() {
        try {
            String sql1 = "INSERT INTO target(id,c_count) (select id,count(id) num from source GROUP BY id) ON CONFLICT (id) DO UPDATE SET c_count = EXCLUDED.c_count";
            Insert insertParse = (Insert) CCJSqlParserUtil.parse(sql1);
            log.info("Insert Parse Table: {}", insertParse.getTable());
        } catch (JSQLParserException e) {
            throw new RuntimeException(e);
        }
    }

}
