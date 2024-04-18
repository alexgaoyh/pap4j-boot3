package cn.net.pap.common.jsonorm.util;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据结构转SQL语句
 */
public class SqlUtil {


    /**
     * 生成 insert 语句
     * @param tableName
     * @param valueMap
     * @return
     */
    public static String geneInsertStatement(String tableName, Map<String, Object> valueMap) {
        // 验证输入
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        if (valueMap == null || valueMap.isEmpty()) {
            throw new IllegalArgumentException("Values cannot be null or empty");
        }

        // 构建列名和值的字符串
        String columns = valueMap.keySet().stream()
                .map(column -> "`" + column + "`")
                .collect(Collectors.joining(", "));
        String valuesStr = valueMap.entrySet().stream()
                .map(entry -> {
                    Object value = entry.getValue();
                    // 这里只是简单地将值转换为字符串，实际应用中需要更复杂的处理，比如处理日期、数字等
                    String val = value == null ? "NULL" : "'" + value.toString().replace("'", "''") + "'";
                    return val;
                })
                .collect(Collectors.joining(", "));

        // 构建完整的INSERT语句
        String sql = "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + valuesStr + ");";
        return sql;
    }

    /**
     * 生成删除语句
     * @param tableName
     * @return
     */
    public static String generateDeleteStatement(String tableName, String pk, Object pkValue) {
        // 验证输入
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        if (pk == null || pk.isEmpty()) {
            throw new IllegalArgumentException("Primary key cannot be null or empty");
        }
        if (pkValue == null) {
            throw new IllegalArgumentException("Primary key value cannot be null");
        }

        // 将主键值转换为字符串，这里简单处理，实际应用中需要更复杂的处理
        String valueStr = pkValue.toString();
        if (pkValue instanceof String) {
            valueStr = "'" + valueStr.replace("'", "''") + "'"; // 处理字符串中的单引号
        } else if (pkValue instanceof Number || pkValue instanceof Boolean) {
            // 数字和布尔值通常不需要引号
        } else {
            // 其他类型可能需要额外的处理或转换
        }

        // 构建DELETE语句
        String sql = "DELETE FROM " + tableName + " WHERE " + pk + " = " + valueStr + ";";
        return sql;
    }

}
