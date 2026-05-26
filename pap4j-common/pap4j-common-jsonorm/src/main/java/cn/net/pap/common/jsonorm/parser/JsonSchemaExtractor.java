package cn.net.pap.common.jsonorm.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.net.pap.common.jsonorm.dto.ExtractionResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * <p>基于 JSON Schema 的结构化核心数据提取引擎。</p>
 * <p>该引擎采用“嵌套保留”策略，确保提取后的数据依然维持原始的层级结构（如递归分类树），
 * 并深度支持各种逻辑组合器与条件分支。</p>
 */
public class JsonSchemaExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String X_EXTRACT = "x-extract";

    public ExtractionResultDTO extract(String jsonData, String schemaJson) throws Exception {
        JsonNode dataNode = MAPPER.readTree(jsonData);
        JsonNode rootSchema = MAPPER.readTree(schemaJson);

        Object result = process(dataNode, rootSchema, "", new Context(rootSchema, new HashSet<>()));
        
        Map<String, Object> coreFields = new LinkedHashMap<>();
        if (result instanceof Map<?, ?> map) {
            map.forEach((k, v) -> coreFields.put(k.toString(), v));
        } else if (result != null) {
            coreFields.put("root", result);
        }

        return new ExtractionResultDTO(coreFields, jsonData);
    }

    private Object process(JsonNode data, JsonNode schema, String absPath, Context ctx) {
        if (schema == null || schema.isMissingNode() || data == null || data.isMissingNode()) return null;

        if (schema.has("$ref")) {
            String ref = schema.get("$ref").asText();
            String visitKey = ref + "@" + absPath;
            if (ctx.visitedRefs.contains(visitKey)) return null;
            ctx.visitedRefs.add(visitKey);
            try {
                return process(data, resolveRef(ctx.rootSchema, ref), absPath, ctx);
            } finally {
                ctx.visitedRefs.remove(visitKey);
            }
        }

        Map<String, Object> currentLevelMap = new LinkedHashMap<>();

        Object combined = handleCombinators(data, schema, absPath, ctx);
        if (combined instanceof Map<?, ?> m) {
            m.forEach((k, v) -> currentLevelMap.put(k.toString(), v));
        } else if (combined != null) {
            return combined;
        }

        mergeToResult(currentLevelMap, process(data, schema.path("then"), absPath, ctx));
        mergeToResult(currentLevelMap, process(data, schema.path("else"), absPath, ctx));

        if (data.isObject()) {
            processObjectFields(data, schema, absPath, currentLevelMap, ctx);
        } else if (data.isArray()) {
            Object arrayResult = processArrayItems(data, schema, absPath, ctx);
            if (arrayResult != null) return arrayResult;
        }

        if (schema.path(X_EXTRACT).asBoolean()) {
            if (currentLevelMap.isEmpty()) {
                return convertNodeValue(data);
            }
        }
        
        return currentLevelMap.isEmpty() ? null : currentLevelMap;
    }

    private void processObjectFields(JsonNode data, JsonNode schema, String absPath, Map<String, Object> result, Context ctx) {
        JsonNode props = schema.path("properties");
        if (props.isObject()) {
            props.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                Object val = process(data.path(key), entry.getValue(), absPath + "." + key, ctx);
                if (val != null) result.put(key, val);
            });
        }
        JsonNode patterns = schema.path("patternProperties");
        if (patterns.isObject()) {
            patterns.fields().forEachRemaining(pEntry -> {
                Pattern p = Pattern.compile(pEntry.getKey());
                data.fields().forEachRemaining(dEntry -> {
                    if (p.matcher(dEntry.getKey()).matches()) {
                        Object val = process(dEntry.getValue(), pEntry.getValue(), absPath + "." + dEntry.getKey(), ctx);
                        if (val != null) result.put(dEntry.getKey(), val);
                    }
                });
            });
        }
    }

    private Object processArrayItems(JsonNode data, JsonNode schema, String absPath, Context ctx) {
        JsonNode items = schema.path("items");
        if (items.isObject()) {
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                Object val = process(data.get(i), items, absPath + "[" + i + "]", ctx);
                if (val != null) {
                    if (val instanceof Map<?, ?> m && m.size() == 1 && m.containsKey("root")) {
                        list.add(m.get("root"));
                    } else {
                        list.add(val);
                    }
                }
            }
            return list.isEmpty() ? null : list;
        } else if (items.isArray()) {
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                Object val = process(data.path(i), items.get(i), absPath + "[" + i + "]", ctx);
                if (val != null) list.add(val);
            }
            return list.isEmpty() ? null : list;
        }
        return null;
    }

    private Object handleCombinators(JsonNode data, JsonNode schema, String absPath, Context ctx) {
        String[] combs = {"allOf", "anyOf", "oneOf"};
        Map<String, Object> merged = new LinkedHashMap<>();
        boolean foundMap = false;
        for (String comb : combs) {
            JsonNode arr = schema.path(comb);
            if (arr.isArray()) {
                for (JsonNode sub : arr) {
                    Object res = process(data, sub, absPath, ctx);
                    if (res instanceof Map<?, ?> m) {
                        m.forEach((k, v) -> merged.put(k.toString(), v));
                        foundMap = true;
                    } else if (res != null) {
                        return res; 
                    }
                }
            }
        }
        return foundMap ? merged : null;
    }

    private void mergeToResult(Map<String, Object> target, Object source) {
        if (source instanceof Map<?, ?> m) {
            m.forEach((k, v) -> target.put(k.toString(), v));
        }
    }

    /**
     * <p>将结构化提取结果转换为适合关系型数据库列映射的“完全平铺”格式。</p>
     * <ul>
     *   <li><b>1:1 关系 (Map):</b> 递归拍扁，Key 使用点号拼接（如 user.userId）。</li>
     *   <li><b>1:N 关系 (List):</b> 整体序列化为 JSON 字符串存储。</li>
     *   <li><b>基本类型:</b> 转换为 String。</li>
     * </ul>
     */
    public Map<String, String> toFlattenedStorageMap(Map<String, Object> coreFields) {
        Map<String, String> flattenedMap = new LinkedHashMap<>();
        flattenRecursive(coreFields, "", flattenedMap);
        return flattenedMap;
    }

    private void flattenRecursive(Object value, String prefix, Map<String, String> target) {
        if (value == null) {
            if (!prefix.isEmpty()) target.put(prefix, null);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                String newPrefix = prefix.isEmpty() ? k.toString() : prefix + "." + k.toString();
                flattenRecursive(v, newPrefix, target);
            });
        } else if (value instanceof List<?>) {
            try { target.put(prefix, MAPPER.writeValueAsString(value)); }
            catch (Exception e) { target.put(prefix, value.toString()); }
        } else {
            target.put(prefix, value.toString());
        }
    }

    public Map<String, String> toStorageReadyMap(Map<String, Object> coreFields) {
        Map<String, String> storageMap = new LinkedHashMap<>();
        coreFields.forEach((key, value) -> {
            if (value == null) storageMap.put(key, null);
            else if (value instanceof String s) storageMap.put(key, s);
            else if (value instanceof Number || value instanceof Boolean) storageMap.put(key, value.toString());
            else {
                try { storageMap.put(key, MAPPER.writeValueAsString(value)); }
                catch (Exception e) { storageMap.put(key, value.toString()); }
            }
        });
        return storageMap;
    }

    private Object convertNodeValue(JsonNode node) {
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.decimalValue();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isNull()) return null;
        return node;
    }

    private JsonNode resolveRef(JsonNode root, String ref) {
        if (ref.startsWith("#")) return root.at(ref.substring(1));
        return null;
    }

    private record Context(JsonNode rootSchema, Set<String> visitedRefs) {}
}
