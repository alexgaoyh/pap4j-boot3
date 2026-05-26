package cn.net.pap.common.jsonorm.parser;

import cn.net.pap.common.jsonorm.dto.FieldInfoDTO;
import cn.net.pap.common.jsonorm.dto.SchemaDiffResultDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>工业级 JSON Schema 差异分析引擎。</p>
 * <p>该引擎通过静态扫描识别 Schema 中的提取标记，并与存储平铺策略（1:1 拍扁，1:N 字符串）保持高度一致。</p>
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

        // 1. 处理 $ref
        if (schema.has("$ref")) {
            String ref = schema.get("$ref").asText();
            JsonNode resolved = resolveRef(ctx.rootSchema, ref);
            if (resolved != null && !resolved.isMissingNode()) {
                String visitKey = absPath + "@" + ref;
                if (ctx.visitedRefs.contains(visitKey)) return;
                ctx.visitedRefs.add(visitKey);
                try {
                    collect(resolved, path, absPath, infos, ctx);
                } finally {
                    ctx.visitedRefs.remove(visitKey);
                }
            }
            return;
        }

        // 2. 识别当前节点标记
        String type = schema.path("type").asText();
        if (schema.path(X_EXTRACT).asBoolean()) {
            String finalPath = path.isEmpty() ? "root" : path;
            infos.put(finalPath, new FieldInfoDTO(finalPath, type, mapToJavaType(type)));
        }

        // 3. 数组剪枝：遇到数组则不再向下拆解具体的子列，因为 1:N 关系在存储时会被序列化为 JSON 字符串列
        if ("array".equals(type) || schema.has("items")) {
            if (hasMarkersInSubtree(schema, new HashSet<>(), ctx.rootSchema)) {
                String finalPath = path.isEmpty() ? "root" : path;
                if (!infos.containsKey(finalPath)) {
                    infos.put(finalPath, new FieldInfoDTO(finalPath, "array", "List<Object>"));
                }
            }
            return;
        }

        // 4. 对象递归：1:1 关系继续向下拆解为拍扁的列
        JsonNode props = schema.path("properties");
        if (props.isObject()) {
            props.fields().forEachRemaining(entry -> {
                String name = entry.getKey();
                String nextPath = path.isEmpty() ? name : path + "." + name;
                String nextAbsPath = absPath.isEmpty() ? name : absPath + "." + name;
                collect(entry.getValue(), nextPath, nextAbsPath, infos, ctx);
            });
        }

        // 5. 组合器处理
        String[] combs = {"allOf", "anyOf", "oneOf", "then", "else"};
        for (String comb : combs) {
            JsonNode sub = schema.path(comb);
            if (sub.isArray()) {
                for (JsonNode item : sub) collect(item, path, absPath, infos, ctx);
            } else if (sub.isObject()) {
                collect(sub, path, absPath, infos, ctx);
            }
        }

        // 6. 正则属性
        JsonNode patterns = schema.path("patternProperties");
        if (patterns.isObject()) {
            patterns.fields().forEachRemaining(entry -> {
                String pKey = "<pattern:" + entry.getKey() + ">";
                collect(entry.getValue(), (path.isEmpty() ? "" : path + ".") + pKey, (absPath.isEmpty() ? "" : absPath + ".") + pKey, infos, ctx);
            });
        }
    }

    private boolean hasMarkersInSubtree(JsonNode schema, Set<Integer> visited, JsonNode root) {
        if (schema == null || schema.isMissingNode()) return false;
        if (schema.path(X_EXTRACT).asBoolean()) return true;

        int identity = System.identityHashCode(schema);
        if (visited.contains(identity)) return false;
        visited.add(identity);

        if (schema.has("$ref")) {
            return hasMarkersInSubtree(resolveRef(root, schema.get("$ref").asText()), visited, root);
        }

        List<String> keys = List.of("properties", "patternProperties", "items", "allOf", "anyOf", "oneOf", "then", "else");
        for (String key : keys) {
            JsonNode sub = schema.path(key);
            if (sub.isObject()) {
                if (hasMarkersInSubtree(sub, visited, root)) return true;
                if ("properties".equals(key) || "patternProperties".equals(key)) {
                    Iterator<JsonNode> it = sub.elements();
                    while (it.hasNext()) {
                        if (hasMarkersInSubtree(it.next(), visited, root)) return true;
                    }
                }
            } else if (sub.isArray()) {
                for (JsonNode item : sub) {
                    if (hasMarkersInSubtree(item, visited, root)) return true;
                }
            }
        }
        return false;
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
