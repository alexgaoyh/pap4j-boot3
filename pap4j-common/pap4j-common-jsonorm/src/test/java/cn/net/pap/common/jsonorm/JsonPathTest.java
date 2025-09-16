package cn.net.pap.common.jsonorm;

import cn.net.pap.common.jsonorm.util.JsonORMUtil;
import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

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
        for(int idx = 0; idx < Integer.parseInt(ctx.read("$.length()").toString()); idx++) {
            Map<String, Object> itemMap = (Map<String, Object>)ctx.read("$[" + idx + "]");
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
