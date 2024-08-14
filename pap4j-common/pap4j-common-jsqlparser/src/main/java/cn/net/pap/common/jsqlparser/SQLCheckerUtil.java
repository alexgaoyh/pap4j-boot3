package cn.net.pap.common.jsqlparser;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

import java.io.StringReader;

/**
 * SQL CHECKER
 */
public class SQLCheckerUtil {

    /**
     * 是否是包含 where 语句 的 Select
     * @param sql
     * @return
     */
    public static Boolean isSelectWithNoWhere(String sql) {
        try {
            CCJSqlParserManager parserManager = new CCJSqlParserManager();
            Statement statement = parserManager.parse(new StringReader(sql));
            if (statement instanceof Select) {
                Select selectStatement = (Select) statement;
                Select selectBody = selectStatement.getSelectBody();

                if (selectBody instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) selectBody;
                    if (plainSelect.getWhere() != null) {
                        return false;
                    } else {
                        return true;
                    }
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 在原有的sql中增加新的where条件
     *
     * @param sql       原sql
     * @param condition 新的and条件
     * @return 新的sql
     */
    public static String addWhereCondition(String sql, String condition) {
        try {
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect plainSelect = (PlainSelect)select.getSelectBody();
            final Expression expression = plainSelect.getWhere();
            final Expression envCondition = CCJSqlParserUtil.parseCondExpression(condition);
            if (expression == null) {
                plainSelect.setWhere(envCondition);
            } else {
                AndExpression andExpression = new AndExpression(expression, envCondition);
                plainSelect.setWhere(andExpression);
            }
            return plainSelect.toString();
        } catch (JSQLParserException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 在原有的sql中增加新的where条件
     *
     * @param sql       sql
     * @return 新的sql
     */
    public static String getTableName(String sql) {
        try {
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect plainSelect = (PlainSelect)select.getSelectBody();
            Table table = (Table) plainSelect.getFromItem();
            return table.getName();
        } catch (JSQLParserException e) {
            throw new RuntimeException(e);
        }
    }

}
