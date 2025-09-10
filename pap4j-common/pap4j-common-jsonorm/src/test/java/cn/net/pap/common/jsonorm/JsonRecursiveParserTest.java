package cn.net.pap.common.jsonorm;

import cn.net.pap.common.jsonorm.parser.JsonRecursiveParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertTrue;

public class JsonRecursiveParserTest {

    @Test
    public void parseTest1() throws Exception {
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

        // json schema normalize

        String schema1 = """
                {
                  "type": "object",
                  "properties": {
                    "name": {"type": "string"},
                    "age": {"type": "integer"},
                    "address": {
                      "type": "object",
                      "properties": {
                        "city": {"type": "string"},
                        "postalCode": {"type": "string", "default": "100000"}
                      },
                      "required": ["city"]
                    },
                    "active": {"type": "boolean", "default": false}
                  }
                }
                """;
        List<Map<String, Object>> result1Normalized = JsonRecursiveParser.normalize(result1, schema1);

        String schema2 = """
                {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": {"type": "string"},
                      "age": {"type": "integer", "default": 18},
                      "email": {"type": "string", "format": "email"}
                    }
                  }
                }
                """;
        List<Map<String, Object>> result2Normalized = JsonRecursiveParser.normalize(result2, schema2);

        String schema3 = """
                {
                  "type": "object",
                  "properties": {
                    "users": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "name": {"type": "string"},
                          "scores": {
                            "type": "array",
                            "items": {"type": "integer"}
                          },
                          "meta": {
                            "type": "object",
                            "properties": {
                              "active": {"type": "boolean"},
                              "role": {"type": "string", "default": "user"}
                            }
                          }
                        }
                      }
                    },
                    "info": {
                      "type": "object",
                      "properties": {
                        "version": {"type": "integer"},
                        "timestamp": {"type": "string", "default": "2023-01-01"}
                      }
                    }
                  }
                }
                """;
        List<Map<String, Object>> result3Normalized = JsonRecursiveParser.normalize(result3, schema3);

        System.out.println("");

    }

    // @Test
    public void parseTest2() throws Exception {
        Integer childrenAllLength = 0;
        Map<String, Integer> checkNumberMap = new TreeMap<String, Integer>();

        String inputStr = Files.readString(Paths.get("C:\\Users\\86181\\Desktop\\input.json"));
        List<Map<String, Object>> maps = JsonRecursiveParser.parseToUniversalList(inputStr);
        for(Map<String, Object> map : maps) {
            assertTrue(map.size() == 21);
            if(map.containsKey("_children") && map.get("_children") != null && map.get("_children") instanceof List) {
                List<Map<String, Object>> _childrenList = (List<Map<String, Object>>)map.get("_children");
                childrenAllLength = childrenAllLength + _childrenList.size();
            }
        }

        assertTrue(childrenAllLength == 1125);
    }


}
