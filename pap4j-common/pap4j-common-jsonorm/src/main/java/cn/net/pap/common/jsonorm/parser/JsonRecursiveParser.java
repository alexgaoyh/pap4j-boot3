package cn.net.pap.common.jsonorm.parser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;

/**
 * JSON 递归解析工具类
 * 功能：
 * 1. 将任意 JSON（对象或数组）解析为 List<Map<String, Object>>
 * 2. 自动处理嵌套对象和数组
 * 3. 支持基本类型（String, Number, Boolean, null）
 */
public class JsonRecursiveParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        // 确保 Jackson 不改变字段顺序（可选）
        OBJECT_MAPPER.configure(JsonParser.Feature.STRICT_DUPLICATE_DETECTION, true);
    }

    /**
     * 将 JSON 字符串解析为 List<Map<String, Object>>
     *
     * @param jsonString JSON 字符串
     * @return List<Map < String, Object>>，如果是对象则包装为长度为1的List
     * @throws IllegalArgumentException 如果JSON不是对象或数组
     */
    public static List<Map<String, Object>> parseToUniversalList(String jsonString) {
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(jsonString);
            return parseNode(rootNode);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON string", e);
        }
    }

    /**
     * 根据 JSON Schema 规范化数据列表
     *
     * @param dataList   原始数据列表
     * @param schemaJson JSON Schema 字符串
     * @return 规范化后的数据列表
     */
    public static List<Map<String, Object>> normalize(List<Map<String, Object>> dataList, String schemaJson) throws Exception {

        JsonNode schemaNode = OBJECT_MAPPER.readTree(schemaJson);
        String schemaType = schemaNode.path("type").asText();

        List<Map<String, Object>> normalizedList = new ArrayList<>();

        if ("array".equals(schemaType)) {
            // 处理数组 Schema
            JsonNode itemsSchema = schemaNode.path("items");
            JsonNode properties = itemsSchema.path("properties");
            for (Map<String, Object> item : dataList) {
                normalizedList.add(normalizeItem(item, properties));
            }
        } else if ("object".equals(schemaType)) {
            // 处理对象 Schema
            JsonNode properties = schemaNode.path("properties");
            for (Map<String, Object> item : dataList) {
                normalizedList.add(normalizeItem(item, properties));
            }
        } else {
            throw new IllegalArgumentException("Schema type must be 'object' or 'array'");
        }

        return normalizedList;
    }

    /**
     * 规范化单个数据项
     */
    private static Map<String, Object> normalizeItem(Map<String, Object> item, JsonNode schemaProperties) {

        Map<String, Object> normalized = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = schemaProperties.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = entry.getKey();
            JsonNode fieldSchema = entry.getValue();

            // 获取字段值（原始数据中可能不存在）
            Object value = item.get(fieldName);

            // 规范化字段值
            Object normalizedValue = normalizeValue(value, fieldSchema);
            normalized.put(fieldName, normalizedValue);
        }
        return normalized;
    }

    /**
     * 规范化字段值
     */
    private static Object normalizeValue(Object value, JsonNode fieldSchema) {
        // 如果原始值为null，使用schema中的默认值或类型默认值
        if (value == null) {
            JsonNode defaultValue = fieldSchema.path("default");
            if (!defaultValue.isMissingNode()) {
                return getValueFromJsonNode(defaultValue);
            }
            return getTypeDefaultValue(fieldSchema.path("type").asText());
        }

        // 类型检查与转换
        String schemaType = fieldSchema.path("type").asText();
        try {
            switch (schemaType) {
                case "string":
                    return value.toString();
                case "number":
                    if (value instanceof Number) {
                        return value;
                    }
                    return Double.parseDouble(value.toString());
                case "integer":
                    if (value instanceof Integer) {
                        return value;
                    }
                    return Integer.parseInt(value.toString());
                case "boolean":
                    if (value instanceof Boolean) {
                        return value;
                    }
                    return Boolean.parseBoolean(value.toString());
                case "object":
                    if (value instanceof Map) {
                        JsonNode properties = fieldSchema.path("properties");
                        if (!properties.isMissingNode()) {
                            return normalizeItem((Map<String, Object>) value, properties);
                        }
                    }
                    return value;
                case "array":
                    if (value instanceof List) {
                        JsonNode itemsSchema = fieldSchema.path("items");
                        if (!itemsSchema.isMissingNode()) {
                            return normalizeArray((List<?>) value, itemsSchema);
                        }
                    }
                    return value;
                default:
                    return value;
            }
        } catch (Exception e) {
            // 类型转换失败时返回默认值
            return getTypeDefaultValue(schemaType);
        }
    }

    /**
     * 规范化数组
     */
    private static List<Object> normalizeArray(List<?> array, JsonNode itemsSchema) {
        List<Object> normalizedArray = new ArrayList<>();
        for (Object item : array) {
            normalizedArray.add(normalizeValue(item, itemsSchema));
        }
        return normalizedArray;
    }

    /**
     * 获取类型的默认值
     */
    private static Object getTypeDefaultValue(String type) {
        switch (type) {
            case "string":
                return "";
            case "number":
                return 0.0;
            case "integer":
                return 0;
            case "boolean":
                return false;
            case "object":
                return new LinkedHashMap<>();
            case "array":
                return new ArrayList<>();
            default:
                return null;
        }
    }

    /**
     * 从JsonNode获取值
     */
    private static Object getValueFromJsonNode(JsonNode node) {
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isNull()) return null;
        if (node.isObject()) return OBJECT_MAPPER.convertValue(node, Map.class);
        if (node.isArray()) return OBJECT_MAPPER.convertValue(node, List.class);
        return null;
    }

    /**
     * 递归解析 JsonNode
     */
    private static List<Map<String, Object>> parseNode(JsonNode node) {
        List<Map<String, Object>> resultList = new ArrayList<>();

        if (node.isArray()) {
            // 如果是数组，遍历每个元素
            for (JsonNode arrayElement : node) {
                if (arrayElement.isObject()) {
                    resultList.add(parseObjectNode(arrayElement));
                } else {
                    // 如果数组元素不是对象，创建一个包含该元素的Map
                    Map<String, Object> wrapperMap = new LinkedHashMap<>();
                    wrapperMap.put("value", parseValueNode(arrayElement));
                    resultList.add(wrapperMap);
                }
            }
        } else if (node.isObject()) {
            // 如果是对象，直接解析并包装为List
            resultList.add(parseObjectNode(node));
        } else {
            // 如果是基本类型，包装为List
            Map<String, Object> wrapperMap = new LinkedHashMap<>();
            wrapperMap.put("value", parseValueNode(node));
            resultList.add(wrapperMap);
        }

        return resultList;
    }

    /**
     * 解析对象节点为Map
     */
    private static Map<String, Object> parseObjectNode(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode valueNode = entry.getValue();

            if (valueNode.isObject()) {
                map.put(key, parseObjectNode(valueNode));
            } else if (valueNode.isArray()) {
                map.put(key, parseArrayNode(valueNode));
            } else {
                map.put(key, parseValueNode(valueNode));
            }
        }

        return map;
    }

    /**
     * 解析数组节点为List
     */
    private static List<Object> parseArrayNode(JsonNode arrayNode) {
        List<Object> list = new ArrayList<>();
        for (JsonNode elementNode : arrayNode) {
            if (elementNode.isObject()) {
                list.add(parseObjectNode(elementNode));
            } else if (elementNode.isArray()) {
                list.add(parseArrayNode(elementNode));
            } else {
                list.add(parseValueNode(elementNode));
            }
        }
        return list;
    }

    /**
     * 解析基本类型节点
     */
    private static Object parseValueNode(JsonNode valueNode) {
        if (valueNode.isTextual()) {
            return valueNode.textValue();
        } else if (valueNode.isNumber()) {
            return valueNode.numberValue();
        } else if (valueNode.isBoolean()) {
            return valueNode.booleanValue();
        } else if (valueNode.isNull()) {
            return null;
        } else {
            throw new IllegalArgumentException("Unsupported JSON value type: " + valueNode.getNodeType());
        }
    }

    /**
     * 将对象转换为JSON字符串（用于测试）
     */
    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert object to JSON", e);
        }
    }
}