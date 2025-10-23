package cn.net.pap.common.datastructure.comment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
     * 生成带注释的 JSON 结构。
     *
     * @param clazz   要处理的类
     * @param visited 已访问过的类
     * @param depth   当前递归深度
     */
    private ObjectNode generateJsonFromClass(Class<?> clazz, Set<Class<?>> visited, int depth) {
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode commentNode = mapper.createObjectNode();

        // 避免进入 JDK 平台类（例如 java.lang.Class）
        if (isJdkClass(clazz)) {
            return mapper.createObjectNode();
        }

        visited.add(clazz);

        for (Field field : clazz.getDeclaredFields()) {
            // 跳过 JDK 内部类或无法访问的字段
            if (isJdkClass(field.getDeclaringClass())) {
                continue;
            }

            try {
                field.setAccessible(true);
            } catch (Exception e) {
                continue; // 忽略无法访问的字段
            }

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

    /**
     * 为字段类型生成默认 JSON 值。
     */
    private JsonNode generateDefaultValue(Class<?> type, Type genericType, Set<Class<?>> visited, int depth) {
        // 原始类型 / 包装类 / 字符串 / 数字 / 布尔
        if (type.isPrimitive() || type == String.class || Number.class.isAssignableFrom(type) || type == Boolean.class || type == Character.class) {
            return defaultPrimitive(type);
        }

        // 枚举类型
        if (type.isEnum()) {
            ArrayNode arrayNode = mapper.createArrayNode();
            for (Object constant : type.getEnumConstants()) {
                arrayNode.add(constant.toString());
            }
            return arrayNode;
        }

        // 集合类型
        if (Collection.class.isAssignableFrom(type)) {
            ArrayNode arrayNode = mapper.createArrayNode();

            if (genericType instanceof ParameterizedType pType) {
                Type elementType = pType.getActualTypeArguments()[0];
                Class<?> elementClass = getClassFromType(elementType);

                if (elementClass != null) {
                    // 元素是基本/包装类型
                    if (elementClass.isPrimitive() || elementClass == String.class || Number.class.isAssignableFrom(elementClass) || elementClass == Boolean.class || elementClass == Character.class) {
                        arrayNode.add(generateDefaultValue(elementClass, elementType, visited, depth + 1));
                    }
                    // 元素是枚举
                    else if (elementClass.isEnum()) {
                        Object[] consts = elementClass.getEnumConstants();
                        if (consts != null && consts.length > 0) arrayNode.add(consts[0].toString());
                    }
                    // 元素是复杂对象
                    else {
                        if (visited.contains(elementClass)) {
                            // 自关联集合，显示一次空对象
                            arrayNode.add(mapper.createObjectNode());
                        } else {
                            arrayNode.add(generateDefaultValue(elementClass, elementType, visited, depth + 1));
                        }
                    }
                }
            }

            return arrayNode;
        }

        // Map 类型
        if (Map.class.isAssignableFrom(type)) {
            ObjectNode mapNode = mapper.createObjectNode();
            mapNode.put("key", "value");
            return mapNode;
        }

        // 日期 / 时间
        if (type == LocalDate.class || type == LocalDateTime.class || type == Date.class) {
            return new TextNode("1970-01-01T00:00:00");
        }

        // 大数类型
        if (type == BigDecimal.class || type == BigInteger.class) {
            return new DoubleNode(0.0);
        }

        // 已访问过的类型，防止循环
        if (visited.contains(type)) {
            // 自关联对象只显示一次
            return mapper.createObjectNode();
        }

        // 自定义类
        return generateJsonFromClass(type, visited, depth + 1);
    }

    /**
     * 根据类型生成默认基本节点。
     */
    private JsonNode defaultPrimitive(Class<?> type) {
        // boolean
        if (type == boolean.class || type == Boolean.class) return BooleanNode.FALSE;

        // 浮点数
        if (type == double.class || type == Double.class || type == float.class || type == Float.class || type == BigDecimal.class)
            return new DoubleNode(0.0);

        // 整数类
        if (type == byte.class || type == Byte.class || type == short.class || type == Short.class || type == int.class || type == Integer.class || type == long.class || type == Long.class || type == BigInteger.class)
            return new IntNode(0);

        // 其余类型默认字符串
        return new TextNode("");
    }

    /**
     * 从 Type 中提取 Class 对象。
     */
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

    /**
     * 判断是否为 JDK 内部类。
     */
    private boolean isJdkClass(Class<?> clazz) {
        if (clazz == null) return false;
        String pkg = clazz.getPackageName();
        return pkg.startsWith("java.") || pkg.startsWith("javax.") || pkg.startsWith("sun.") || pkg.startsWith("jdk.") || pkg.startsWith("com.sun.");
    }

}
