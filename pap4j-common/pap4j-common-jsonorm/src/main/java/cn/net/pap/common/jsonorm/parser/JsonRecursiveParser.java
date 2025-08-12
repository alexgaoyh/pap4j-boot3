package cn.net.pap.common.jsonorm.parser;

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
                    Map<String, Object> wrapperMap = new HashMap<>();
                    wrapperMap.put("value", parseValueNode(arrayElement));
                    resultList.add(wrapperMap);
                }
            }
        } else if (node.isObject()) {
            // 如果是对象，直接解析并包装为List
            resultList.add(parseObjectNode(node));
        } else {
            // 如果是基本类型，包装为List
            Map<String, Object> wrapperMap = new HashMap<>();
            wrapperMap.put("value", parseValueNode(node));
            resultList.add(wrapperMap);
        }

        return resultList;
    }

    /**
     * 解析对象节点为Map
     */
    private static Map<String, Object> parseObjectNode(JsonNode node) {
        Map<String, Object> map = new HashMap<>();
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