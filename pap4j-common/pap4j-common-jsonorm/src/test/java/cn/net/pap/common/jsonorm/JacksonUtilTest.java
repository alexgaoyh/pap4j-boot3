package cn.net.pap.common.jsonorm;

import cn.net.pap.common.jsonorm.util.JacksonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JacksonUtilTest {

    /**
     * 序列化的时候，忽略特定字段
     *
     * @throws Exception
     */
    @Test
    public void filterSpecialField() throws Exception {
        JacksonDTO jacksonDTO = new JacksonDTO();
        jacksonDTO.setName("name");
        jacksonDTO.setAge("age");

        JacksonDetailDTO jsonDetailDTO = new JacksonDetailDTO();
        jsonDetailDTO.setdName("dName");
        jsonDetailDTO.setdAge("dAge");
        Map<String, Object> ext = new HashMap<>();
        ext.put("detail", jsonDetailDTO);
        jacksonDTO.setExt(ext);

        List<String> fieldsToExclude = List.of("dName");
        ObjectMapper mapper = JacksonUtil.createObjectMapper(fieldsToExclude);
        String json = mapper.writeValueAsString(jacksonDTO);
        System.out.println(json);
    }

    /**
     * 根据前后的字段, 过滤掉不需要的字段
     * @throws Exception
     */
    @Test
    public void filterJSONTest() throws Exception {
        String originalJson = "{\"name\":\"name\",\"age\":\"age\",\"ext\":{\"detail\":{\"dName\":\"dName\",\"dAge\":\"dAge\"}}}";

        String targetStructureJson = "{\"name\":null,\"ext\":{\"detail\":{\"dName\":null}}}";

        // 解析原始JSON和目标结构
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode originalNode = objectMapper.readTree(originalJson);
        JsonNode targetStructureNode = objectMapper.readTree(targetStructureJson);

        // 过滤不需要的字段
        JsonNode filteredJson = JacksonUtil.filterJson(originalNode, targetStructureNode);
        // 输出结果
        System.out.println("Filtered JSON: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(filteredJson));
    }

    public class JacksonDTO implements Serializable {

        private String name;

        private String age;

        private Map<String, Object> ext;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAge() {
            return age;
        }

        public void setAge(String age) {
            this.age = age;
        }

        public Map<String, Object> getExt() {
            return ext;
        }

        public void setExt(Map<String, Object> ext) {
            this.ext = ext;
        }
    }

    public static class JacksonDetailDTO implements Serializable {

        private String dName;

        private String dAge;

        public String getdName() {
            return dName;
        }

        public void setdName(String dName) {
            this.dName = dName;
        }

        public String getdAge() {
            return dAge;
        }

        public void setdAge(String dAge) {
            this.dAge = dAge;
        }
    }
}
