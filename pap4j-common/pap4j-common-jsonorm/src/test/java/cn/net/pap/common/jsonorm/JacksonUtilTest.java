package cn.net.pap.common.jsonorm;

import java.io.ByteArrayOutputStream;

import cn.net.pap.common.jsonorm.dto.CategoryDTO;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import cn.net.pap.common.jsonorm.dto.HeadDTO;
import cn.net.pap.common.jsonorm.dto.JsonDTO;
import cn.net.pap.common.jsonorm.util.JacksonUtil;
import cn.net.pap.common.jsonorm.util.JsonORMUtil;
import com.esotericsoftware.kryo.Kryo;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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

    @Test
    public void jsonToListMapTest() throws Exception {
        String json = JsonORMUtil.readFileToString(TestResourceUtil.getFile("input.json"));
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

        ObjectMapper objectMapper3 = new ObjectMapper();
        objectMapper3.setPropertyNamingStrategy(new PropertyNamingStrategy() {
            @Override
            public String nameForGetterMethod(MapperConfig<?> config,
                                              AnnotatedMethod method,
                                              String defaultName) {
                if(defaultName.equals("remark")) {
                    return "备注备注";
                } else if (defaultName.equals("language")) {
                    return "语种语种";
                } else if (defaultName.equals("id")) {
                    return "序号序号";
                } else if (defaultName.equals("_children")) {
                    return "明细";
                } else {
                    return super.nameForGetterMethod(config, method, defaultName);
                }
            }
        });
        String s = objectMapper3.writeValueAsString(result2);
        System.out.println(s);
    }

    // @Test
    public void extractKeysTest() throws Exception {
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";

        Set<String> keys = new HashSet<>();
        keys.add("target_key");

        Map<String, String> extracted = JacksonUtil.extractKeys(desktop + File.separator + "large.json", keys);
        extracted.forEach((k, v) -> System.out.println(k + " : " + v));

    }

    @Test
    public void jsonParserTest1() throws Exception {
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";
        String filePath = desktop + File.separator + "json.json";
        if(new File(filePath).exists()) {
            JsonFactory factory = new JsonFactory();
            try (JsonParser parser = factory.createParser(new File(filePath))) {
                JsonToken token;
                while ((token = parser.nextToken()) != null) {
                    System.out.println("Token: " + token);
                    System.out.println("Current name: " + parser.getCurrentName());
                    System.out.println("Current value: " + parser.getText());
                    System.out.println("---");
                }
            }
        }
    }

    @Test
    public void jsonParserTest2() throws Exception {
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";
        String filePath = desktop + File.separator + "array.json";
        if(new File(filePath).exists()) {
            List<JsonNode> jsonNodes = JacksonUtil.readJsonArrayRange(filePath, 0, 2);
            jsonNodes.stream().forEach(e -> System.out.println(e.toPrettyString()));
        }
    }

    @Test
    public void jsonParserTest3() throws Exception {
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";
        String filePath = desktop + File.separator + "json.json";
        if(new File(filePath).exists()) {
            List<JsonNode> jsonNodes = JacksonUtil.readJsonArrayRange(filePath, "result", 0, 2);
            jsonNodes.stream().forEach(e -> System.out.println(e.toPrettyString()));
        }
    }

    @Test
    public void jsonParserTest4() throws Exception {
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";
        String filePath = desktop + File.separator + "json.json";
        if(new File(filePath).exists()) {
            ObjectNode objectNode = JacksonUtil.readObjectWithArraySlice(filePath, "result", 0, 2);
            System.out.println(objectNode.toPrettyString());
        }
    }

    @Test
    public void sumJsonArrayFieldTest() throws Exception {
        Path tempFile = Files.createTempFile("test_data_", ".json");
        try {
            String jsonContent = """
                {
                  "items": [
                    {"name": "item1", "price": 10.5},
                    {"name": "item2", "price": 20},
                    {"name": "item3", "price": 15.75}
                  ]
                }
                """;
            Files.writeString(tempFile, jsonContent);
            // 调用方法并验证结果
            Number result = JacksonUtil.sumJsonArrayField(tempFile.toString(), "items", "price");
            assertEquals(46.25, result.doubleValue(), 0.001);
            assertInstanceOf(Double.class, result);
        } finally {
            // 确保删除临时文件
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void databaseStorageComparisonTest() throws Exception {

        final int WARM_UP = 5;
        final int LOOP = 1000;

        // ---------- 构造测试数据 ----------
        List<JsonDTO.CharDTO> charDTOS = new ArrayList<>();
        for (int idx = 0; idx < 100; idx++) {
            JsonDTO.CharDTO charDTO = new JsonDTO.CharDTO();
            charDTO.setText("Text-" + idx);
            charDTO.setBox(Arrays.asList(idx * 1.0, idx * 2.0, idx * 3.0, idx * 4.0));
            charDTO.setCoords(Arrays.asList(idx * 0.1, idx * 0.2, idx * 0.3, idx * 0.4));
            charDTO.setDistance(idx % 1000);
            charDTOS.add(charDTO);
        }
        JsonDTO jsonDTO = new JsonDTO();
        jsonDTO.setChars(charDTOS);

        // ---------- JSON ----------
        ObjectMapper mapper = new ObjectMapper();
        byte[] jsonBytes = null;

        // warm-up
        for (int i = 0; i < WARM_UP; i++) {
            jsonBytes = mapper.writeValueAsBytes(jsonDTO);
            mapper.readValue(jsonBytes, JsonDTO.class);
        }

        long jsonSerializeNs = 0;
        long jsonDeserializeNs = 0;

        for (int i = 0; i < LOOP; i++) {
            long t1 = System.nanoTime();
            jsonBytes = mapper.writeValueAsBytes(jsonDTO);
            long t2 = System.nanoTime();

            long t3 = System.nanoTime();
            mapper.readValue(jsonBytes, JsonDTO.class);
            long t4 = System.nanoTime();

            jsonSerializeNs += (t2 - t1);
            jsonDeserializeNs += (t4 - t3);
        }

        // ---------- Kryo ----------
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setReferences(true);
        kryo.register(JsonDTO.class);

        byte[] kryoBytes = null;

        // warm-up
        for (int i = 0; i < WARM_UP; i++) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Output output = new Output(baos);
            kryo.writeObject(output, jsonDTO);
            output.close();
            kryoBytes = baos.toByteArray();

            Input input = new Input(kryoBytes);
            kryo.readObject(input, JsonDTO.class);
            input.close();
        }

        long kryoSerializeNs = 0;
        long kryoDeserializeNs = 0;

        for (int i = 0; i < LOOP; i++) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Output output = new Output(baos);

            long d1 = System.nanoTime();
            kryo.writeObject(output, jsonDTO);
            output.close();
            kryoBytes = baos.toByteArray();
            long d2 = System.nanoTime();

            Input input = new Input(kryoBytes);
            long d3 = System.nanoTime();
            kryo.readObject(input, JsonDTO.class);
            input.close();
            long d4 = System.nanoTime();

            kryoSerializeNs += (d2 - d1);
            kryoDeserializeNs += (d4 - d3);
        }

        // ---------- 输出结果 ----------
        System.out.println("====== Result (avg over " + LOOP + " runs) ======");

        System.out.println("JSON bytes length = " + jsonBytes.length);
        System.out.printf("JSON serialize avg = %.3f ms%n",
                jsonSerializeNs / 1_000_000.0 / LOOP);
        System.out.printf("JSON deserialize avg = %.3f ms%n",
                jsonDeserializeNs / 1_000_000.0 / LOOP);

        System.out.println();

        System.out.println("Kryo bytes length = " + kryoBytes.length);
        System.out.printf("Kryo serialize avg = %.3f ms%n",
                kryoSerializeNs / 1_000_000.0 / LOOP);
        System.out.printf("Kryo deserialize avg = %.3f ms%n",
                kryoDeserializeNs / 1_000_000.0 / LOOP);
    }

    @Test
    public void categoryDTOTest() throws Exception {
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";
        String inputJSON = desktop + File.separator + "input.json";
        if(new File(inputJSON).exists()) {
            ObjectMapper objectMapper = new ObjectMapper();
            CategoryDTO root = objectMapper.readValue(Files.readString(Paths.get(inputJSON)), CategoryDTO.class);
            System.out.println(root);
        }
    }

    /**
     * 深度优先
     * @param root
     * @return
     */
    public static List<CategoryDTO> flattenToDepthFirstList(CategoryDTO root) {
        List<CategoryDTO> flattenedList = new ArrayList<>();
        if (root != null) {
            depthFirstTraverse(root, flattenedList);
        }
        return flattenedList;
    }

    private static void depthFirstTraverse(CategoryDTO node, List<CategoryDTO> result) {
        if (node == null) {
            return;
        }
        result.add(node);

        if (node.getChildren() != null) {
            for (CategoryDTO child : node.getChildren()) {
                depthFirstTraverse(child, result);
            }
        }
    }


}
