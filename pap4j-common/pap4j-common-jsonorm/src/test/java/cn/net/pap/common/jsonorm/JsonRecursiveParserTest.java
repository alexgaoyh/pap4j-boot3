package cn.net.pap.common.jsonorm;

import cn.net.pap.common.jsonorm.parser.JsonRecursiveParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class JsonRecursiveParserTest {

    @Test
    public void parseTest1() {
        // 示例1：解析对象
        String jsonObject = "{\"name\":\"Alice\",\"age\":25,\"address\":{\"city\":\"Beijing\"}}";
        List<Map<String, Object>> result1 = JsonRecursiveParser.parseToUniversalList(jsonObject);
        System.out.println("对象解析结果: " + JsonRecursiveParser.toJson(result1));

        // 示例2：解析数组
        String jsonArray = "[{\"name\":\"Alice\"},{\"name\":\"Bob\"}]";
        List<Map<String, Object>> result2 = JsonRecursiveParser.parseToUniversalList(jsonArray);
        System.out.println("数组解析结果: " + JsonRecursiveParser.toJson(result2));

        // 示例3：解析复杂嵌套结构
        String complexJson = "{\"users\":[{\"name\":\"Alice\",\"scores\":[90,85],\"meta\":{\"active\":true}}],\"info\":{\"version\":1}}";
        List<Map<String, Object>> result3 = JsonRecursiveParser.parseToUniversalList(complexJson);
        System.out.println("复杂结构解析结果: " + JsonRecursiveParser.toJson(result3));
    }

}
