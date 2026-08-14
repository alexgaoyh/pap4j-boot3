package cn.net.pap.common.jsonorm.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>JSON Schema (Draft-07) → Elasticsearch Mapping 转换器。</p>
 * <p>依据 {@code JSON_SCHEMA_ES_MAPPING_GUIDE.md} 规则,将 JSON Schema 递归转换为 ES index mapping (settings + mappings)。</p>
 */
public final class JsonSchemaToEsMappingUtil {

    // 设计说明:JSON Schema / ES mapping 规范关键字(如 "oneOf"、"items"、"nested"、"scaled_float")
    // 属于输入/输出格式的领域词汇,按内联字面量书写;真正的魔法数值(1024/10000/8/date pattern 等)抽 static final 常量。
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_DEPTH = 8;
    private static final int KEYWORD_IGNORE_ABOVE = 1024;
    private static final int DEFAULT_MONEY_FACTOR = 10000;
    private static final String DEFAULT_ANALYZER = "standard";
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss||strict_date_optional_time";

    private JsonSchemaToEsMappingUtil() {
    }

    /** 将 JSON Schema 字符串转换为 ES 索引创建请求体。 */
    public static Map<String, Object> generateIndexMapping(String schemaJson) {
        return generateIndexMapping(schemaJson, null, DEFAULT_MONEY_FACTOR);
    }

    /** 将 JSON Schema 字符串转换为 ES 索引创建请求体(支持自定义金额字段与缩放因子)。 */
    public static Map<String, Object> generateIndexMapping(String schemaJson, Set<String> moneyFields, int moneyScalingFactor) {
        if (schemaJson == null || schemaJson.isBlank()) {
            throw new IllegalArgumentException("schemaJson 不能为空");
        }
        try {
            return generateIndexMapping(MAPPER.readTree(schemaJson), moneyFields, moneyScalingFactor);
        } catch (Exception e) {
            throw new IllegalArgumentException("Schema 解析失败: " + e.getMessage(), e);
        }
    }

    /** 将 JSON Schema 节点转换为 ES 索引创建请求体。 */
    public static Map<String, Object> generateIndexMapping(JsonNode schema) {
        return generateIndexMapping(schema, null, DEFAULT_MONEY_FACTOR);
    }

    /** 将 JSON Schema 节点转换为 ES 索引创建请求体(支持自定义金额字段与缩放因子)。 */
    public static Map<String, Object> generateIndexMapping(JsonNode schema, Set<String> moneyFields, int moneyScalingFactor) {
        if (schema == null || schema.isNull()) {
            throw new IllegalArgumentException("Schema 不能为空");
        }
        String rootType = resolveType(schema);
        boolean objectLike = "object".equals(rootType) || schema.has("properties") || schema.has("oneOf") || schema.has("anyOf");
        if (!objectLike) {
            throw new IllegalArgumentException("根节点必须是 object 类型的 schema,实际类型: " + (rootType == null ? "unknown" : rootType));
        }

        MappingContext ctx = new MappingContext(schema, moneyFields, moneyScalingFactor > 0 ? moneyScalingFactor : DEFAULT_MONEY_FACTOR);
        Map<String, Object> mappings = new LinkedHashMap<>(4);

        if (schema.has("oneOf") || schema.has("anyOf")) {
            Map<String, Object> union = mapUnion("", schema, ctx, 0);
            if (!union.containsKey("properties")) {
                throw new IllegalArgumentException("根节点 oneOf/anyOf 各分支必须为 object 类型");
            }
            mappings.put("dynamic", "false");
            mappings.put("properties", union.get("properties"));
        } else {
            Map<String, Object> rootObject = mapObject("", schema, ctx, 0);
            if (rootObject.containsKey("dynamic")) {
                mappings.put("dynamic", rootObject.get("dynamic"));
            }
            mappings.put("properties", rootObject.get("properties"));
        }

        Map<String, Object> body = new LinkedHashMap<>(4);
        body.put("settings", Map.of("number_of_shards", 1, "number_of_replicas", 0));
        body.put("mappings", mappings);
        return body;
    }

    /** 将生成的 mapping 结构序列化为格式化 JSON 字符串。 */
    public static String toJson(Map<String, Object> indexMapping) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(indexMapping);
        } catch (Exception e) {
            throw new IllegalStateException("Mapping 序列化失败: " + e.getMessage(), e);
        }
    }

    private record MappingContext(JsonNode root, Set<String> moneyFields, int scalingFactor) {}

    // ==================== 递归解析与映射 ====================

    private static Map<String, Object> resolveNode(String key, JsonNode node, MappingContext ctx, int depth) {
        JsonNode schema = resolveRef(node, ctx.root);
        if (schema.hasNonNull("const")) {
            return mapConst(key, schema.get("const"), ctx);
        }
        if (schema.hasNonNull("enum") && schema.get("enum").isArray() && !schema.get("enum").isEmpty()) {
            return mapEnum(key, schema.get("enum"), ctx);
        }
        if (schema.hasNonNull("oneOf") || schema.hasNonNull("anyOf")) {
            return mapUnion(key, schema, ctx, depth);
        }

        String type = resolveType(schema);
        if (type == null) {
            if (schema.has("properties")) type = "object";
            else if (schema.has("items")) type = "array";
            else return textField();
        }
        return mapBySchemaType(key, type, schema, ctx, depth);
    }

    private static Map<String, Object> mapBySchemaType(String key, String type, JsonNode schema, MappingContext ctx, int depth) {
        return switch (type) {
            case "object" -> mapObject(key, schema, ctx, depth);
            case "array" -> mapArray(key, schema, ctx, depth);
            case "string" -> mapString(schema);
            case "integer" -> Map.of("type", "long");
            case "number" -> isMoneyKey(key, ctx.moneyFields)
                    ? Map.of("type", "scaled_float", "scaling_factor", ctx.scalingFactor)
                    : Map.of("type", "double");
            case "boolean" -> Map.of("type", "boolean");
            case "null" -> null;
            default -> textField();
        };
    }

    private static Map<String, Object> mapObject(String key, JsonNode schema, MappingContext ctx, int depth) {
        JsonNode properties = schema.get("properties");
        if (properties == null || !properties.isObject() || properties.isEmpty() || depth >= MAX_DEPTH) {
            return Map.of("type", "flattened");
        }
        Map<String, Object> fieldMapping = new LinkedHashMap<>(4);
        fieldMapping.put("type", "object");
        if (isAdditionalPropertiesFalse(schema)) {
            fieldMapping.put("dynamic", "false");
        }
        Map<String, Object> props = buildProperties(properties, ctx, depth + 1);
        mergeIfThenElse(props, schema, ctx, depth + 1);
        fieldMapping.put("properties", props);
        return fieldMapping;
    }

    private static Map<String, Object> mapArray(String key, JsonNode schema, MappingContext ctx, int depth) {
        JsonNode items = schema.get("items");
        if (items == null) return textField();
        if (items.isArray() && !items.isEmpty()) items = items.get(0);

        JsonNode itemSchema = resolveRef(items, ctx.root);
        String itemType = resolveType(itemSchema);
        boolean itemIsObject = "object".equals(itemType) || (itemType == null && (itemSchema.has("properties") || itemSchema.has("oneOf") || itemSchema.has("anyOf")));
        if (!itemIsObject) {
            Map<String, Object> elementMapping = resolveNode(key, itemSchema, ctx, depth);
            return elementMapping == null ? keywordField() : elementMapping;
        }

        Map<String, Object> nested = new LinkedHashMap<>(4);
        nested.put("type", "nested");
        if (isAdditionalPropertiesFalse(itemSchema) || depth >= MAX_DEPTH) {
            nested.put("dynamic", "false");
        }
        if (depth >= MAX_DEPTH) return nested;

        if (itemSchema.hasNonNull("oneOf") || itemSchema.hasNonNull("anyOf")) {
            Map<String, Object> itemMapping = mapUnion(key, itemSchema, ctx, depth);
            if (itemMapping.containsKey("properties")) {
                nested.put("properties", itemMapping.get("properties"));
            }
            return nested;
        }

        JsonNode itemProps = itemSchema.get("properties");
        if (itemProps == null || !itemProps.isObject() || itemProps.isEmpty()) {
            return Map.of("type", "flattened");
        }
        nested.put("properties", buildProperties(itemProps, ctx, depth + 1));
        return nested;
    }

    private static Map<String, Object> mapUnion(String key, JsonNode schema, MappingContext ctx, int depth) {
        JsonNode branches = schema.hasNonNull("oneOf") ? schema.get("oneOf") : schema.get("anyOf");
        if (branches == null || !branches.isArray() || branches.isEmpty() || !isAllObjectBranches(branches, ctx.root)) {
            return branches != null && branches.isArray() && !branches.isEmpty() ? Map.of("type", "flattened") : textField();
        }

        List<Map<String, Object>> branchMappings = new ArrayList<>(branches.size());
        for (JsonNode branch : branches) {
            JsonNode b = resolveRef(branch, ctx.root);
            JsonNode props = b.get("properties");
            if (props != null && props.isObject() && !props.isEmpty()) {
                branchMappings.add(buildProperties(props, ctx, depth + 1));
            }
        }
        if (branchMappings.isEmpty()) return Map.of("type", "flattened");

        Map<String, Object> union = new LinkedHashMap<>(16);
        Map<String, Map<String, Object>> firstSeen = new LinkedHashMap<>(16);
        for (Map<String, Object> branch : branchMappings) {
            for (Map.Entry<String, Object> entry : branch.entrySet()) {
                String fieldKey = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> fieldMapping = (Map<String, Object>) entry.getValue();
                Map<String, Object> prev = firstSeen.putIfAbsent(fieldKey, fieldMapping);
                if (prev == null) {
                    union.put(fieldKey, fieldMapping);
                } else if (!prev.equals(fieldMapping)) {
                    union.put(fieldKey, Map.of("type", "flattened"));
                }
            }
        }
        return Map.of("type", "object", "properties", union);
    }

    private static boolean isAllObjectBranches(JsonNode branches, JsonNode root) {
        for (JsonNode branch : branches) {
            JsonNode b = resolveRef(branch, root);
            String bt = resolveType(b);
            if ("null".equals(bt)) continue;
            boolean objectLike = "object".equals(bt) || (bt == null && b.has("properties"));
            if (!objectLike || !b.has("properties") || !b.get("properties").isObject() || b.get("properties").isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> mapString(JsonNode schema) {
        String format = schema.hasNonNull("format") ? schema.get("format").asText() : null;
        if (format == null) return textField();
        return switch (format) {
            case "date-time" -> Map.of("type", "date", "format", DATE_TIME_FORMAT, "ignore_malformed", true);
            case "date" -> Map.of("type", "date", "format", "yyyy-MM-dd");
            case "ipv4", "ipv6" -> Map.of("type", "ip");
            case "time", "email", "uuid", "uri", "uri-reference", "hostname" -> keywordField();
            default -> keywordField();
        };
    }

    private static Map<String, Object> mapEnum(String key, JsonNode enumValues, MappingContext ctx) {
        String firstType = null;
        for (JsonNode value : enumValues) {
            String t = jsonType(value);
            if (firstType == null) firstType = t;
            else if (!firstType.equals(t)) { firstType = "mixed"; break; }
        }
        return switch (firstType == null ? "mixed" : firstType) {
            case "string" -> keywordField();
            case "boolean" -> Map.of("type", "boolean");
            case "integer" -> Map.of("type", "long");
            case "number" -> isMoneyKey(key, ctx.moneyFields)
                    ? Map.of("type", "scaled_float", "scaling_factor", ctx.scalingFactor)
                    : Map.of("type", "double");
            case "null" -> null;
            default -> keywordField();
        };
    }

    private static Map<String, Object> mapConst(String key, JsonNode value, MappingContext ctx) {
        return mapEnum(key, MAPPER.createArrayNode().add(value), ctx);
    }

    private static String jsonType(JsonNode v) {
        if (v.isTextual()) return "string";
        if (v.isBoolean()) return "boolean";
        if (v.isIntegralNumber()) return "integer";
        if (v.isNumber()) return "number";
        if (v.isNull()) return "null";
        if (v.isObject()) return "object";
        if (v.isArray()) return "array";
        return "string";
    }

    private static JsonNode resolveRef(JsonNode schema, JsonNode root) {
        JsonNode current = schema;
        Set<String> seen = new HashSet<>(4);
        while (current != null && current.hasNonNull("$ref")) {
            String ref = current.get("$ref").asText();
            if ("#".equals(ref)) return root;
            if (!seen.add(ref)) throw new IllegalArgumentException("检测到 $ref 循环引用: " + ref);
            if (ref.startsWith("#/")) {
                JsonNode target = root.at(ref.substring(1));
                if (!target.isMissingNode()) { current = target; continue; }
            }
            throw new IllegalArgumentException("无法解析 $ref: " + ref);
        }
        return current;
    }

    private static String resolveType(JsonNode schema) {
        JsonNode type = schema.get("type");
        if (type == null) return null;
        if (type.isTextual()) return type.asText();
        if (type.isArray()) {
            for (JsonNode t : type) {
                if (!"null".equals(t.asText())) return t.asText();
            }
            return "null";
        }
        return null;
    }

    private static Map<String, Object> buildProperties(JsonNode properties, MappingContext ctx, int depth) {
        Map<String, Object> result = new LinkedHashMap<>(properties.size());
        for (Iterator<Map.Entry<String, JsonNode>> it = properties.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String fieldKey = entry.getKey();
            validateFieldName(fieldKey);
            Map<String, Object> child = resolveNode(fieldKey, entry.getValue(), ctx, depth);
            if (child != null && !child.isEmpty()) result.put(fieldKey, child);
        }
        return result;
    }

    private static void mergeIfThenElse(Map<String, Object> props, JsonNode schema, MappingContext ctx, int depth) {
        for (String branchKey : List.of("then", "else")) {
            JsonNode branch = schema.get(branchKey);
            if (branch == null || !branch.has("properties") || !branch.get("properties").isObject()) continue;
            for (Iterator<Map.Entry<String, JsonNode>> it = branch.get("properties").fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                String fieldKey = entry.getKey();
                if (props.containsKey(fieldKey)) continue;
                validateFieldName(fieldKey);
                Map<String, Object> child = resolveNode(fieldKey, entry.getValue(), ctx, depth);
                if (child != null && !child.isEmpty()) props.put(fieldKey, child);
            }
        }
    }

    private static void validateFieldName(String key) {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("字段名不能为空");
        if (key.contains(".")) throw new IllegalArgumentException("字段名不能包含点号(会被 ES 当作对象路径): " + key);
        if (key.startsWith("_") || key.startsWith("@")) throw new IllegalArgumentException("字段名不能以 _ 或 @ 开头(ES 保留前缀): " + key);
    }

    private static boolean isAdditionalPropertiesFalse(JsonNode schema) {
        return schema.hasNonNull("additionalProperties") && schema.get("additionalProperties").isBoolean() && !schema.get("additionalProperties").asBoolean();
    }

    private static boolean isMoneyKey(String key, Set<String> moneyFields) {
        return key != null && !key.isEmpty() && moneyFields != null && !moneyFields.isEmpty()
                && (moneyFields.contains(key) || moneyFields.contains(key.toLowerCase()));
    }

    private static Map<String, Object> keywordField() {
        return Map.of("type", "keyword", "ignore_above", KEYWORD_IGNORE_ABOVE);
    }

    private static Map<String, Object> textField() {
        Map<String, Object> m = new LinkedHashMap<>(4);
        m.put("type", "text");
        m.put("analyzer", DEFAULT_ANALYZER);
        m.put("fields", Map.of("keyword", keywordField()));
        return m;
    }
}
