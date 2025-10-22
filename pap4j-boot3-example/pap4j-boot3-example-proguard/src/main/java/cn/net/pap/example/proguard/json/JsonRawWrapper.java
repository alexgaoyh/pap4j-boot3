package cn.net.pap.example.proguard.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.http.converter.json.MappingJacksonValue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具类：调用 JsonRawWrapper.wrap(obj, "extraJson") 在 Controller 中直接返回 MappingJacksonValue
 * 该工具会为该 MappingJacksonValue 创建一个临时 ObjectMapper 并注册到内部表，
 * 自定义的 HttpMessageConverter 会在写响应时读取并使用该 ObjectMapper。
 * 支持 Map、Bean、List<Map>、List<Bean>。
 */
public class JsonRawWrapper {

    /**
     * 包装对象，使指定字段按原始 JSON 输出
     */
    public static MappingJacksonValue wrap(Object value, String... rawFieldNames) {
        return wrap(value, Set.of(rawFieldNames));
    }

    /**
     * 包装对象，使指定字段按原始 JSON 输出
     */
    public static MappingJacksonValue wrap(Object value, Set<String> rawFields) {
        ObjectMapper mapper = buildRawFieldMapper(rawFields);
        JsonNode finalNode = toJsonNode(mapper, value, rawFields);
        return new MappingJacksonValue(finalNode);
    }

    /**
     * 构建自定义 ObjectMapper（支持 raw 字段）
     */
    private static ObjectMapper buildRawFieldMapper(Set<String> rawFields) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setAnnotationIntrospector(new RawFieldIntrospector(rawFields));
        return mapper;
    }

    /**
     * 通用对象转 JsonNode（支持 List、Map、Bean）
     */
    private static JsonNode toJsonNode(ObjectMapper mapper, Object value, Set<String> rawFields) {
        if (value == null) {
            return NullNode.getInstance();
        }

        if (value instanceof Map<?, ?> map) {
            return handleMap(mapper, map, rawFields);
        } else if (value instanceof List<?> list) {
            ArrayNode arrayNode = mapper.createArrayNode();
            for (Object item : list) {
                arrayNode.add(toJsonNode(mapper, item, rawFields));
            }
            return arrayNode;
        } else {
            // 普通 Bean 交给 Jackson 自动处理
            return mapper.valueToTree(value);
        }
    }

    /**
     * Map 类型处理逻辑（可嵌套）
     */
    private static ObjectNode handleMap(ObjectMapper mapper, Map<?, ?> map, Set<String> rawFields) {
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
                node.set(key, toJsonNode(mapper, val, rawFields));
            }
        }
        return node;
    }

    /**
     * AnnotationIntrospector 用于处理 Bean 类型
     */
    static class RawFieldIntrospector extends NopAnnotationIntrospector {
        private final Set<String> rawFields;

        RawFieldIntrospector(Set<String> rawFields) {
            this.rawFields = rawFields;
        }

        @Override
        public Object findSerializer(Annotated annotated) {
            String name = null;

            if (annotated instanceof AnnotatedField field) {
                name = field.getName();
            } else if (annotated instanceof AnnotatedMethod method) {
                name = extractPropertyNameFromGetter(method.getName());
            }

            if (name != null && rawFields.contains(name)) {
                return RawJsonStringSerializer.class;
            }
            return null;
        }

        /**
         * 从 getter 名提取属性名
         */
        private static String extractPropertyNameFromGetter(String name) {
            if (name.startsWith("get") && name.length() > 3) {
                return Character.toLowerCase(name.charAt(3)) + name.substring(4);
            } else if (name.startsWith("is") && name.length() > 2) {
                return Character.toLowerCase(name.charAt(2)) + name.substring(3);
            }
            return name;
        }
    }

    /**
     * 自定义序列化器：直接输出 JSON 字符串内容
     */
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

