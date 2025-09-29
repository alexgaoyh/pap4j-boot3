package cn.net.pap.common.jsonorm;

import cn.net.pap.common.jsonorm.util.JsonORMUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonPathTest {

    @Test
    public void jsonPath1Test() {
        String json = "{ \"store\": { \"book\": [ { \"title\": \"A\", \"price\": 8 }, { \"title\": \"B\", \"price\": 12 } ] } }";
        com.jayway.jsonpath.DocumentContext ctx = JsonPath.parse(json);

        List<String> titles = ctx.read("$.store.book[?(@.price < 10)].title");

        ctx.set("$.store.book[0].title", "New Title");

        ctx.delete("$.store.book[?(@.price > 10)]");

        String json2 = ctx.jsonString();
        System.out.println(json2);
    }

    @Test
    public void jsonPathHideDelTest() {
        String json = "{\"id\":1,\"name\":\"John Doe\",\"isActive\":true,\"address\":{\"street\":\"123 Main St\",\"city\":\"New York\",\"zipCode\":\"10001\"},\"phoneNumbers\":[{\"type\":\"home\",\"number\":\"123-456-7890\"},{\"type\":\"work\",\"number\":\"987-654-3210\"}],\"orders\":[{\"orderId\":\"A123\",\"items\":[{\"product\":\"Laptop\",\"price\":1200.50},{\"product\":\"Mouse\",\"price\":25.99}]},{\"orderId\":\"B456\",\"items\":[{\"product\":\"Keyboard\",\"price\":45.00}]}]}";
        // 解析 JSON
        com.jayway.jsonpath.DocumentContext ctx = JsonPath.parse(json);

        // 隐藏 address.street 和 phoneNumbers[*].number 字段的值
        ctx.set("$.address.street", "***");
        ctx.set("$.phoneNumbers[*].number", "***");

        // 过滤掉 orders[*].items[*].price 字段
        ctx.delete("$.orders[*].items[*].price");

        String result = ctx.jsonString();
        System.out.println(result);
    }

    @Test
    public void jsonPathInsert1Test() {
        String json = "{}";
        com.jayway.jsonpath.DocumentContext ctx = JsonPath.parse(json);
        List<String> values = Arrays.asList("value1", "value2", "value3");
        ctx.put("$", "arrayNode", values);
        String result = ctx.jsonString();
        System.out.println(result);
    }

    @Test
    public void jsonPathInsert2Test() {
        String json = "{}";
        com.jayway.jsonpath.DocumentContext ctx = JsonPath.parse(json);

        ctx.put("$", "arrayNode", new ArrayList<>());

        Map<String, String> mapName1 = new HashMap<>();
        mapName1.put("name", "1");
        ctx.add("$.arrayNode", mapName1);
        Map<String, String> mapName2 = new HashMap<>();
        mapName2.put("name", "2");
        ctx.add("$.arrayNode", mapName2);

        String result = ctx.jsonString();
        System.out.println(result);
    }

    // @Test
    public void jsonToListMapTest() throws Exception {
        String json = JsonORMUtil.readFileToString(new File("C:\\Users\\86181\\Desktop\\input.json"));
        com.jayway.jsonpath.DocumentContext ctx = JsonPath.parse(json);
        for (int idx = 0; idx < Integer.parseInt(ctx.read("$.length()").toString()); idx++) {
            Map<String, Object> itemMap = (Map<String, Object>) ctx.read("$[" + idx + "]");
            System.out.println(itemMap);
        }
    }

    // @Test
    public void jsonToListMapTest2() throws Exception {
        String json = JsonORMUtil.readFileToString(new File("C:\\Users\\86181\\Desktop\\input.json"));
        // 读取的时候，有可能会读取出来 utf8-bom 的头，要提前做处理
        com.jayway.jsonpath.DocumentContext ctx = JsonPath.parse(json);
        // ctx.read("$..Chars[*].Code");
        JSONArray allChildren = ctx.read("$.._children[*]");
        for (Object child : allChildren) {
            Map<String, Object> childMap = (Map<String, Object>) child;
            System.out.println(childMap.toString());
        }
    }

    // @Test
    public void jsonPathTest2() throws Exception {
        // 如下 json ，数据来源是 count_file.md 文件中的 Export-DirectoryTree.ps1 部分的输出。
        // 同样的的，也可以是 linux 下 tree -J -f 的输出， 但是两者的结果会有不同，当前仅做数据的解析与输出，具体业务需要额外进行逻辑添加。
        String json = JsonORMUtil.readFileToString(new File("C:\\Users\\86181\\Desktop\\test_tree.json"));
        com.jayway.jsonpath.DocumentContext ctx = JsonPath.parse(json);
        // .. 表示递归下降，搜索所有层级的属性，如下命令是找到 Type 属性的值为 File 的部分。
        List<Object> fileNodes = JsonPath.read(json, "$..[?(@.Type == 'File' || @.type == 'file')]");
        for (Object fileNode : fileNodes) {
            Map<String, Object> fileNodeMap = (Map<String, Object>) fileNode;
            System.out.println(fileNodeMap.toString());
        }
    }

    // @Test
    public void jsonHandleTest3() throws Exception {
        String json = JsonORMUtil.readFileToString(new File("C:\\Users\\86181\\Desktop\\test_tree.json"));

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode root = (ArrayNode) mapper.readTree(json);
        addPathRecursive(root, "");

        System.out.println(mapper.writeValueAsString(root));
    }

    private static void addPathRecursive(ArrayNode array, String parentPath) {
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i).isObject()) {
                ObjectNode node = (ObjectNode) array.get(i);
                String type = node.get("type").asText();

                if ("report".equals(type)) {
                    continue; // 跳过report节点
                }

                String name = node.get("name").asText();
                String typeText = node.get("type").asText();
                String currentPath = parentPath.isEmpty() ? name : parentPath + (parentPath.endsWith("/") ? "" : "/") + name;
                if(typeText.equals("directory") && !currentPath.endsWith("/")) {
                    currentPath = currentPath + "/";
                }

                node.put("path", currentPath);

                // 递归处理子节点
                if (node.has("contents") && node.get("contents").isArray()) {
                    addPathRecursive((ArrayNode) node.get("contents"), currentPath);
                }
            }
        }
    }

    @Test
    public void shownPathTest1() throws Exception {
        File dataFile = ResourceUtils.getFile("classpath:json-mapping-insert-multi-layer-test-data-01.json");
        String dataJSON = JsonORMUtil.readFileToString(dataFile);
        // 配置以返回路径信息
        com.jayway.jsonpath.Configuration config = com.jayway.jsonpath.Configuration.builder().options(com.jayway.jsonpath.Option.AS_PATH_LIST).build();
        // 获取所有sub_items的路径
        List<String> paths = JsonPath.using(config).parse(dataJSON).read("$..sub_items[*]");
        System.out.println(paths);

        // 所有路径
        List<String> alls = JsonPath.using(config).parse(dataJSON).read("$..*");
        System.out.println(alls);

        // 筛选出最"深"的路径（通常是叶子节点）
        List<String> leafPaths = alls.stream()
                .filter(path -> {
                    // 如果一个路径是另一个路径的前缀，说明它是中间节点
                    return alls.stream()
                            .noneMatch(otherPath -> !otherPath.equals(path) && otherPath.startsWith(path));
                })
                .collect(Collectors.toList());
        System.out.println(leafPaths);
    }


    @Test
    public void jsonPathTest1() throws Exception {
        File dataFile = ResourceUtils.getFile("classpath:json-mapping-insert-multi-layer-test-data-01.json");
        String dataJSON = JsonORMUtil.readFileToString(dataFile);

        com.jayway.jsonpath.Configuration config = com.jayway.jsonpath.Configuration.builder().mappingProvider(new JacksonMappingProvider()).build();

        List<Integer> quantitys = JsonPath.using(config).parse(dataJSON).read("$.order.items[*].quantity", new TypeRef<List<Integer>>() {});
        Double total = JsonPath.using(config).parse(dataJSON).read("$.order.total", Double.class);

        assertTrue(true);
    }

    /**
     * 定义  mappings.json 和 data.json
     * 前者是 格式化SQL 的定义，后者是 业务数据
     * 做数据的匹配，生成可执行SQL
     *
     * @throws Exception
     */
    @Test
    public void reGeneTest1() throws Exception {
        File mappingFile = ResourceUtils.getFile("classpath:json-mapping-insert-multi-layer-test-mapping-01.json");
        String mappingJSON = JsonORMUtil.readFileToString(mappingFile);

        File dataFile = ResourceUtils.getFile("classpath:json-mapping-insert-multi-layer-test-data-01.json");
        String dataJSON = JsonORMUtil.readFileToString(dataFile);

        com.jayway.jsonpath.DocumentContext mappingCtx = JsonPath.parse(mappingJSON);
        com.jayway.jsonpath.DocumentContext dataCtx = JsonPath.parse(dataJSON);

        // 第一步， 拿着 gene_key 的定义，重新生成一个 data.json 的数据
        Object mappingGeneKeyArrayObj = mappingCtx.read("$.mappings[*].gene_key");
        if (mappingGeneKeyArrayObj != null && mappingGeneKeyArrayObj instanceof JSONArray) {
            JSONArray mappingGeneKeyArray = (JSONArray) mappingGeneKeyArrayObj;
            for (int i = 0; i < mappingGeneKeyArray.size(); i++) {
                Object mappingGeneKeyObj = mappingGeneKeyArray.get(i);
                if (mappingGeneKeyObj != null && mappingGeneKeyObj instanceof HashMap<?, ?>) {
                    HashMap<String, Object> mappingGeneKeyMap = (HashMap<String, Object>) mappingGeneKeyObj;
                    String path = mappingGeneKeyMap.get("path").toString();
                    setValueRecursive(dataCtx, path);
                }
            }
        }
        System.out.println(dataCtx.jsonString());

        // 第二步， 一步一步的执行预定义的 SQL， 得到可执行的SQL
        Object mappingsObjs = mappingCtx.read("$.mappings[*]");
        if (mappingsObjs != null && mappingsObjs instanceof JSONArray) {
            JSONArray mappingsArray = (JSONArray) mappingsObjs;
            for (Object mappingsObj : mappingsArray) {
                if (mappingsObj != null && mappingsObj instanceof HashMap<?, ?>) {
                    HashMap<String, Object> mappingsObjMap = (HashMap<String, Object>) mappingsObj;
                    String sqlTemplate = (String) mappingsObjMap.get("sql");
                    List<String> params = (List<String>) mappingsObjMap.get("params");
                    // 渲染 SQL
                    List<String> renderedSqls = renderSqls(sqlTemplate, params, dataCtx);
                    System.out.println(renderedSqls);
                }
            }
        }
    }

    @Test
    public void reGeneTest2() throws Exception {
        File mappingFile = ResourceUtils.getFile("classpath:json-mapping-select-multi-layer-test-mapping-01.json");
        String mappingJSON = JsonORMUtil.readFileToString(mappingFile);

        File dataFile = ResourceUtils.getFile("classpath:json-mapping-select-multi-layer-test-data-01.json");
        String dataJSON = JsonORMUtil.readFileToString(dataFile);

        com.jayway.jsonpath.DocumentContext mappingCtx = JsonPath.parse(mappingJSON);
        com.jayway.jsonpath.DocumentContext dataCtx = JsonPath.parse(dataJSON);

        Object mappingsObjs = mappingCtx.read("$.mappings[*]");
        if (mappingsObjs != null && mappingsObjs instanceof JSONArray) {
            JSONArray mappingsArray = (JSONArray) mappingsObjs;
            for (Object mappingsObj : mappingsArray) {
                if (mappingsObj != null && mappingsObj instanceof HashMap<?, ?>) {
                    HashMap<String, Object> mappingsObjMap = (HashMap<String, Object>) mappingsObj;
                    String sqlTemplate = (String) mappingsObjMap.get("sql");
                    List<Map<String, Object>> where_conditions = (List<Map<String, Object>>) mappingsObjMap.get("where_conditions");
                    // 渲染 SQL
                    List<String> render_where_sqls = new ArrayList<>();
                    if(where_conditions != null && where_conditions.size() > 0) {
                        for (Map<String, Object> where_condition : where_conditions) {
                            String where_sql = (String) where_condition.get("sql");
                            String operator = (String) where_condition.get("operator");
                            String type = (String) where_condition.get("type");
                            List<String> param_paths = (List<String>) where_condition.get("param_paths");
                            if(param_paths != null && param_paths.size() > 0) {
                                for (String param_path : param_paths) {
                                    Object rawValue = null;
                                    try {
                                        rawValue = dataCtx.read(param_path);
                                    } catch (Exception e) {
                                        rawValue = null;
                                    }
                                    String valueStr;
                                    if (rawValue == null) {
                                        valueStr = "null";
                                    } else {
                                        // 根据类型处理
                                        switch (type.toLowerCase()) {
                                            case "number":
                                                valueStr = rawValue.toString();
                                                break;
                                            case "varchar":
                                                valueStr = "'" + rawValue.toString().replace("'", "''") + "'";
                                                break;
                                            default:
                                                valueStr = "'" + rawValue.toString().replace("'", "''") + "'";
                                                break;
                                        }
                                        // 根据 operator 处理特殊逻辑
                                        if ("like".equalsIgnoreCase(operator)) {
                                            valueStr = "'%" + rawValue.toString().replace("'", "''") + "%'";
                                        } else if ("in".equalsIgnoreCase(operator)) {
                                            // 假设是逗号分隔或者数组
                                            if (rawValue instanceof Collection) {
                                                valueStr = ((Collection<?>) rawValue).stream()
                                                        .map(v -> type.equalsIgnoreCase("number") ? v.toString() : "'" + v.toString().replace("'", "''") + "'")
                                                        .collect(Collectors.joining(", "));
                                            } else {
                                                // 字符串用逗号分隔
                                                String[] arr = rawValue.toString().split(",");
                                                valueStr = Arrays.stream(arr)
                                                        .map(v -> type.equalsIgnoreCase("number") ? v.trim() : "'" + v.trim().replace("'", "''") + "'")
                                                        .collect(Collectors.joining(", "));
                                            }
                                        }
                                    }
                                    // 替换 SQL 里的 ?
                                    where_sql = where_sql.replaceFirst("\\?", valueStr);
                                }
                            }
                            render_where_sqls.add(where_sql);
                        }
                    }
                    if(render_where_sqls.size() > 0) {
                        sqlTemplate = sqlTemplate + " WHERE " + String.join(" AND ", render_where_sqls);
                    }
                    System.out.println(sqlTemplate);
                }
            }
        }
    }

    /**
     * JsonPath 的节点 递归处理，进行添加节点操作.
     *
     * @param ctx
     * @param path
     */
    private static void setValueRecursive(com.jayway.jsonpath.DocumentContext ctx, String path) {
        if (!path.contains("[*]")) {
            int index = path.lastIndexOf(".");
            String parentPath = path.substring(0, index); // 路径到对象
            String key = path.substring(index + 1);       // 属性名
            ctx.put(parentPath, key, UUID.randomUUID().toString().replace("-", ""));
            return;
        }

        // 找到第一个 [*] 的位置
        int idx = path.indexOf("[*]");
        String prefix = path.substring(0, idx + 3); // 包含[*]
        String suffix = path.substring(idx + 3); // 剩余路径

        List<Object> arrayElements = ctx.read(prefix);

        for (int i = 0; i < arrayElements.size(); i++) {
            // 构造当前元素路径
            String currentPath = prefix.replace("[*]", "[" + i + "]") + suffix;
            // 递归处理剩余路径
            setValueRecursive(ctx, currentPath);
        }
    }

    /**
     * 格式化 SQL
     *
     * @param sqlTemplate
     * @param params
     * @param ctx
     * @return
     */
    private static List<String> renderSqls(String sqlTemplate, List<String> params, com.jayway.jsonpath.DocumentContext ctx) {
        List<String> results = new ArrayList<>();
        renderRecursive(sqlTemplate, params, ctx, results, "");
        return results;
    }

    private static void renderRecursive(String sqlTemplate, List<String> params, com.jayway.jsonpath.DocumentContext ctx,
                                        List<String> results, String basePath) {

        // 找出当前层第一个包含 [*] 的路径
        int starIndex = -1;
        String arrayPath = null;
        for (String path : params) {
            if (path.contains("[*]")) {
                starIndex = params.indexOf(path);
                arrayPath = path;
                break;
            }
        }

        if (arrayPath == null) {
            // 没有 [*]，直接替换
            String sql = sqlTemplate;
            for (String path : params) {
                Object value = safeRead(ctx, path);
                sql = sql.replaceFirst("\\?", value == null ? "null" : String.valueOf(value));
            }
            results.add(sql);
            return;
        }

        // 计算当前数组路径
        if (!basePath.isEmpty() && !arrayPath.startsWith(basePath)) {
            if (arrayPath.startsWith("$")) {
                arrayPath = basePath + arrayPath.substring(1);
            } else {
                arrayPath = basePath + "." + arrayPath;
            }
        }

        int idx = arrayPath.indexOf("[*]");
        String prefix = arrayPath.substring(0, idx + 3); // 包含 [*]
        String suffix = arrayPath.substring(idx + 3);

        List<Object> arrayElements = safeReadArray(ctx, prefix);

        for (int i = 0; i < arrayElements.size(); i++) {
            String currentBasePath = prefix.replace("[*]", "[" + i + "]");

            // 替换当前层所有参数的 [*]
            List<String> newParams = new ArrayList<>();
            for (String path : params) {
                if (path.contains("[*]") && path.startsWith(prefix.substring(0, idx))) {
                    newParams.add(path.replaceFirst("\\[\\*\\]", "[" + i + "]"));
                } else {
                    newParams.add(path);
                }
            }

            // 递归处理下一层
            renderRecursive(sqlTemplate, newParams, ctx, results, currentBasePath);
        }
    }

    private static Object safeRead(com.jayway.jsonpath.DocumentContext ctx, String path) {
        try {
            return ctx.read(path);
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> safeReadArray(com.jayway.jsonpath.DocumentContext ctx, String path) {
        try {
            Object val = ctx.read(path);
            if (val instanceof List) {
                return (List<Object>) val;
            }
            return Collections.emptyList();
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }


    @Test
    public void getFieldTest() {
        String json = "{\"name\":\"John\",\"age\":30,\"address\":{\"city\":\"New York\",\"zip\":\"10001\"},\"hobbies\":[\"reading\",\"traveling\"],\"skills\":{\"programming\":{\"java\":\"advanced\",\"python\":\"intermediate\"},\"languages\":[\"English\",\"Spanish\"]}}";

        // 解析 JSON
        Object parsedJson = JsonPath.parse(json).json();

        // 获取所有字段名称
        List<String> fieldNames = new ArrayList<>();
        getAllFieldNames(parsedJson, "", fieldNames);

        // 打印所有字段名称
        for (String fieldName : fieldNames) {
            System.out.println(fieldName);
        }
    }

    /**
     * 递归获取所有字段名称
     *
     * @param json       当前 JSON 节点
     * @param parentPath 父路径
     * @param fieldNames 存储字段名称的列表
     */
    private static void getAllFieldNames(Object json, String parentPath, List<String> fieldNames) {
        if (json instanceof LinkedHashMap) {
            LinkedHashMap<String, Object> jsonObject = (LinkedHashMap<String, Object>) json;
            for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
                String currentPath = parentPath.isEmpty() ? entry.getKey() : parentPath + "." + entry.getKey();
                fieldNames.add(currentPath);
                getAllFieldNames(entry.getValue(), currentPath, fieldNames);
            }
        } else if (json instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) json;
            for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
                String currentPath = parentPath.isEmpty() ? entry.getKey() : parentPath + "." + entry.getKey();
                fieldNames.add(currentPath);
                getAllFieldNames(entry.getValue(), currentPath, fieldNames);
            }
        } else if (json instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) json;
            if (!jsonArray.isEmpty()) {
                // 使用 * 替换数组索引
                String arrayPath = parentPath + "[*]";
                fieldNames.add(arrayPath);
                getAllFieldNames(jsonArray.get(0), arrayPath, fieldNames);
            }
        }
    }


}
