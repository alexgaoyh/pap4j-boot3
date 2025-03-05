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


}
