package cn.net.pap.common.jsonorm.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 高效JSON解析工具类，通过字符串池和数字常量池优化内存使用
 */
public class OptimizedJsonParser {

    // 字符串常量池
    private static final Map<String, String> STRING_CONSTANT_POOL = new HashMap<>();

    // 数字常量池
    private static final Map<Integer, Integer> INTEGER_CONSTANT_POOL = new HashMap<>();
    private static final Map<Long, Long> LONG_CONSTANT_POOL = new HashMap<>();
    private static final Map<Double, Double> DOUBLE_CONSTANT_POOL = new HashMap<>();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        // 注册自定义反序列化器
        SimpleModule module = new SimpleModule();
        module.addDeserializer(String.class, new StringPoolDeserializer());
        module.addDeserializer(Integer.class, new IntegerPoolDeserializer());
        module.addDeserializer(Long.class, new LongPoolDeserializer());
        module.addDeserializer(Double.class, new DoublePoolDeserializer());
        OBJECT_MAPPER.registerModule(module);
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 预加载常用数字
        preloadCommonNumbers();
    }

    /**
     * 预加载常用数字到常量池
     */
    private static void preloadCommonNumbers() {
        // 预加载-128到127的整数(Java Integer缓存范围)
        for (int i = -128; i <= 127; i++) {
            INTEGER_CONSTANT_POOL.put(i, i);
        }

        // 预加载常见的状态码等
        int[] commonInts = {200, 201, 400, 401, 403, 404, 500};
        for (int num : commonInts) {
            INTEGER_CONSTANT_POOL.put(num, num);
        }

        // 预加载常见的长整型
        LONG_CONSTANT_POOL.put(0L, 0L);
        LONG_CONSTANT_POOL.put(1L, 1L);
    }

    /**
     * 带常量池优化的反序列化方法
     */
    public static <T> T parseWithOptimization(String json, Class<T> valueType) throws IOException {
        return OBJECT_MAPPER.readValue(json, valueType);
    }

    /**
     * 流式解析JSON文件，适用于大文件
     */
    public static void parseLargeFileWithOptimization(File jsonFile, JsonHandler handler) throws IOException {
        JsonFactory factory = new JsonFactory();
        try (JsonParser parser = factory.createParser(jsonFile)) {
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                handler.handleToken(parser, token);
            }
        }
    }

    // ========== 字符串池化相关 ==========

    /**
     * 自定义字符串反序列化器
     */
    private static class StringPoolDeserializer extends StdDeserializer<String> {
        protected StringPoolDeserializer() {
            super(String.class);
        }

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            return poolString(value);
        }
    }

    /**
     * 字符串池化方法
     */
    public static String poolString(String value) {
        if (value == null) {
            return null;
        }
        return STRING_CONSTANT_POOL.computeIfAbsent(value, String::intern);
    }

    /**
     * 添加预定义字符串常量
     */
    public static void addStringConstant(String constant) {
        if (constant != null) {
            STRING_CONSTANT_POOL.put(constant, constant.intern());
        }
    }

    // ========== 整数池化相关 ==========

    /**
     * 自定义Integer反序列化器
     */
    private static class IntegerPoolDeserializer extends StdDeserializer<Integer> {
        protected IntegerPoolDeserializer() {
            super(Integer.class);
        }

        @Override
        public Integer deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            int value = p.getValueAsInt();
            return poolInteger(value);
        }
    }

    /**
     * 整数池化方法
     */
    public static Integer poolInteger(int value) {
        return INTEGER_CONSTANT_POOL.computeIfAbsent(value, k -> value);
    }

    /**
     * 添加预定义整数常量
     */
    public static void addIntegerConstant(int constant) {
        INTEGER_CONSTANT_POOL.put(constant, constant);
    }

    // ========== 长整型池化相关 ==========

    /**
     * 自定义Long反序列化器
     */
    private static class LongPoolDeserializer extends StdDeserializer<Long> {
        protected LongPoolDeserializer() {
            super(Long.class);
        }

        @Override
        public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            long value = p.getValueAsLong();
            return poolLong(value);
        }
    }

    /**
     * 长整型池化方法
     */
    public static Long poolLong(long value) {
        return LONG_CONSTANT_POOL.computeIfAbsent(value, k -> value);
    }

    /**
     * 添加预定义长整型常量
     */
    public static void addLongConstant(long constant) {
        LONG_CONSTANT_POOL.put(constant, constant);
    }

    // ========== 双精度浮点数池化相关 ==========

    /**
     * 自定义Double反序列化器
     */
    private static class DoublePoolDeserializer extends StdDeserializer<Double> {
        protected DoublePoolDeserializer() {
            super(Double.class);
        }

        @Override
        public Double deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            double value = p.getValueAsDouble();
            return poolDouble(value);
        }
    }

    /**
     * 双精度浮点数池化方法
     */
    public static Double poolDouble(double value) {
        return DOUBLE_CONSTANT_POOL.computeIfAbsent(value, k -> value);
    }

    /**
     * 添加预定义双精度浮点数常量
     */
    public static void addDoubleConstant(double constant) {
        DOUBLE_CONSTANT_POOL.put(constant, constant);
    }

    /**
     * 流式处理接口
     */
    public interface JsonHandler {
        void handleToken(JsonParser parser, JsonToken token) throws IOException;
    }
}
