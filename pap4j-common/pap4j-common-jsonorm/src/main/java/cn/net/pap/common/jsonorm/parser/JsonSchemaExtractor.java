package cn.net.pap.common.jsonorm.parser;

import cn.net.pap.common.jsonorm.dto.ExtractionResultDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * 执行结构化数据提取。
     *
     * @param jsonData 原始 JSON 数据字符串
     * @param schemaJson JSON Schema 字符串
     * @return 提取结果 DTO
     * @throws Exception 解析异常
     */
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

    /**
     * 核心递归处理入口。负责处理 $ref 引用保护和 Schema 归一化。
     */
    private Object process(JsonNode data, JsonNode schema, String absPath, Context ctx) {
        if (schema == null || schema.isMissingNode() || data == null || data.isMissingNode()) return null;

        // 1. 获取当前节点的“有效 Schema”（合并 $ref 和 allOf）
        JsonNode effectiveSchema = getEffectiveSchema(schema, ctx);

        // 2. 检查循环引用保护
        if (schema.has("$ref")) {
            String ref = schema.get("$ref").asText();
            String visitKey = ref + "@" + absPath;
            if (ctx.visitedRefs.contains(visitKey)) return null;
            ctx.visitedRefs.add(visitKey);
            try {
                return doProcess(data, effectiveSchema, absPath, ctx);
            } finally {
                ctx.visitedRefs.remove(visitKey);
            }
        }

        return doProcess(data, effectiveSchema, absPath, ctx);
    }

    /**
     * 获取当前节点的有效 Schema。
     * 该方法会递归合并 $ref 引用和 allOf 组合条件，生成一个扁平化的、包含所有约束的 Schema 节点。
     * 这种“归一化”处理是实现 AOP 式提取标记注入的关键。
     */
    private JsonNode getEffectiveSchema(JsonNode schema, Context ctx) {
        if (schema == null || !schema.isObject()) return schema;

        com.fasterxml.jackson.databind.node.ObjectNode merged = MAPPER.createObjectNode();

        // A. 如果有 $ref，先合并引用的内容
        if (schema.has("$ref")) {
            JsonNode resolved = resolveRef(ctx.rootSchema, schema.get("$ref").asText());
            if (resolved.isObject()) {
                deepMerge(merged, (com.fasterxml.jackson.databind.node.ObjectNode) getEffectiveSchema(resolved, ctx));
            }
        }

        // B. 合并 allOf
        JsonNode allOf = schema.path("allOf");
        if (allOf.isArray()) {
            for (JsonNode sub : allOf) {
                if (sub.isObject()) {
                    deepMerge(merged, (com.fasterxml.jackson.databind.node.ObjectNode) getEffectiveSchema(sub, ctx));
                }
            }
        }

        // C. 合并本地定义的属性（本地定义具有最高优先级，最后合并）
        deepMerge(merged, (com.fasterxml.jackson.databind.node.ObjectNode) schema);
        merged.remove("$ref");
        merged.remove("allOf");

        return merged;
    }

    /**
     * 执行实际的数据提取逻辑。
     */
    private Object doProcess(JsonNode data, JsonNode schema, String absPath, Context ctx) {
        Map<String, Object> currentLevelMap = new LinkedHashMap<>();

        // A. 处理逻辑组合器 (anyOf, oneOf)
        Object combined = handleCombinators(data, schema, absPath, ctx);
        if (combined instanceof Map<?, ?> m) {
            m.forEach((k, v) -> currentLevelMap.put(k.toString(), v));
        } else if (combined != null) {
            return combined;
        }

        // B. 处理条件分支 (then, else)
        mergeToResult(currentLevelMap, process(data, schema.path("then"), absPath, ctx));
        mergeToResult(currentLevelMap, process(data, schema.path("else"), absPath, ctx));

        // C. 处理容器类型属性
        if (data.isObject()) {
            processObjectFields(data, schema, absPath, currentLevelMap, ctx);
        } else if (data.isArray()) {
            Object arrayResult = processArrayItems(data, schema, absPath, ctx);
            if (arrayResult != null) return arrayResult;
        }

        // 3. 最终判定逻辑：如果有 x-extract 标记，或者当前层级有提取出的子字段，则返回结果
        if (schema.path(X_EXTRACT).asBoolean()) {
            if (currentLevelMap.isEmpty()) {
                return convertNodeValue(data);
            }
        }

        return currentLevelMap.isEmpty() ? null : currentLevelMap;
    }

    /**
     * 处理对象类型的字段提取。
     */
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

    /**
     * 处理数组项。支持 standard List 和 Tuple 两种模式。
     */
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

    /**
     * 处理逻辑组合器。
     */
    private Object handleCombinators(JsonNode data, JsonNode schema, String absPath, Context ctx) {
        String[] combs = {"anyOf", "oneOf"};
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

    /**
     * 合并子解析结果到当前 Map。
     */
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
     *   <li><b>基本类型:</b> 保持原始类型。</li>
     * </ul>
     */
    public Map<String, Object> toFlattenedStorageMap(Map<String, Object> coreFields) {
        Map<String, Object> flattenedMap = new LinkedHashMap<>();
        flattenRecursive(coreFields, "", flattenedMap);
        return flattenedMap;
    }

    private void flattenRecursive(Object value, String prefix, Map<String, Object> target) {
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
            try {
                target.put(prefix, MAPPER.writeValueAsString(value));
            } catch (Exception e) {
                target.put(prefix, value.toString());
            }
        } else {
            target.put(prefix, value);
        }
    }

    /**
     * <p>将投影结果转换为存储格式（仅处理顶级 Key，Value 为 JSON 字符串或基本类型字符串）。</p>
     */
    public Map<String, String> toStorageReadyMap(Map<String, Object> coreFields) {
        Map<String, String> storageMap = new LinkedHashMap<>();
        coreFields.forEach((key, value) -> {
            if (value == null) storageMap.put(key, null);
            else if (value instanceof String s) storageMap.put(key, s);
            else if (value instanceof Number || value instanceof Boolean) storageMap.put(key, value.toString());
            else {
                try {
                    storageMap.put(key, MAPPER.writeValueAsString(value));
                } catch (Exception e) {
                    storageMap.put(key, value.toString());
                }
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

    /**
     * 深度合并两个 ObjectNode。
     */
    private void deepMerge(com.fasterxml.jackson.databind.node.ObjectNode mainNode, com.fasterxml.jackson.databind.node.ObjectNode updateNode) {
        updateNode.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey();
            JsonNode value = entry.getValue();
            if (mainNode.has(fieldName) && mainNode.get(fieldName).isObject() && value.isObject()) {
                deepMerge((com.fasterxml.jackson.databind.node.ObjectNode) mainNode.get(fieldName), (com.fasterxml.jackson.databind.node.ObjectNode) value);
            } else {
                mainNode.set(fieldName, value);
            }
        });
    }

    private record Context(JsonNode rootSchema, Set<String> visitedRefs) {
    }
}
