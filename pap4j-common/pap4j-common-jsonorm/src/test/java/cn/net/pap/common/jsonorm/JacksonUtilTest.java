package cn.net.pap.common.jsonorm;

import cn.net.pap.common.jsonorm.dto.HeadDTO;
import cn.net.pap.common.jsonorm.util.JacksonUtil;
import cn.net.pap.common.jsonorm.util.JsonORMUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import org.junit.jupiter.api.Test;

import java.io.File;
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
        ObjectMapper objectMapper = new ObjectMapper();

        String originalJson = "{\"name\":\"name\",\"age\":\"age\",\"ext\":{\"detail\":{\"dName\":\"dName\",\"dAge\":\"dAge\",\"dList\":[{\"d1\":\"d1\",\"d2\":\"d2\"},{\"d1\":\"d3\",\"d2\":\"d4\"}]}},\"list\":[{\"a\":\"1\",\"b\":\"2\",\"ext\":{\"c\":\"12\",\"d\":\"1212\"}},{\"a\":\"3\",\"b\":\"4\",\"ext\":{\"c\":\"34\",\"d\":\"3434\"}}]}";
        String targetStructureJson = "{\"name\":null,\"ext\":{\"detail\":{\"dName\":null,\"dList\":[{\"d1\":null}]}},\"list\":[{\"a\":null,\"ext\":{\"c\":null}}]}";
        JsonNode originalNode = objectMapper.readTree(originalJson);
        JsonNode targetStructureNode = objectMapper.readTree(targetStructureJson);
        JsonNode filteredJson = JacksonUtil.filterJson(originalNode, targetStructureNode);
        System.out.println("Filtered JSON: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(filteredJson));

        System.out.println("-------------------------------------------------------------------------");

        String originalJson2 = "{\"name\":\"name\",\"age\":\"age\",\"ext\":{\"detail\":{\"dName\":\"dName\",\"dAge\":\"dAge\"}},\"list\":[{\"a\": \"1\",\"b\": \"2\"}, {\"a\": \"3\",\"b\": \"4\"}]}";
        String targetStructureJson2 = "{\"name\":null,\"ext\":{\"detail\":{\"dName\":null}}}";
        JsonNode originalNode2 = objectMapper.readTree(originalJson2);
        JsonNode targetStructureNode2 = objectMapper.readTree(targetStructureJson2);
        JsonNode filteredJson2 = JacksonUtil.filterJson(originalNode2, targetStructureNode2);
        System.out.println("Filtered JSON2: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(filteredJson2));
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

    // @Test
    public void jsonToListMapTest() throws Exception {
        String json = JsonORMUtil.readFileToString(new File("C:\\Users\\86181\\Desktop\\input.json"));
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<HeadDTO> result = objectMapper.readValue(json, new TypeReference<List<HeadDTO>>() {});
        System.out.println(result.size());

        ObjectMapper objectMapper2 = new ObjectMapper();
        // 自定义命名策略
        objectMapper2.setPropertyNamingStrategy(new PropertyNamingStrategy() {
            @Override
            public String nameForSetterMethod(MapperConfig<?> config,
                                              AnnotatedMethod method,
                                              String defaultName) {
                // todo 这里在反序列的时候，增加自定义的命令策略，比如如下是将json中定义的 ‘备注’ 转换为 'remark'；  json中定义的 '语种' 转换为 ’language‘ .
                if(defaultName.equals("remark")) {
                    return "备注";
                } else if (defaultName.equals("language")) {
                    return "语种";
                } else {
                    return super.nameForSetterMethod(config, method, defaultName);
                }
            }
        });
        objectMapper2.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<HeadDTO> result2 = objectMapper2.readValue(json, new TypeReference<List<HeadDTO>>() {});
        System.out.println(result2.size());

    }


}
