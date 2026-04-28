package cn.net.pap.common.jsonorm;

import cn.net.pap.common.jsonorm.parser.JsonRecursiveParser;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertTrue;

public class JsonRecursiveParserTest {

    private static final Logger log = LoggerFactory.getLogger(JsonRecursiveParserTest.class);

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

    @Test
    public void parseTest2() throws Exception {
        Integer childrenAllLength = 0;

        String inputStr = Files.readString(Paths.get(TestResourceUtil.getFile("input.json").getAbsolutePath().toString()));
        List<Map<String, Object>> maps = JsonRecursiveParser.parseToUniversalList(inputStr);
        for(Map<String, Object> map : maps) {
            if(map.containsKey("_children") && map.get("_children") != null && map.get("_children") instanceof List) {
                List<Map<String, Object>> _childrenMapList = (List<Map<String, Object>>)map.get("_children");
                childrenAllLength = childrenAllLength + _childrenMapList.size();
                for(Map<String, Object> _childrenMap : _childrenMapList) {
                    String sourceId = _childrenMap.get("sourceId").toString();
                    String volume = _childrenMap.get("volume").toString();
                    String jpgCount = _childrenMap.get("jpgCount").toString();
                    log.info("{}, {}, {}", sourceId, volume, jpgCount);
                }
            }
        }

    }

    public static void generateEmptyJpeg(String outputPath) {
        try {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, 0xFFFFFF);
            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            ImageIO.write(image, "jpg", outputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
