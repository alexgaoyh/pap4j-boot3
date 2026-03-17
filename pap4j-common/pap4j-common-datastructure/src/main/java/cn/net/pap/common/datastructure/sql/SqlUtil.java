package cn.net.pap.common.datastructure.sql;

import cn.net.pap.common.datastructure.exception.UtilException;

import static java.util.Objects.isNull;

/**
 * <p><strong>SqlUtil</strong> 提供防止 SQL 注入的实用方法。</p>
 *
 * <p>它主要侧重于通过对照允许的模式验证字符以及检查危险的 SQL 关键字来清理动态 SQL 输入，如 "ORDER BY" 子句。</p>
 *
 * <ul>
 *     <li>根据白名单模式进行验证。</li>
 *     <li>过滤危险的关键字。</li>
 *     <li>强制执行长度限制。</li>
 * </ul>
 */
public class SqlUtil {

    /**
     * <p>用于检测常见危险 SQL 关键字的正则表达式。</p>
     */
    public static String SQL_REGEX = "\u000B|and |extractvalue|updatexml|sleep|exec |insert |select |delete |update |drop |count |chr |mid |master |truncate |char |declare |or |union |like |+|/*|user()";

    /**
     * <p>SQL 子句的允许模式（字母、数字、下划线、空格、逗号、句号）。</p>
     */
    public static String SQL_PATTERN = "[a-zA-Z0-9_\\ \\,\\.]+";

    /**
     * <p>ORDER BY 子句允许的最大长度。</p>
     */
    private static final int ORDER_BY_MAX_LENGTH = 500;

    /**
     * <p>验证字符串以防止 SQL 注入攻击。</p>
     *
     * <p>如果值超过长度限制、包含无效字符或匹配被禁用的关键字，则抛出 {@link UtilException}。</p>
     *
     * @param value 要检查的 SQL 字符串段。
     * @return 如果字符串安全则是原始字符串。
     * @throws UtilException 如果验证失败。
     */
    public static String checkSql(String value) {
        if (isNotEmpty(value) && !isValidOrderBySql(value)) {
            throw new UtilException("参数不符合规范，不能进行查询");
        }
        if (isNotEmpty(value) && !filterKeyword(value)) {
            throw new UtilException("参数不符合规范，不能进行查询");
        }
        if (value.length() > ORDER_BY_MAX_LENGTH) {
            throw new UtilException("参数已超过最大限制，不能进行查询");
        }
        return value;
    }

    /**
     * <p>检查字符串是否匹配允许字符的模式。</p>
     *
     * @param value 要测试的字符串。
     * @return <strong>true</strong> 如果字符串匹配该模式。
     */
    private static boolean isValidOrderBySql(String value) {
        return value.matches(SQL_PATTERN);
    }

    /**
     * <p>检查是否存在被禁用的 SQL 关键字。</p>
     *
     * @param value 要测试的字符串。
     * @return <strong>true</strong> 如果字符串安全（未找到关键字），否则返回 <strong>false</strong>。
     */
    private static boolean filterKeyword(String value) {
        if (isEmpty(value)) {
            return false;
        }
        String[] sqlKeywords = SQL_REGEX.split("\\|");
        for (String sqlKeyword : sqlKeywords) {
            if (value.toLowerCase().indexOf(sqlKeyword) > -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * <p>检查字符串是否为 null 或为空。</p>
     *
     * @param str 字符串。
     * @return <strong>true</strong> 如果为空。
     */
    private static boolean isEmpty(String str) {
        return isNull(str) || "".equals(str.trim());
    }

    /**
     * <p>检查字符串是否不为空。</p>
     *
     * @param str 字符串。
     * @return <strong>true</strong> 如果不为空。
     */
    private static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

}
