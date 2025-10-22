package cn.net.pap.common.jsonorm.wrapper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.http.converter.json.MappingJacksonValue;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * 工具类：调用 JsonRawWrapper.wrap(obj, "extraJson") 在 Controller 中直接返回 MappingJacksonValue
 * 该工具会为该 MappingJacksonValue 创建一个临时 ObjectMapper 并注册到内部表，
 * 自定义的 HttpMessageConverter 会在写响应时读取并使用该 ObjectMapper。
 */
public class JsonRawWrapper {

    /**
     * 包装对象，使指定字段按原始 JSON 输出
     */
    public static MappingJacksonValue wrap(Object value, Set<String> rawFields) {
        ObjectMapper mapper = buildRawFieldMapper(rawFields);

        Object finalValue;
        if (value instanceof Map) {
            finalValue = handleMap((Map<?, ?>) value, rawFields, mapper);
        } else {
            // 直接让自定义 ObjectMapper 序列化成 JsonNode
            finalValue = mapper.valueToTree(value);
        }

        // 返回包装对象
        return new MappingJacksonValue(finalValue);
    }

    // 构建自定义 ObjectMapper
    private static ObjectMapper buildRawFieldMapper(Set<String> rawFields) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setAnnotationIntrospector(new RawFieldIntrospector(rawFields));
        return mapper;
    }

    // Map 类型专用逻辑
    private static ObjectNode handleMap(Map<?, ?> map, Set<String> rawFields, ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object val = entry.getValue();

            if (rawFields.contains(key) && val instanceof String str) {
                try {
                    JsonNode jsonNode = mapper.readTree(str);
                    node.set(key, jsonNode);
                } catch (Exception e) {
                    node.put(key, str); // fallback：非合法 JSON
                }
            } else {
                node.set(key, mapper.valueToTree(val));
            }
        }
        return node;
    }

    // AnnotationIntrospector 用于处理 Bean 类型
    static class RawFieldIntrospector extends NopAnnotationIntrospector {
        private final Set<String> rawFields;

        RawFieldIntrospector(Set<String> rawFields) {
            this.rawFields = rawFields;
        }

        @Override
        public Object findSerializer(Annotated annotated) {
            String name = null;

            if (annotated instanceof com.fasterxml.jackson.databind.introspect.AnnotatedField field) {
                name = field.getName(); // 字段名
            } else if (annotated instanceof com.fasterxml.jackson.databind.introspect.AnnotatedMethod method) {
                name = method.getName();
                // getter -> 去掉 get/is 前缀并转首字母小写
                if (name.startsWith("get") && name.length() > 3) {
                    name = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                } else if (name.startsWith("is") && name.length() > 2) {
                    name = Character.toLowerCase(name.charAt(2)) + name.substring(3);
                }
            }

            if (name != null && rawFields.contains(name)) {
                return RawJsonStringSerializer.class;
            }
            return null;
        }

    }

    // 自定义序列化器
    public static class RawJsonStringSerializer extends StdSerializer<String> {
        protected RawJsonStringSerializer() {
            super(String.class);
        }

        @Override
        public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (value != null) {
                gen.writeRawValue(value);
            } else {
                gen.writeNull();
            }
        }
    }
}

