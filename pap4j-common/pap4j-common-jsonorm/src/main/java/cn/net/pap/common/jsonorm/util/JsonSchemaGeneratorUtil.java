package cn.net.pap.common.jsonorm.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JsonSchemaGeneratorUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将 JSON 字符串转换为 JSON Schema
     *
     * @param jsonString JSON 字符串
     * @return JSON Schema 字符串
     * @throws IOException
     */
    public static String convertJsonToJsonSchema(String jsonString) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(jsonString);
        return generateJsonSchema(jsonNode);
    }

    /**
     * 递归构建 JSON Schema
     *
     * @param node JSON 节点
     * @return JSON Schema 字符串
     */
    private static String generateJsonSchema(JsonNode node) {
        StringBuilder schemaBuilder = new StringBuilder();

        // 获取类型
        if (node.isObject()) {
            schemaBuilder.append("{\"type\":\"object\", \"properties\":{");
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                schemaBuilder.append("\"").append(field.getKey()).append("\":")
                        .append(generateJsonSchema(field.getValue()));
                if (fields.hasNext()) {
                    schemaBuilder.append(",");
                }
            }
            schemaBuilder.append("}}");
        } else if (node.isArray()) {
            schemaBuilder.append("{\"type\":\"array\", \"items\":")
                    .append(generateJsonSchema(node.get(0)))  // 假设数组内的元素类型一致
                    .append("}");
        } else if (node.isTextual()) {
            schemaBuilder.append("{\"type\":\"string\"}");
        } else if (node.isNumber()) {
            schemaBuilder.append("{\"type\":\"number\"}");
        } else if (node.isBoolean()) {
            schemaBuilder.append("{\"type\":\"boolean\"}");
        } else if (node.isNull()) {
            schemaBuilder.append("{\"type\":\"null\"}");
        } else {
            schemaBuilder.append("{\"type\":\"unknown\"}");
        }

        return schemaBuilder.toString();
    }

    /**
     * 根据 JSON Schema 字符串生成 JSON 数据
     *
     * @param jsonSchema JSON Schema 字符串
     * @return 生成的 JSON 字符串
     * @throws IOException
     */
    public static String generateJsonFromSchema(String jsonSchema) throws IOException {
        // 将 JSON Schema 字符串解析为 JsonNode
        JsonNode schemaNode = objectMapper.readTree(jsonSchema);

        // 生成对应的 JSON 数据
        JsonNode generatedJson = generateJsonFromSchemaNode(schemaNode);

        // 返回生成的 JSON 字符串
        return objectMapper.writeValueAsString(generatedJson);
    }

    /**
     * 根据 JSON Schema 节点生成相应的 JSON 数据
     *
     * @param schemaNode JSON Schema 节点
     * @return 生成的 JSON 节点
     */
    private static JsonNode generateJsonFromSchemaNode(JsonNode schemaNode) {
        if (schemaNode.isObject()) {
            ObjectNode jsonObject = objectMapper.createObjectNode();

            // 处理 type 属性
            if (schemaNode.has("type")) {
                String type = schemaNode.get("type").asText();
                if (!type.equals("object")) {
                    jsonObject.set("type", generateJsonForType(type, schemaNode));
                }
            }

            // 处理 properties 字段
            if (schemaNode.has("properties")) {
                JsonNode propertiesNode = schemaNode.get("properties");
                Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    JsonNode fieldValue = field.getValue();
                    if (fieldValue.has("type")) {
                        String fieldType = fieldValue.get("type").asText();
                        jsonObject.set(field.getKey(), generateJsonForType(fieldType, fieldValue));
                    }
                }
            }

            // 如果有 items 字段，表示是数组类型
            if (schemaNode.has("items")) {
                ArrayNode jsonArray = objectMapper.createArrayNode();
                JsonNode itemsNode = schemaNode.get("items");
                if (itemsNode.has("type")) {
                    String itemType = itemsNode.get("type").asText();
                    jsonArray.add(generateJsonForType(itemType, itemsNode));
                }
                jsonObject.set("items", jsonArray);
            }

            return jsonObject;
        } else if (schemaNode.isArray()) {
            ArrayNode jsonArray = objectMapper.createArrayNode();
            if (schemaNode.size() > 0 && schemaNode.get(0).has("type")) {
                String type = schemaNode.get(0).get("type").asText();
                jsonArray.add(generateJsonForType(type, schemaNode.get(0)));
            }
            return jsonArray;
        }
        return objectMapper.nullNode();
    }

    /**
     * 根据类型生成 JSON 数据
     *
     * @param type       数据类型
     * @param schemaNode JSON Schema 节点
     * @return 生成的 JSON 节点
     */
    private static JsonNode generateJsonForType(String type, JsonNode schemaNode) {
        switch (type) {
            case "string":
                return TextNode.valueOf("");
            case "number":
                return DecimalNode.valueOf(BigDecimal.ZERO);
            case "integer":
                return IntNode.valueOf(0);
            case "boolean":
                return BooleanNode.valueOf(true);
            case "array":
                ArrayNode arrayNode = objectMapper.createArrayNode();
                // 假设数组中的元素类型一致，取第一个元素的类型
                if (schemaNode.has("items")) {
                    String itemType = schemaNode.get("items").get("type").asText();
                    arrayNode.add(generateJsonForType(itemType, schemaNode.get("items")));
                }
                return arrayNode;
            case "object":
                return generateJsonFromSchemaNode(schemaNode);
            default:
                return objectMapper.nullNode();
        }
    }

    /**
     * 将 JSON 字符串转换为平铺的 List<String>
     *
     * @param json 输入的 JSON 字符串
     * @return 平铺后的字段路径列表
     * @throws IOException
     */
    public static List<String> flattenJson(String json) throws IOException {
        JsonNode rootNode = objectMapper.readTree(json);
        List<String> result = new ArrayList<>();
        flattenJsonNode(rootNode, "", result);
        return result;
    }

    /**
     * 递归遍历 JSON 节点并平铺
     *
     * @param node        当前的 JSON 节点
     * @param currentPath 当前路径（如 name, address.street 等）
     * @param result      最终的结果列表
     */
    private static void flattenJsonNode(JsonNode node, String currentPath, List<String> result) {
        if (node.isObject()) {
            // 如果当前节点是对象类型，遍历其字段
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldPath = currentPath.isEmpty() ? field.getKey() : currentPath + "." + field.getKey();
                flattenJsonNode(field.getValue(), fieldPath, result);
            }
        } else if (node.isArray()) {
            // 如果当前节点是数组类型，遍历数组中的每个元素
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode arrayElement = arrayNode.get(i);
                String arrayPath = currentPath + "[" + i + "]";
                flattenJsonNode(arrayElement, arrayPath, result);
            }
        } else {
            // 如果当前节点是值类型，直接将路径和值添加到结果列表
            result.add(currentPath + "=" + node.asText());
        }
    }

    /**
     * 将平铺的 List<String> 转换为嵌套的 JSON 字符串
     *
     * @param flatList 平铺的字段列表
     * @return 嵌套的 JSON 字符串
     * @throws IOException
     */
    public static String unflattenJson(List<String> flatList) throws IOException {
        ObjectNode rootNode = objectMapper.createObjectNode();

        // 处理每个平铺的项
        for (String flatEntry : flatList) {
            String[] pathValue = flatEntry.split("=", 2);
            if (pathValue.length == 2) {
                String path = pathValue[0];
                String value = pathValue[1];
                setNestedValue(rootNode, path, value);
            }
        }

        // 返回生成的 JSON 字符串
        return objectMapper.writeValueAsString(rootNode);
    }

    /**
     * 根据路径递归设置嵌套值
     *
     * @param currentNode 当前的 JSON 节点（ObjectNode 或 ArrayNode）
     * @param path        JSON 字段路径（如 "address.street" 或 "phoneNumbers[0].type"）
     * @param value       要设置的值
     */
    private static void setNestedValue(JsonNode currentNode, String path, String value) {
        String[] parts = path.split("\\.", 2);  // 按照 "." 分割路径
        String key = parts[0];

        // 处理数组索引
        if (key.contains("[")) {
            String arrayKey = key.substring(0, key.indexOf("["));
            int index = Integer.parseInt(key.substring(key.indexOf("[") + 1, key.indexOf("]")));

            // 获取目标数组节点或创建新数组节点
            JsonNode targetNode;
            if (currentNode.has(arrayKey)) {
                targetNode = currentNode.get(arrayKey);
            } else {
                targetNode = objectMapper.createArrayNode();
                ((ObjectNode) currentNode).set(arrayKey, targetNode);
            }

            // 确保数组大小足够
            ArrayNode arrayNode = (ArrayNode) targetNode;
            while (arrayNode.size() <= index) {
                arrayNode.add(objectMapper.createObjectNode());  // 填充空元素
            }

            targetNode = arrayNode.get(index);

            // 递归处理剩余路径
            if (parts.length > 1) {
                setNestedValue(targetNode, parts[1], value);
            } else {
                // 设置目标字段的值
                ((ObjectNode) targetNode).put(arrayKey, value);
            }

        } else {
            // 如果当前节点是对象类型，检查是否已经有该字段
            JsonNode targetNode;
            if (currentNode.has(key)) {
                targetNode = currentNode.get(key);
            } else {
                targetNode = objectMapper.createObjectNode();
                ((ObjectNode) currentNode).set(key, targetNode);
            }

            // 如果路径还有下一层，递归处理
            if (parts.length > 1) {
                setNestedValue(targetNode, parts[1], value);
            } else {
                // 到达路径末端，直接设置值
                ((ObjectNode) currentNode).put(key, value);
            }
        }
    }

}

