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
     * 清空不在目标结构中的字段
     *
     * @param jsonObj         原始对象的JsonNode
     * @param targetStructure 目标结构的JsonNode
     * @return 过滤后的JsonNode
     */
    public static JsonNode filterJson(JsonNode jsonObj, JsonNode targetStructure) {
        ObjectMapper objectMapper = new ObjectMapper();

        com.fasterxml.jackson.databind.node.ObjectNode newJsonNode = objectMapper.createObjectNode();

        // 遍历原始JSON对象的字段
        Iterator<Map.Entry<String, JsonNode>> fields = jsonObj.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = entry.getKey();
            JsonNode fieldValue = entry.getValue();

            // 如果目标结构中有该字段，则将其添加到新的JSON节点中
            if (targetStructure.has(fieldName)) {
                if (fieldValue.isObject()) {
                    // 如果是对象类型，则递归处理
                    newJsonNode.set(fieldName, filterJson(fieldValue, targetStructure.get(fieldName)));
                } else {
                    // 如果是普通字段，直接赋值
                    newJsonNode.set(fieldName, fieldValue);
                }
            }
        }

        return newJsonNode;
    }

}
