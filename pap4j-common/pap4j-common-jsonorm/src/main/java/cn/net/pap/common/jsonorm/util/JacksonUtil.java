package cn.net.pap.common.jsonorm.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class JacksonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * ObjectMapper
     *
     * @param fieldsToExclude
     * @return
     */
    public static ObjectMapper createObjectMapper(List<String> fieldsToExclude) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializerFactory(mapper.getSerializerFactory().withSerializerModifier(new DynamicFieldExclusionModifier(fieldsToExclude)));
        return mapper;
    }

    private static class DynamicFieldExclusionModifier extends BeanSerializerModifier {
        private final List<String> fieldsToExclude;

        public DynamicFieldExclusionModifier(List<String> fieldsToExclude) {
            this.fieldsToExclude = fieldsToExclude;
        }

        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
            return beanProperties.stream().filter(writer -> !fieldsToExclude.contains(writer.getName())).collect(Collectors.toList());
        }
    }

    /**
     * 清空不在目标结构中的字段，支持处理数组
     *
     * @param jsonObj         原始对象的JsonNode
     * @param targetStructure 目标结构的JsonNode
     * @return 过滤后的JsonNode
     */
    public static JsonNode filterJson(JsonNode jsonObj, JsonNode targetStructure) {
        // 判断当前是否为数组
        if (jsonObj.isArray()) {
            // 如果是数组，处理每个元素
            return filterJsonArray(jsonObj, targetStructure);
        } else if (jsonObj.isObject()) {
            // 如果是对象，处理对象的字段
            return filterJsonObject(jsonObj, targetStructure);
        } else {
            // 其他类型直接返回
            return jsonObj;
        }
    }

    /**
     * 处理JSON对象类型的字段
     *
     * @param jsonObj         原始对象的JsonNode
     * @param targetStructure 目标结构的JsonNode
     * @return 过滤后的JsonNode
     */
    private static JsonNode filterJsonObject(JsonNode jsonObj, JsonNode targetStructure) {
        // 生成一个新的ObjectNode
        com.fasterxml.jackson.databind.node.ObjectNode newJsonNode = objectMapper.createObjectNode();

        // 遍历原始JSON对象的字段
        Iterator<Map.Entry<String, JsonNode>> fields = jsonObj.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = entry.getKey();
            JsonNode fieldValue = entry.getValue();

            // 如果目标结构中有该字段，则将其添加到新的JSON节点中
            if (targetStructure != null && targetStructure.isObject() && targetStructure.has(fieldName)) {
                if (fieldValue.isObject()) {
                    // 如果是对象类型，则递归处理
                    newJsonNode.set(fieldName, filterJson(fieldValue, targetStructure.get(fieldName)));
                } else if (fieldValue.isArray()) {
                    // 如果是数组类型，则递归处理
                    newJsonNode.set(fieldName, filterJson(fieldValue, targetStructure.get(fieldName)));
                } else {
                    // 如果是普通字段，直接赋值
                    newJsonNode.set(fieldName, fieldValue);
                }
            }
            if (targetStructure != null && targetStructure.isArray() && targetStructure.size() > 0 && targetStructure.get(0).has(fieldName)) {
                if (fieldValue.isObject()) {
                    // 如果是对象类型，则递归处理
                    newJsonNode.set(fieldName, filterJson(fieldValue, targetStructure.get(0).get(fieldName)));
                } else if (fieldValue.isArray()) {
                    // 如果是数组类型，则递归处理
                    newJsonNode.set(fieldName, filterJson(fieldValue, targetStructure.get(0).get(fieldName)));
                } else {
                    // 如果是普通字段，直接赋值
                    newJsonNode.set(fieldName, fieldValue);
                }
            }
        }

        return newJsonNode;
    }

    /**
     * 处理JSON数组类型的字段
     *
     * @param jsonArray       原始数组的JsonNode
     * @param targetStructure 目标结构的JsonNode
     * @return 过滤后的JsonNode
     */
    private static JsonNode filterJsonArray(JsonNode jsonArray, JsonNode targetStructure) {
        // 创建一个数组节点
        com.fasterxml.jackson.databind.node.ArrayNode newArrayNode = objectMapper.createArrayNode();

        // 遍历原始数组的每个元素
        for (JsonNode element : jsonArray) {
            // 对每个元素递归调用 filterJson
            JsonNode filteredElement = filterJson(element, targetStructure);

            // 如果过滤后的元素包含有效字段且有目标字段，则将其添加到数组中
            if (filteredElement.isObject() && filteredElement.size() > 0) {
                newArrayNode.add(filteredElement);
            }
        }

        return newArrayNode;
    }

    private static final int BATCH_SIZE = 9999;

    /**
     * 批量处理大JSON数组，避免内存溢出
     * JacksonUtil.parseLargeJsonInBatches(filename, batch -> {
     * System.out.println("Processing batch with " + batch.size() + " items");
     * });
     */
    public static void parseLargeJsonInBatches(String filePath, Consumer<List<Map<String, Object>>> batchProcessor) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory factory = mapper.getFactory();

        try (JsonParser parser = factory.createParser(new File(filePath))) {
            // 定位到数组开始
            while (parser.nextToken() != JsonToken.START_ARRAY) {
                // 跳过直到数组开始
            }

            List<Map<String, Object>> currentBatch = new ArrayList<>(BATCH_SIZE);
            int totalCount = 0;

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                Map<String, Object> map = parseObjectToMap(parser);
                if (map != null) {
                    currentBatch.add(map);
                    totalCount++;

                    // 达到批次大小时处理并清空
                    if (currentBatch.size() >= BATCH_SIZE) {
                        batchProcessor.accept(currentBatch);
                        currentBatch = new ArrayList<>(BATCH_SIZE);
                        System.out.println("Processed " + totalCount + " records");
                    }
                }
            }

            // 处理最后一批
            if (!currentBatch.isEmpty()) {
                batchProcessor.accept(currentBatch);
                System.out.println("Total processed: " + totalCount + " records");
            }
        }
    }

    private static Map<String, Object> parseObjectToMap(JsonParser parser) throws IOException {
        if (parser.getCurrentToken() != JsonToken.START_OBJECT) {
            parser.skipChildren();
            return null;
        }

        Map<String, Object> map = new LinkedHashMap<>();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            parser.nextToken(); // 移动到值

            Object value = parseValue(parser);
            map.put(fieldName, value);
        }

        return map;
    }

    private static Object parseValue(JsonParser parser) throws IOException {
        JsonToken token = parser.getCurrentToken();
        switch (token) {
            case VALUE_STRING:
                return parser.getText();
            case VALUE_NUMBER_INT:
                // 根据数值大小返回合适的类型
                if (parser.getNumberType() == JsonParser.NumberType.INT) {
                    return parser.getIntValue();
                } else {
                    return parser.getLongValue();
                }
            case VALUE_NUMBER_FLOAT:
                return parser.getDoubleValue();
            case VALUE_TRUE:
                return true;
            case VALUE_FALSE:
                return false;
            case VALUE_NULL:
                return null;
            case START_OBJECT:
                return parseObjectToMap(parser);
            case START_ARRAY:
                return parseArrayToList(parser);
            default:
                parser.skipChildren();
                return null;
        }
    }

    private static List<Object> parseArrayToList(JsonParser parser) throws IOException {
        List<Object> list = new ArrayList<>();

        while (parser.nextToken() != JsonToken.END_ARRAY) {
            Object value = parseValue(parser);
            list.add(value);
        }

        return list;
    }

    /**
     * 从大 JSON 文件中提取指定 key 的值，返回 Map
     *
     * @param jsonFilePath  JSON 文件路径
     * @param keysToExtract 要提取的 key 集合
     * @return Map<key, value>
     * @throws IOException
     */
    public static Map<String, String> extractKeys(String jsonFilePath, Set<String> keysToExtract) throws IOException {
        Map<String, String> resultMap = new HashMap<>();
        JsonFactory factory = new JsonFactory();

        try (JsonParser parser = factory.createParser(new File(jsonFilePath))) {
            while (!parser.isClosed()) {
                JsonToken token = parser.nextToken();
                if (token == null) break;

                if (JsonToken.FIELD_NAME.equals(token)) {
                    String currentName = parser.getCurrentName();

                    // 如果是目标 key
                    if (keysToExtract.contains(currentName)) {
                        parser.nextToken(); // 移动到值
                        if (parser.currentToken().isScalarValue()) {
                            resultMap.put(currentName, parser.getValueAsString());
                        } else {
                            // 对象或数组，序列化为字符串
                            resultMap.put(currentName, parser.readValueAsTree().toString());
                        }
                    } else {
                        // 跳过不关心的子树
                        parser.nextToken();
                        if (parser.currentToken() == JsonToken.START_OBJECT || parser.currentToken() == JsonToken.START_ARRAY) {
                            parser.skipChildren();
                        }
                    }
                }
            }
        }

        return resultMap;
    }

    /**
     * 从超大 JSON 数组文件中，流式读取指定下标范围的元素 只支持 根节点是数组
     * startIndex 含，endIndex 不含
     *
     * @param filePath   JSON 文件路径
     * @param startIndex 起始索引（含）
     * @param endIndex   结束索引（不含）
     * @return 对应索引范围的 JsonNode 列表
     * @throws Exception 异常
     */
    public static List<JsonNode> readJsonArrayRange(String filePath, int startIndex, int endIndex) throws Exception {
        List<JsonNode> result = new ArrayList<>();

        JsonFactory factory = new JsonFactory();
        try (JsonParser parser = factory.createParser(new File(filePath))) {
            // 移动到第一个 START_ARRAY
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalStateException("文件不是 JSON 数组格式！");
            }
            int index = 0;
            // 读取数组中的每个元素
            while (true) {
                JsonToken token = parser.nextToken();
                if (token == null || token == JsonToken.END_ARRAY) {
                    break;
                }

                JsonNode node = objectMapper.readTree(parser);

                if (index >= startIndex && index < endIndex) {
                    result.add(node);
                }
                if (index >= endIndex) {
                    break;
                }
                index++;
            }
        }
        return result;
    }

    /**
     * 从超大 JSON 数组文件中，流式读取指定下标范围的元素 支持 根节点不是是数组，从根节点过滤
     * @param filePath
     * @param arrayFieldPath
     * @param startIndex
     * @param endIndex
     * @return
     * @throws Exception
     */
    public static List<JsonNode> readJsonArrayRange(String filePath, String arrayFieldPath, int startIndex, int endIndex) throws Exception {
        List<JsonNode> result = new ArrayList<>();
        String[] path = arrayFieldPath.split("\\.");

        JsonFactory factory = new JsonFactory();
        try (JsonParser parser = factory.createParser(new File(filePath))) {
            int pathIndex = 0;

            // 1. 扫描 JSON，找到指定数组字段
            while (parser.nextToken() != null) {
                JsonToken token = parser.getCurrentToken();
                if (token == JsonToken.FIELD_NAME) {
                    String currentName = parser.getCurrentName();
                    if (currentName.equals(path[pathIndex])) {
                        parser.nextToken(); // 移到字段值
                        // 如果已经到路径末尾，且字段值是数组，则进入读取阶段
                        if (pathIndex == path.length - 1 && parser.getCurrentToken() == JsonToken.START_ARRAY) {
                            return readArrayRangeCore(parser, startIndex, endIndex);
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("未找到数组字段：" + arrayFieldPath);
    }

    private static List<JsonNode> readArrayRangeCore(JsonParser parser, int startIndex, int endIndex) throws Exception {
        List<JsonNode> result = new ArrayList<>();
        int index = 0;
        while (true) {
            JsonToken token = parser.nextToken();
            if (token == null || token == JsonToken.END_ARRAY) {
                break;
            }
            // 读取一个节点（对象、数组、字符串、数字都支持）
            JsonNode node = objectMapper.readTree(parser);
            if (index >= startIndex && index < endIndex) {
                result.add(node);
            }
            if (index >= endIndex) {
                break;
            }
            index++;
        }
        return result;
    }

    /**
     * 根节点是对象 其中某个字段（如 "result"）是超大数组 你要 仅分片读取数组，同时保留根对象的其他字段 返回的结果是多个 完整对象（除大数组外的字段 + 当前片段数组）
     * 根为对象，某字段是大数组。读取该数组的 [startIndex, endIndex) 切片，并保留根对象其它字段。
     * @param filePath
     * @param arrayField
     * @param startIndex
     * @param endIndex
     * @return
     * @throws Exception
     */
    public static ObjectNode readObjectWithArraySlice(String filePath, String arrayField, int startIndex, int endIndex ) throws Exception {
        try (JsonParser parser = new JsonFactory().createParser(new File(filePath))) {
            // 根对象
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalStateException("根节点必须是 JSON 对象！");
            }
            ObjectNode root = objectMapper.createObjectNode();
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                // 只处理 FIELD_NAME，其他 token 跳过（防御性）
                if (token != JsonToken.FIELD_NAME) {
                    // 如果遇到非 FIELD_NAME（理论上不会），让 parser 跳过其 children 保持同步
                    parser.skipChildren();
                    continue;
                }
                String fieldName = parser.getCurrentName();
                // 移动到字段值
                token = parser.nextToken();
                if (!arrayField.equals(fieldName)) {
                    // 普通字段：读取整个值（对象/数组/简单类型都支持）
                    JsonNode node = objectMapper.readTree(parser);
                    root.set(fieldName, node);
                } else {
                    // 目标数组字段
                    if (token != JsonToken.START_ARRAY) {
                        throw new IllegalStateException("字段 " + arrayField + " 不是数组！");
                    }
                    ArrayNode slice = readArraySliceAndSkipRest(parser, startIndex, endIndex);
                    root.set(fieldName, slice);
                    // 此时 parser 已经位于 array 的 END_ARRAY（即数组结束）
                    // 下一次 while 的 parser.nextToken() 会移动到下一个字段或 END_OBJECT
                }
            }
            return root;
        }
    }

    /**
     * 从 parser 的当前位置（当前 token 是 START_ARRAY）开始，读取数组片段 [startIndex, endIndex)
     * 并确保跳过数组中剩下的元素直到遇到 END_ARRAY，返回切片 ArrayNode。
     */
    private static ArrayNode readArraySliceAndSkipRest(JsonParser parser, int startIndex, int endIndex) throws Exception {
        ArrayNode array = objectMapper.createArrayNode();
        int index = 0;
        // parser 当前指向的是 START_ARRAY（调用此方法前已验证）
        JsonToken token;
        while ((token = parser.nextToken()) != null) {
            if (token == JsonToken.END_ARRAY) {
                // 正常数组结束
                break;
            }
            // token 是当前元素的起始 token（对象/数组/值等）
            // 使用 readTree 来读取完整的元素值（会把整个元素消费掉）
            JsonNode node = objectMapper.readTree(parser);
            // 如果在目标范围内则收集
            if (index >= startIndex && index < endIndex) {
                array.add(node);
            }
            index++;
            // 如果已经读够（index 已超出 endIndex-1），我们需要跳过数组剩余所有元素
            if (index >= endIndex) {
                // 跳过剩余数组元素直到遇到 END_ARRAY
                // 注意：parser 此时位于上一个元素消费后的 token（可能是 START_OBJECT/END_OBJECT 等），
                // 所以我们需要继续迭代 tokens，skipChildren 用于跳过结构节点的子内容。
                while ((token = parser.nextToken()) != null) {
                    if (token == JsonToken.END_ARRAY) {
                        break; // 找到数组结尾，完成清理
                    }
                    // 如果碰到结构节点（对象/数组的 START），skipChildren 会跳到对应的 END
                    if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                        parser.skipChildren();
                    }
                    // 对于简单值 token（VALUE_STRING, VALUE_NUMBER_INT...）直接继续循环
                }
                break;
            }
        }
        return array;
    }


}
