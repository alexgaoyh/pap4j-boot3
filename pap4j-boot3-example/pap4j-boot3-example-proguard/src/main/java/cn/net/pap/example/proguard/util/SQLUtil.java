package cn.net.pap.example.proguard.util;

import cn.net.pap.example.proguard.entity.Proguard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * SQL 生成与处理工具类。
 * 提供将 JSON 结构数据解析并生成对应 SQL INSERT 语句的功能。
 */
public class SQLUtil {

    /**
     * json 2 list map
     * @param jsonStr
     * @return
     * @throws Exception
     */
    public static List<Map<String, JsonNode>> generateJsonNodeFromJson(String jsonStr) throws Exception {
        List<Map<String, JsonNode>> returnList = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(jsonStr);
        if(jsonNode.isObject() && !jsonNode.isArray()) {
            Map<String, JsonNode> returnMapList = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                returnMapList.put(entry.getKey(), entry.getValue());
            }
            returnList.add(returnMapList);
        } else {
            for (JsonNode node : jsonNode) {
                Map<String, JsonNode> returnMapList = new HashMap<>();
                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    returnMapList.put(entry.getKey(), entry.getValue());
                }
                returnList.add(returnMapList);
            }
        }
        return returnList;
    }

    /**
     * 将符合 Proguard 结构的 JSON 字符串解析并转换为实际的 SQL INSERT 语句（只考虑一级节点）。
     *
     * @param tableName 目标数据库表名
     * @param jsonStr   输入的 JSON 字符串
     * @return 组装后的 SQL INSERT 语句
     * @throws Exception 解析或生成过程中抛出异常
     */
    public static String generateInsertSqlFromJson(String tableName, String jsonStr) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(jsonStr);

        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();

        java.lang.reflect.Field[] fields = Proguard.class.getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            if (field.isAnnotationPresent(jakarta.persistence.Transient.class)) {
                continue;
            }

            String columnName = getColumnName(field);
            JsonNode valueNode = jsonNode.get(field.getName());
            if (valueNode == null) {
                valueNode = jsonNode.get(columnName);
            }

            if (valueNode != null && !valueNode.isMissingNode()) {
                if (columns.length() > 0) {
                    columns.append(", ");
                    values.append(", ");
                }
                columns.append(columnName);
                values.append(getSqlValue(field, valueNode, mapper));
            }
        }

        return "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + values + ")";
    }

    /**
     * 根据字段反射信息获取对应的数据库列名。
     * 如果字段上有 @Column 属性且 name 不为空则使用该名称；否则将 camelCase 转换为 snake_case。
     *
     * @param field 反射字段
     * @return 数据库列名
     */
    private static String getColumnName(java.lang.reflect.Field field) {
        if (field.isAnnotationPresent(jakarta.persistence.Column.class)) {
            jakarta.persistence.Column colAnn = field.getAnnotation(jakarta.persistence.Column.class);
            if (colAnn.name() != null && !colAnn.name().isEmpty()) {
                return colAnn.name();
            }
        }
        return convertCamelToSnake(field.getName());
    }

    /**
     * 将解析到的 JsonNode 节点根据目标字段类型转换为对应的 SQL 值字符串。
     * 如果是 JSON 字段、对象或数组，则序列化为 JSON 字符串并进行单引号转义。
     *
     * @param field     反射字段
     * @param valueNode JSON 节点值
     * @param mapper    Jackson ObjectMapper
     * @return 格式化后的 SQL 字符串值
     * @throws Exception 序列化异常
     */
    private static String getSqlValue(java.lang.reflect.Field field, JsonNode valueNode, ObjectMapper mapper) throws Exception {
        if (valueNode.isNull()) {
            return "NULL";
        }
        boolean isJsonField = Map.class.isAssignableFrom(field.getType()) || List.class.isAssignableFrom(field.getType()) || JsonNode.class.isAssignableFrom(field.getType());

        if (isJsonField || valueNode.isContainerNode()) {
            String jsonString = valueNode.isContainerNode() ? mapper.writeValueAsString(valueNode) : valueNode.asText();
            return "'" + jsonString.replace("'", "''") + "'";
        }
        if (valueNode.isTextual()) {
            return "'" + valueNode.asText().replace("'", "''") + "'";
        }
        return valueNode.asText();
    }

    /**
     * 将 JsonNode 格式化为用于 SQL 的字符串表示形式（如包含单引号的转义字符串、数字字面量或 NULL）
     *
     * @param valueNode    JSON 节点值
     * @param objectMapper Jackson ObjectMapper
     * @return 格式化后的 SQL 字符串值
     * @throws Exception 序列化异常
     */
    public static String getSqlValue(JsonNode valueNode, ObjectMapper objectMapper) throws Exception {
        if (valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
            return "NULL";
        }
        if (valueNode.isTextual()) {
            return "'" + valueNode.asText().replace("'", "''") + "'";
        }
        if (valueNode.isNumber()) {
            return valueNode.asText();
        }
        // 如果是复杂的 JSON 对象或数组，序列化为 JSON 字符串后转义并加上单引号
        String jsonStr = objectMapper.writeValueAsString(valueNode);
        return "'" + jsonStr.replace("'", "''") + "'";
    }

    /**
     * 将 camelCase 格式的字符串转换为 snake_case 格式。
     *
     * @param input 输入字符串
     * @return 转换后的 snake_case 字符串
     */
    public static String convertCamelToSnake(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder result = new StringBuilder();
        result.append(Character.toLowerCase(input.charAt(0)));
        for (int i = 1; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append('_').append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

}
