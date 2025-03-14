package cn.net.pap.common.jsonorm;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;

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



}
