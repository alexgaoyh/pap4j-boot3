package cn.net.pap.common.jsonorm.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Iterator;
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

}

