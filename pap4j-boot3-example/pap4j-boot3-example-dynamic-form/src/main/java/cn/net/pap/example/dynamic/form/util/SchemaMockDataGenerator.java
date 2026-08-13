package cn.net.pap.example.dynamic.form.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * <p>基于 JSON Schema（Draft-07）的随机业务数据生成器。</p>
 * <p>严格遵循 schema 约束（type/enum/const/min-max/multipleOf/format/required/array/oneOf/allOf/$ref），
 * 并叠加轻量字段名语义（amount→金额、status→状态词、name/customer→姓名/公司等）让数据贴近真实业务；
 * 语义未命中或与约束冲突时自动回落纯约束驱动。</p>
 * <p>所有随机均基于 {@link Random}，传入相同 seed 可复现同一批数据。</p>
 */
public final class SchemaMockDataGenerator {

    /** 自引用（$ref: "#"）递归的最大深度，超过后数组不再生成元素以终止递归。 */
    private static final int MAX_DEPTH = 6;

    /** 数组默认最大元素数。 */
    private static final int DEFAULT_MAX_ITEMS = 3;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String[] STATUS_VALUES = {"PAID", "UNPAID", "SHIPPED", "CANCELLED", "REFUNDED"};

    private static final String[] NAME_VALUES = {
            "甲有限公司", "乙有限公司", "丙有限公司",
            "丁有限公司", "戊有限公司", "己有限公司"
    };

    private SchemaMockDataGenerator() {
    }

    /**
     * 根据 JSON Schema 生成 count 条随机业务数据。
     *
     * @param schemaJson 标准 JSON Schema（Draft-07）字符串
     * @param count      生成条数
     * @param seed       随机种子，可为 null（随机）或固定值（可复现）
     * @return 业务数据列表
     * @throws IllegalArgumentException schema 无法解析或根节点生成失败时抛出
     */
    public static List<Map<String, Object>> generate(String schemaJson, int count, Long seed) {
        try {
            JsonNode schema = MAPPER.readTree(schemaJson);
            Random rng = seed == null ? new Random() : new Random(seed);
            List<Map<String, Object>> records = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Object value = gen("", schema, schema, rng, 0);
                if (value instanceof Map) {
                    records.add((Map<String, Object>) value);
                } else {
                    Map<String, Object> wrapper = new LinkedHashMap<>();
                    wrapper.put("data", value);
                    records.add(wrapper);
                }
            }
            return records;
        } catch (Exception e) {
            throw new IllegalArgumentException("Schema 解析失败: " + e.getMessage(), e);
        }
    }

    private static Object gen(String key, JsonNode schema, JsonNode rootSchema, Random rng, int depth) {
        JsonNode s = resolveRef(schema, rootSchema);
        s = pickBranch(s, rng);
        if (s.hasNonNull("const")) {
            return toObject(s.get("const"));
        }
        if (s.hasNonNull("enum") && s.get("enum").isArray() && s.get("enum").size() > 0) {
            JsonNode picked = s.get("enum").get(rng.nextInt(s.get("enum").size()));
            return toObject(picked);
        }
        String type = resolveType(s);
        if (type == null) {
            if (s.has("properties")) {
                type = "object";
            } else if (s.has("items")) {
                type = "array";
            } else {
                return randomScalar(rng);
            }
        }
        switch (type) {
            case "object":
                return genObject(key, s, rootSchema, rng, depth);
            case "array":
                return genArray(key, s, rootSchema, rng, depth);
            case "string":
                return genString(key, s, rng);
            case "number":
                return genNumber(key, s, rng, false);
            case "integer":
                return genNumber(key, s, rng, true);
            case "boolean":
                return rng.nextBoolean();
            case "null":
                return null;
            default:
                return randomScalar(rng);
        }
    }

    /** 解析 $ref（支持 "#" 自引用 与 "#/$defs/x"、"#/definitions/x"）。 */
    private static JsonNode resolveRef(JsonNode schema, JsonNode rootSchema) {
        if (!schema.hasNonNull("$ref")) {
            return schema;
        }
        String ref = schema.get("$ref").asText();
        if ("#".equals(ref)) {
            return rootSchema;
        }
        if (ref.startsWith("#/")) {
            JsonNode target = rootSchema.at(ref.substring(1));
            if (!target.isMissingNode()) {
                return target;
            }
        }
        return schema;
    }

    /**
     * 处理多态/条件分支：oneOf 随机取一分支；allOf 与 if/then 仅在无基础结构时选用
     * （基础 properties 优先，条件逻辑作为近似忽略）。
     */
    private static JsonNode pickBranch(JsonNode schema, Random rng) {
        if (schema.has("type") || schema.has("properties") || schema.has("items")) {
            return schema;
        }
        if (schema.hasNonNull("oneOf") && schema.get("oneOf").isArray() && schema.get("oneOf").size() > 0) {
            return schema.get("oneOf").get(rng.nextInt(schema.get("oneOf").size()));
        }
        if (schema.hasNonNull("allOf") && schema.get("allOf").isArray()) {
            for (JsonNode branch : schema.get("allOf")) {
                if (branch.has("type") || branch.has("properties") || branch.has("items")) {
                    return branch;
                }
                if (branch.has("then")) {
                    return branch.get("then");
                }
            }
            return schema.get("allOf").get(0);
        }
        if (schema.has("then")) {
            return schema.get("then");
        }
        if (schema.has("else")) {
            return schema.get("else");
        }
        return schema;
    }

    private static Map<String, Object> genObject(String key, JsonNode schema, JsonNode rootSchema, Random rng, int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        JsonNode properties = schema.get("properties");
        if (properties == null || !properties.isObject()) {
            return map;
        }
        Set<String> required = new HashSet<>();
        if (schema.hasNonNull("required") && schema.get("required").isArray()) {
            for (JsonNode r : schema.get("required")) {
                required.add(r.asText());
            }
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = properties.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String fieldKey = entry.getKey();
            JsonNode propSchema = entry.getValue();
            if (required.contains(fieldKey) || rng.nextBoolean()) {
                map.put(fieldKey, gen(fieldKey, propSchema, rootSchema, rng, depth + 1));
            }
        }
        return map;
    }

    private static List<Object> genArray(String key, JsonNode schema, JsonNode rootSchema, Random rng, int depth) {
        List<Object> list = new ArrayList<>();
        if (depth >= MAX_DEPTH) {
            return list;
        }
        int minItems = schema.has("minItems") ? schema.get("minItems").asInt() : 0;
        int maxItems = schema.has("maxItems") ? schema.get("maxItems").asInt() : DEFAULT_MAX_ITEMS;
        if (minItems > maxItems) {
            maxItems = minItems;
        }
        int size = minItems + rng.nextInt(Math.max(1, maxItems - minItems + 1));
        JsonNode items = schema.get("items");
        JsonNode itemSchema = null;
        if (items != null) {
            itemSchema = items.isArray() && items.size() > 0 ? items.get(0) : items;
        }
        boolean unique = schema.has("uniqueItems") && schema.get("uniqueItems").asBoolean();
        Set<Object> seen = new HashSet<>();
        for (int i = 0; i < size; i++) {
            Object value = itemSchema == null ? randomScalar(rng) : gen("", itemSchema, rootSchema, rng, depth + 1);
            if (unique) {
                int attempts = 0;
                while (!seen.add(value) && attempts++ < 20) {
                    value = itemSchema == null ? randomScalar(rng) : gen("", itemSchema, rootSchema, rng, depth + 1);
                }
            }
            list.add(value);
        }
        return list;
    }

    private static Object genString(String key, JsonNode schema, Random rng) {
        String format = schema.hasNonNull("format") ? schema.get("format").asText() : null;
        Object semantic = semanticValue(key, schema, rng);
        if (semantic != null) {
            return semantic;
        }
        if (format != null) {
            String formatted = formatValue(format, rng);
            if (formatted != null) {
                return formatted;
            }
        }
        if (schema.hasNonNull("pattern")) {
            String byPattern = generateByPattern(schema.get("pattern").asText(), rng);
            if (byPattern != null) {
                return byPattern;
            }
        }
        int minLen = schema.has("minLength") ? schema.get("minLength").asInt() : 0;
        int maxLen = schema.has("maxLength") ? schema.get("maxLength").asInt() : 24;
        if (minLen > maxLen) {
            maxLen = minLen;
        }
        int len = minLen + rng.nextInt(Math.max(1, maxLen - minLen + 1));
        return randomString(len, rng);
    }

    private static Object genNumber(String key, JsonNode schema, Random rng, boolean integer) {
        boolean money = !integer && isMoneyKey(key);
        if (integer) {
            long lo = schema.has("minimum") ? schema.get("minimum").asLong() : 0L;
            long hi = schema.has("maximum") ? schema.get("maximum").asLong() : 1000L;
            if (schema.has("exclusiveMinimum") && schema.get("exclusiveMinimum").isNumber()) {
                lo = schema.get("exclusiveMinimum").asLong() + 1;
            }
            if (schema.has("exclusiveMaximum") && schema.get("exclusiveMaximum").isNumber()) {
                hi = schema.get("exclusiveMaximum").asLong() - 1;
            }
            if (lo > hi) {
                hi = lo;
            }
            long range = hi - lo + 1;
            long value = lo + Math.floorMod(rng.nextLong(), range);
            if (schema.has("multipleOf")) {
                value = alignToMultiple(value, schema.get("multipleOf").asLong(), lo, hi);
            }
            return (int) value;
        }
        BigDecimal lo = decimalBound(schema, "minimum", false);
        BigDecimal hi = decimalBound(schema, "maximum", true);
        BigDecimal value;
        if (money) {
            double span = hi.subtract(lo).doubleValue();
            value = BigDecimal.valueOf(lo.doubleValue() + rng.nextDouble() * span)
                    .setScale(2, RoundingMode.HALF_UP);
            if (value.compareTo(lo) < 0) {
                value = lo;
            }
            if (value.compareTo(hi) > 0) {
                value = hi;
            }
        } else {
            double span = hi.subtract(lo).doubleValue();
            value = BigDecimal.valueOf(lo.doubleValue() + rng.nextDouble() * span);
        }
        if (schema.has("multipleOf")) {
            value = value.divide(schema.get("multipleOf").decimalValue(), 0, RoundingMode.HALF_UP)
                    .multiply(schema.get("multipleOf").decimalValue());
        }
        return value;
    }

    /** 返回字段对应的语义值，未命中返回 null。 */
    private static Object semanticValue(String key, JsonNode schema, Random rng) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        String k = key.toLowerCase();
        String type = resolveType(schema);
        boolean stringish = type == null || "string".equals(type);
        if (stringish) {
            if (k.endsWith("date") || k.endsWith("time") || k.contains("date") || k.contains("time")) {
                String fmt = schema.hasNonNull("format") ? schema.get("format").asText() : null;
                return randomDate(fmt, rng);
            }
            if (k.contains("email")) {
                return "user" + (1000 + rng.nextInt(9000)) + "@example.com";
            }
            if (k.endsWith("phone") || k.endsWith("mobile") || k.endsWith("tel")) {
                return "13" + String.format("%09d", Math.floorMod(rng.nextLong(), 1_000_000_000L));
            }
            if (k.endsWith("url") || k.endsWith("uri") || k.contains("link")) {
                return "https://example.com/" + randomString(6, rng);
            }
            if (k.equals("status") || k.contains("status") || k.equals("state")) {
                return STATUS_VALUES[rng.nextInt(STATUS_VALUES.length)];
            }
            if (k.contains("name") || k.equals("customer") || k.contains("company") || k.contains("brand")) {
                return NAME_VALUES[rng.nextInt(NAME_VALUES.length)];
            }
            if (k.endsWith("no") || k.endsWith("code") || k.endsWith("id") || k.endsWith("num")) {
                return "NO" + String.format("%06d", Math.floorMod(rng.nextLong(), 1_000_000L));
            }
            if (k.contains("address")) {
                return "某某市某某区某某路" + (1 + rng.nextInt(999)) + "号";
            }
            if (k.equals("grade") || k.endsWith("score") || k.equals("level")) {
                return String.valueOf(60 + rng.nextInt(41));
            }
        }
        return null;
    }

    private static boolean isMoneyKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        String k = key.toLowerCase();
        return k.contains("amount") || k.contains("price") || k.contains("total")
                || k.contains("money") || k.contains("balance") || k.contains("fee");
    }

    private static String formatValue(String format, Random rng) {
        switch (format) {
            case "date":
                return randomDate("date", rng);
            case "date-time":
                return randomDate("date-time", rng);
            case "time":
                return randomDate("time", rng);
            case "email":
                return "user" + (1000 + rng.nextInt(9000)) + "@pap.net.cn";
            case "uuid":
                return uuidFromRng(rng);
            case "uri":
            case "uri-reference":
                return "https://pap.net.cn/" + randomString(6, rng);
            case "ipv4":
                return (1 + rng.nextInt(223)) + "." + rng.nextInt(256) + "."
                        + rng.nextInt(256) + "." + (1 + rng.nextInt(254));
            case "hostname":
                return "svc-" + randomString(5, rng) + ".pap.net.cn";
            default:
                return null;
        }
    }

    private static String randomDate(String format, Random rng) {
        int year = 2020 + rng.nextInt(6);
        int month = 1 + rng.nextInt(12);
        int day = 1 + rng.nextInt(28);
        String date = String.format("%04d-%02d-%02d", year, month, day);
        if (format == null || "date".equals(format)) {
            return date;
        }
        if ("time".equals(format)) {
            return String.format("%02d:%02d:%02d", rng.nextInt(24), rng.nextInt(60), rng.nextInt(60));
        }
        return date + "T" + String.format("%02d:%02d:00", rng.nextInt(24), rng.nextInt(60));
    }

    private static String uuidFromRng(Random rng) {
        return String.format("%08x-%04x-%04x-%04x-%012x",
                rng.nextInt(), rng.nextInt(0x10000), rng.nextInt(0x10000),
                rng.nextInt(0x10000), Math.floorMod(rng.nextLong(), 1L << 48));
    }

    /** 针对少量常见 pattern 生成匹配串，未命中返回 null（调用方回落随机串）。 */
    private static String generateByPattern(String pattern, Random rng) {
        if (pattern == null) {
            return null;
        }
        if (pattern.matches("^\\^\\d{4}-\\d{2}-\\d{2}\\$$")) {
            return randomDate("date", rng);
        }
        if (pattern.matches("^\\^[A-Z]{2}\\d{6}\\$$")) {
            char[] prefix = new char[2];
            for (int i = 0; i < 2; i++) {
                prefix[i] = (char) ('A' + rng.nextInt(26));
            }
            return new String(prefix) + String.format("%06d", Math.floorMod(rng.nextLong(), 1_000_000L));
        }
        if (pattern.matches("^\\^[0-9a-zA-Z]{6,12}\\$$")) {
            return randomString(8, rng);
        }
        return null;
    }

    private static long alignToMultiple(long value, long multiple, long lo, long hi) {
        long aligned = value - Math.floorMod(value, multiple);
        if (aligned < lo) {
            aligned += multiple;
        }
        if (aligned > hi) {
            return lo;
        }
        return aligned;
    }

    private static BigDecimal decimalBound(JsonNode schema, String keyword, boolean upper) {
        if (schema.hasNonNull(keyword) && schema.get(keyword).isNumber()) {
            return schema.get(keyword).decimalValue();
        }
        if (upper) {
            String excl = "exclusiveMaximum";
            if (schema.hasNonNull(excl) && schema.get(excl).isNumber()) {
                return schema.get(excl).decimalValue();
            }
        } else {
            String excl = "exclusiveMinimum";
            if (schema.hasNonNull(excl) && schema.get(excl).isNumber()) {
                return schema.get(excl).decimalValue();
            }
        }
        return upper ? BigDecimal.valueOf(10000) : BigDecimal.ZERO;
    }

    private static String resolveType(JsonNode schema) {
        JsonNode type = schema.get("type");
        if (type == null) {
            return null;
        }
        if (type.isTextual()) {
            return type.asText();
        }
        if (type.isArray() && type.size() > 0) {
            for (JsonNode node : type) {
                if (!"null".equals(node.asText())) {
                    return node.asText();
                }
            }
            return "null";
        }
        return null;
    }

    private static Object randomScalar(Random rng) {
        switch (rng.nextInt(4)) {
            case 0:
                return randomString(6, rng);
            case 1:
                return rng.nextInt(1000);
            case 2:
                return rng.nextBoolean();
            default:
                return rng.nextInt(100);
        }
    }

    private static String randomString(int length, Random rng) {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(rng.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static Object toObject(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isInt() || node.isLong()) {
            return node.asLong();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isObject() || node.isArray()) {
            return MAPPER.convertValue(node, Object.class);
        }
        return node.asText();
    }
}
