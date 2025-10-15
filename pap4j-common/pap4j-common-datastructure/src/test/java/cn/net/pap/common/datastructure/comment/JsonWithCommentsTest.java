package cn.net.pap.common.datastructure.comment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 解析 实体类，返回 JSON 格式，含 comment 注释部分 javadoc.
 */
public class JsonWithCommentsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testGenerateJson() throws Exception {
        String className = "cn.net.pap.common.datastructure.catalog.dto.CatalogTreeDTO";
        ObjectNode json = generateJsonFromClass(Class.forName(className), new HashSet<>(), 0);
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
    }

    /**
     * @param clazz   要处理的类
     * @param visited 已访问过的类
     * @param depth   当前递归深度
     */
    private ObjectNode generateJsonFromClass(Class<?> clazz, Set<Class<?>> visited, int depth) {
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode commentNode = mapper.createObjectNode();

        visited.add(clazz);

        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            String fieldName = field.getName();
            Class<?> fieldType = field.getType();
            Type genericType = field.getGenericType();

            JsonNode valueNode;
            if (visited.contains(fieldType)) {
                // 自关联，只显示一次结构，不递归
                if (Collection.class.isAssignableFrom(fieldType)) {
                    valueNode = mapper.createArrayNode(); // 集合类型显示空
                } else {
                    valueNode = mapper.createObjectNode(); // 对象类型显示空对象
                }
            } else {
                valueNode = generateDefaultValue(fieldType, genericType, visited, depth + 1);
            }

            rootNode.set(fieldName, valueNode);
            commentNode.put(fieldName, fieldName);
        }

        rootNode.set("@comment", commentNode);
        visited.remove(clazz);
        return rootNode;
    }

    private JsonNode generateDefaultValue(Class<?> type, Type genericType, Set<Class<?>> visited, int depth) {
        if (type.isPrimitive() || type == String.class || Number.class.isAssignableFrom(type) || type == Boolean.class || type == Character.class) {
            return defaultPrimitive(type);
        }

        if (type.isEnum()) {
            ArrayNode arrayNode = mapper.createArrayNode();
            for (Object constant : type.getEnumConstants()) {
                arrayNode.add(constant.toString());
            }
            return arrayNode;
        }

        if (Collection.class.isAssignableFrom(type)) {
            ArrayNode arrayNode = mapper.createArrayNode();
            if (genericType instanceof ParameterizedType pType) {
                Type elementType = pType.getActualTypeArguments()[0];
                Class<?> elementClass = getClassFromType(elementType);
                if (elementClass != null) {
                    if (visited.contains(elementClass)) {
                        // 自关联集合，显示一次空对象
                        arrayNode.add(mapper.createObjectNode());
                    } else {
                        arrayNode.add(generateJsonFromClass(elementClass, visited, depth + 1));
                    }
                }
            }
            return arrayNode;
        }

        if (Map.class.isAssignableFrom(type)) {
            ObjectNode mapNode = mapper.createObjectNode();
            mapNode.put("key", "value");
            return mapNode;
        }

        if (type == LocalDate.class || type == LocalDateTime.class || type == Date.class) {
            return new TextNode("1970-01-01T00:00:00");
        }

        if (type == BigDecimal.class || type == BigInteger.class) {
            return new DoubleNode(0.0);
        }

        // 自定义对象
        if (visited.contains(type)) {
            // 自关联对象只显示一次
            return mapper.createObjectNode();
        }
        return generateJsonFromClass(type, visited, depth + 1);
    }

    private Class<?> getClassFromType(Type type) {
        if (type instanceof Class<?> clazz) return clazz;
        if (type instanceof ParameterizedType pType) return getClassFromType(pType.getRawType());
        if (type instanceof WildcardType wType) {
            Type[] bounds = wType.getUpperBounds();
            if (bounds.length > 0) return getClassFromType(bounds[0]);
        }
        if (type instanceof TypeVariable<?> tv) {
            Type[] bounds = tv.getBounds();
            if (bounds.length > 0) return getClassFromType(bounds[0]);
        }
        return null;
    }

    private JsonNode defaultPrimitive(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) return BooleanNode.FALSE;
        if (Number.class.isAssignableFrom(type) || type.isPrimitive()) return new IntNode(0);
        return new TextNode("");
    }

}
