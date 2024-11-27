package cn.net.pap.common.jsonorm.util;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
            return beanProperties.stream().filter(writer -> !fieldsToExclude.contains(writer.getName()))
                    .collect(Collectors.toList());
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

}
