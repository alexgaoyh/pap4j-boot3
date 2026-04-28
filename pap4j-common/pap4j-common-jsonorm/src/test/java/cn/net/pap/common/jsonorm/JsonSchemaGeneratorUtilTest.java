package cn.net.pap.common.jsonorm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.net.pap.common.jsonorm.util.JacksonUtil;
import cn.net.pap.common.jsonorm.util.JsonSchemaGeneratorUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

public class JsonSchemaGeneratorUtilTest {
    private static final Logger log = LoggerFactory.getLogger(JsonSchemaGeneratorUtilTest.class);

    /**
     * 效果： 完成一个 json 结构的过滤操作
     *
     * 1、入参是一个任意结构的 JSON 字符串
     * 2、将其转换为 json schema 结构
     * 3、将 json schema 结构转换为 JSON 字符串， 这个字符串与第一步的 JSON 字符串相比，减少了重复的数值部分，保留了真实的数据结构，做到了结构精简；
     * 4、将 第三步 精简后的 JSON 字符串转平铺，从而获得到 JSON 中所有使用的 key ;
     * 5、尝试删除一些 JSON 数据结构中所使用的 key；
     * 6、最终再将 删除部分 key 后的 平铺JSON 再转为 JSON结构；
     * 7、将原始的真实数据与第六步获得的精简JSON做统一处理，从而实现数据脱敏；
     */
    @Test
    public void jsonSchemaInFieldFilter() {
        String json = "{\"name\":\"name\",\"age\":\"age\",\"ext\":{\"detail\":{\"dName\":\"dName\",\"dAge\":\"dAge\",\"dList\":[{\"d1\":\"d1\",\"d2\":\"d2\"},{\"d1\":\"d3\",\"d2\":\"d4\"}]}},\"list\":[{\"a\":\"1\",\"b\":\"2\",\"ext\":{\"c\":\"12\",\"d\":\"1212\"}},{\"a\":\"3\",\"b\":\"4\",\"ext\":{\"c\":\"34\",\"d\":\"3434\"}}]}";

        try {
            String jsonSchema = JsonSchemaGeneratorUtil.convertJsonToJsonSchema(json);
            log.info("{}", jsonSchema);

            log.info("-----------------------------------------------------");

            String reJson = JsonSchemaGeneratorUtil.generateJsonFromSchema(jsonSchema);
            log.info("{}", reJson);

            log.info("-----------------------------------------------------");

            List<String> fieldKeys = JsonSchemaGeneratorUtil.flattenJson(reJson);
            log.info("{}", fieldKeys);

            log.info("-----------------------------------------------------");

            fieldKeys.remove(5);
            fieldKeys.remove(7);

            log.info("-----------------------------------------------------");

            log.info("{}", fieldKeys);

            log.info("-----------------------------------------------------");

            String unflattenJson = JsonSchemaGeneratorUtil.unflattenJson(fieldKeys);
            log.info("{}", unflattenJson);

            log.info("-----------------------------------------------------");

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode originalNode = objectMapper.readTree(json);
            JsonNode targetStructureNode = objectMapper.readTree(unflattenJson);
            JsonNode filteredJson = JacksonUtil.filterJson(originalNode, targetStructureNode);
            log.info("{}", "Filtered JSON2: " + objectMapper.writeValueAsString(filteredJson));


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    @DisplayName("根据json-schema生成mock数据")
    public void geneMockDataTest1() throws IOException {
        String jsonSchema = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": {
                    "price": {
                      "type": "number",
                      "default": 10.5,
                      "exclusiveMinimum": 0
                    },
                    "tags": {
                      "type": "array",
                      "default": ["general"],
                      "items": { "type": "string" }
                    }
                  }
                }
                """;
        String reJson = JsonSchemaGeneratorUtil.generateJsonFromSchema(jsonSchema);
        log.info("{}", reJson);
    }

}
