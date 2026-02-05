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

}
