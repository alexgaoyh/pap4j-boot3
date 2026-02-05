package cn.net.pap.common.jsonorm;

/**
 * json schema 与 DB 的关联处理
 */
public class JsonSchemaDBTest {

    /**
     * 从 mysql 中的 information_schema.COLUMNS， 查询特定表的字段信息，期望后期与 json schema 的配置做匹配。
     */
    public static final String tableColumnMysql = """
            SELECT
                COLUMN_NAME,
                DATA_TYPE,
                COLUMN_TYPE,
                CASE
                    WHEN COLUMN_TYPE LIKE '%(%' THEN
                        SUBSTRING_INDEX(SUBSTRING_INDEX(COLUMN_TYPE, '(', -1), IF(COLUMN_TYPE LIKE '%,%', ',', ')'), 1)
                    ELSE NULL
                END AS LENGTH_LEFT,
                CASE
                    WHEN COLUMN_TYPE LIKE '%,%' THEN
                        SUBSTRING_INDEX(SUBSTRING_INDEX(COLUMN_TYPE, ',', -1), ')', 1)
                    ELSE NULL
                END AS LENGTH_RIGHT,
                IS_NULLABLE,
                COLUMN_DEFAULT,
                COLUMN_COMMENT
            FROM
                information_schema.COLUMNS
            WHERE
                TABLE_SCHEMA = 'cf'
                AND TABLE_NAME = 't_ad'
            ORDER BY
                ORDINAL_POSITION;
            """;

    /**
     * kingbase 下，表字段信息查询。
     */
    public static final String tableColumnKingbase = """
            SELECT
                a.attname,
                format_type(a.atttypid, a.atttypmod),
            		CASE
                    WHEN format_type(a.atttypid, a.atttypmod) LIKE '%(%' THEN
                        regexp_replace(format_type(a.atttypid, a.atttypmod), '.*\\(([0-9]+).*', '\\1')
                    ELSE NULL
                END AS length_left,
                CASE
                    WHEN format_type(a.atttypid, a.atttypmod) LIKE '%,%' THEN
                        regexp_replace(format_type(a.atttypid, a.atttypmod), '.*,([0-9]+)\\)', '\\1')
                    ELSE NULL
                END AS length_right,
                a.attnotnull,
                d.description
            FROM
                sys_catalog.sys_attribute a
                JOIN sys_catalog.sys_class c ON a.attrelid = c.oid
                JOIN sys_catalog.sys_namespace n ON c.relnamespace = n.oid
                LEFT JOIN sys_catalog.sys_description d ON d.objoid = c.oid AND d.objsubid = a.attnum
            WHERE
                n.nspname = 'public'
                AND c.relname = 'test'
                AND a.attnum > 0
                AND NOT a.attisdropped
            ORDER BY
                a.attnum;
            """;

}
