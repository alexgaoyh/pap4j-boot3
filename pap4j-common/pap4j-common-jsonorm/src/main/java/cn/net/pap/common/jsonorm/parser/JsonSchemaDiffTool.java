package cn.net.pap.common.jsonorm.parser;

import cn.net.pap.common.jsonorm.dto.FieldInfoDTO;
import cn.net.pap.common.jsonorm.dto.SchemaDiffResultDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * <p>工业级 JSON Schema 差异分析引擎。</p>
 */
public class JsonSchemaDiffTool {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaDiffTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String X_EXTRACT = "x-extract";

    public SchemaDiffResultDTO diff(String oldSchemaJson, String newSchemaJson) throws Exception {
        JsonNode oldSchema = MAPPER.readTree(oldSchemaJson);
        JsonNode newSchema = MAPPER.readTree(newSchemaJson);

        Map<String, FieldInfoDTO> oldInfos = new HashMap<>();
        Map<String, FieldInfoDTO> newInfos = new HashMap<>();

        collect(oldSchema, "", "", oldInfos, new Context(oldSchema, new HashSet<>()));
        collect(newSchema, "", "", newInfos, new Context(newSchema, new HashSet<>()));

        Map<String, FieldInfoDTO> added = new HashMap<>();
        newInfos.forEach((path, info) -> {
            if (!oldInfos.containsKey(path)) added.put(path, info);
        });

        Set<String> removed = new HashSet<>();
        oldInfos.keySet().forEach(path -> {
            if (!newInfos.containsKey(path)) removed.add(path);
        });

        return new SchemaDiffResultDTO(added, removed);
    }

    private void collect(JsonNode schema, String path, String absPath, Map<String, FieldInfoDTO> infos, Context ctx) {
        if (schema == null || schema.isMissingNode()) return;

        // 核心防环：针对静态结构的绝对路径防环
        // 在静态分析中，如果 absPath 已经包含该节点，说明进入了无限递归（如分类树）
        // 我们只需扫描到第一层递归即可停止。
        String visitKey = System.identityHashCode(schema) + "@" + absPath;
        if (ctx.visitedRefs.contains(visitKey) || absPath.length() > 500) return;
        ctx.visitedRefs.add(visitKey);

        try {
            // 1. 处理 $ref
            if (schema.has("$ref")) {
                String ref = schema.get("$ref").asText();
                collect(resolveRef(ctx.rootSchema, ref), path, absPath, infos, ctx);
                return;
            }

            // 2. 识别标记
            String type = schema.path("type").asText();
            if (schema.path(X_EXTRACT).asBoolean()) {
                String finalPath = path.isEmpty() ? "root" : path;
                infos.put(finalPath, new FieldInfoDTO(finalPath, type, mapToJavaType(type)));
            }

            // 3. 递归属性
            JsonNode props = schema.path("properties");
            if (props.isObject()) {
                props.fields().forEachRemaining(entry -> {
                    String name = entry.getKey();
                    String nextPath = path.isEmpty() ? name : path + "." + name;
                    String nextAbsPath = absPath.isEmpty() ? name : absPath + "." + name;
                    collect(entry.getValue(), nextPath, nextAbsPath, infos, ctx);
                });
            }

            // 4. 组合器
            String[] combs = {"allOf", "anyOf", "oneOf", "then", "else"};
            for (String comb : combs) {
                JsonNode sub = schema.path(comb);
                if (sub.isArray()) {
                    for (JsonNode item : sub) collect(item, path, absPath, infos, ctx);
                } else if (sub.isObject()) {
                    collect(sub, path, absPath, infos, ctx);
                }
            }

            // 5. 正则属性
            JsonNode patterns = schema.path("patternProperties");
            if (patterns.isObject()) {
                patterns.fields().forEachRemaining(entry -> {
                    String patternKey = entry.getKey();
                    String suffix = "<pattern:" + patternKey + ">";
                    String nextPath = (path.isEmpty() ? "" : path + ".") + suffix;
                    String nextAbsPath = (absPath.isEmpty() ? "" : absPath + ".") + suffix;
                    collect(entry.getValue(), nextPath, nextAbsPath, infos, ctx);
                });
            }

            // 6. 数组处理 (修复 Items 路径)
            JsonNode items = schema.path("items");
            if (items.isObject()) {
                // 静态分析中，进入数组项。为了防止无限递归，我们不在这里无限累加 path。
                // 仅累加 absPath 探测结构。
                collect(items, path, absPath + "[]", infos, ctx);
            } else if (items.isArray()) {
                for (int i = 0; i < items.size(); i++) {
                    String idx = "[" + i + "]";
                    collect(items.get(i), path + idx, absPath + idx, infos, ctx);
                }
            }
        } finally {
            // 注意：不要在 finally 中 remove visitKey，因为我们是针对路径进行剪枝
        }
    }

    private JsonNode resolveRef(JsonNode root, String ref) {
        if (ref.startsWith("#")) return root.at(ref.substring(1));
        return null;
    }

    private String mapToJavaType(String jsonType) {
        return switch (jsonType) {
            case "string" -> "String";
            case "integer" -> "Long";
            case "number" -> "BigDecimal";
            case "boolean" -> "Boolean";
            case "object" -> "Map<String, Object>";
            case "array" -> "List<Object>";
            default -> "Object";
        };
    }

    private record Context(JsonNode rootSchema, Set<String> visitedRefs) {
    }
}
