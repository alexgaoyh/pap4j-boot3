package cn.net.pap.common.jsonorm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.net.pap.common.jsonorm.dto.JsonDTO;
import cn.net.pap.common.jsonorm.dto.JsonDTO2;
import cn.net.pap.common.jsonorm.parser.OptimizedJsonParser;
import cn.net.pap.common.jsonorm.util.JsonORMUtil;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class OptimizedJsonParserTest {
    private static final Logger log = LoggerFactory.getLogger(OptimizedJsonParserTest.class);

    public String generateBigJsonFile() throws Exception {
        File file = Files.createTempFile("generateBigJsonFile", ".json").toFile();
        JsonFactory factory = new JsonFactory();
        long targetRecords = 10000;
        try (JsonGenerator generator = factory.createGenerator(new FileWriter(file))) {
            generator.writeStartObject();
            generator.writeFieldName("chars");
            generator.writeStartArray();
            for (long i = 0; i < targetRecords; i++) {
                generator.writeStartObject();
                generator.writeNumberField("distance", (int) (Math.random() * 100));
                generator.writeFieldName("coords");
                generator.writeStartArray();
                generator.writeNumber(Math.random() * 1000.0); // 模拟坐标 X
                generator.writeNumber(Math.random() * 1000.0); // 模拟坐标 Y
                generator.writeEndArray();
                generator.writeFieldName("box");
                generator.writeStartArray();
                generator.writeNumber(Math.random() * 100.0);
                generator.writeNumber(Math.random() * 100.0);
                generator.writeNumber(Math.random() * 100.0);
                generator.writeNumber(Math.random() * 100.0);
                generator.writeEndArray();
                generator.writeStringField("text", "MockText_" + i);
                generator.writeEndObject();

                if (i > 0 && i % 100000 == 0) {
                    log.info("{}", "已生成: " + i + " 条 CharDTO 记录...");
                }
            }
            generator.writeEndArray();
            generator.writeEndObject();
        }

        return file.getAbsolutePath();
    }

    @Test
    public void optimizedTest1() throws Exception {
        File file = new File(generateBigJsonFile());
        try {
            String json = JsonORMUtil.readFileToString(file);
            // 这里就是初始化一下
            JsonDTO jsonDTO2 = OptimizedJsonParser.parseWithOptimization(json, JsonDTO.class);

            long l = System.currentTimeMillis();
            JsonDTO jsonDTO = OptimizedJsonParser.parseWithOptimization(json, JsonDTO.class);
            log.info("{}", System.currentTimeMillis() - l);

            // 打印对象内部结构（对象头、字段对齐等）
            log.info("ClassLayout:");
            log.info("{}", ClassLayout.parseInstance(jsonDTO).toPrintable());

            // 打印对象图总大小（包括引用的对象）
            log.info("GraphLayout:");
            log.info("{}", GraphLayout.parseInstance(jsonDTO).toFootprint());
            log.info("{}", "Total size: " + GraphLayout.parseInstance(jsonDTO).totalSize() + " bytes");
        } finally {
            if (file.exists()) {
                file.delete();
            }
        }
    }


    @Test
    public void noOptimizedTest1() throws Exception {
        File file = new File(generateBigJsonFile());
        try {
            String json = JsonORMUtil.readFileToString(file);
            ObjectMapper OBJECT_MAPPER = new ObjectMapper();
            OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            JsonDTO jsonDTO2 = OBJECT_MAPPER.readValue(json, JsonDTO.class);

            long l = System.currentTimeMillis();
            JsonDTO jsonDTO = OBJECT_MAPPER.readValue(json, JsonDTO.class);
            log.info("{}", System.currentTimeMillis() - l);

            // 打印对象内部结构（对象头、字段对齐等）
            log.info("ClassLayout:");
            log.info("{}", ClassLayout.parseInstance(jsonDTO).toPrintable());

            // 打印对象图总大小（包括引用的对象）
            log.info("GraphLayout:");
            log.info("{}", GraphLayout.parseInstance(jsonDTO).toFootprint());
            log.info("{}", "Total size: " + GraphLayout.parseInstance(jsonDTO).totalSize() + " bytes");
        } finally {
            if (file.exists()) {
                file.delete();
            }
        }
    }

    @Test
    public void jsonToListMapTest() throws Exception {
        File file = null;
        try {
            file = TestResourceUtil.getFile("input.json");
            String json = JsonORMUtil.readFileToString(file);
            ObjectMapper objectMapper = new ObjectMapper();
            List<Map<String, Object>> result = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            log.info("{}", result.size());
        } finally {
            if (file != null && file.exists()) {
                file.delete();
            }
        }
    }

    @Test
    public void benchmarkSerializationTest0() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
    }

    @Test
    public void benchmarkSerializationTest1() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<JsonDTO.CharDTO> charDTOS = new ArrayList<>();
        for(int idx = 0; idx < 100; idx++) {
            JsonDTO.CharDTO charDTO = new JsonDTO.CharDTO();
            charDTO.setText(idx + "");
            charDTO.setBox(List.of(Double.parseDouble(idx + ""), Double.parseDouble(idx + ""), Double.parseDouble(idx + ""), Double.parseDouble(idx + "")));
            charDTO.setCoords(List.of(Double.parseDouble(idx + ""), Double.parseDouble(idx + ""), Double.parseDouble(idx + ""), Double.parseDouble(idx + "")));
            charDTO.setDistance(idx);
            charDTOS.add(charDTO);
        }
        JsonDTO jsonDTO2 = new JsonDTO();
        jsonDTO2.setChars(charDTOS);

        long start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            mapper.writeValueAsString(jsonDTO2);
        }
        long end = System.nanoTime();

        log.info("{}", "Time per serialization: " +
                (end - start) / 10000 / 1_000 + "μs");
    }

    @Test
    public void benchmarkSerializationTest2() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
//        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
//        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//        mapper.disable(SerializationFeature.INDENT_OUTPUT);
//        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false);
        ObjectWriter writer = mapper.writerFor(JsonDTO.class);
        List<JsonDTO.CharDTO> charDTOS = new ArrayList<>();
        for(int idx = 0; idx < 100; idx++) {
            JsonDTO.CharDTO charDTO = new JsonDTO.CharDTO();
            charDTO.setText(idx + "");
            charDTO.setBox(List.of(Double.parseDouble(idx + ""), Double.parseDouble(idx + ""), Double.parseDouble(idx + ""), Double.parseDouble(idx + "")));
            charDTO.setCoords(List.of(Double.parseDouble(idx + ""), Double.parseDouble(idx + ""), Double.parseDouble(idx + ""), Double.parseDouble(idx + "")));
            charDTO.setDistance(idx);
            charDTOS.add(charDTO);
        }
        JsonDTO jsonDTO2 = new JsonDTO();
        jsonDTO2.setChars(charDTOS);

        long start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            writer.writeValueAsString(jsonDTO2);
        }
        long end = System.nanoTime();

        log.info("{}", "Time per serialization: " +
                (end - start) / 10000 / 1_000 + "μs");
    }

    @Test
    public void benchmarkSerializationTest3() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
//        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
//        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//        mapper.disable(SerializationFeature.INDENT_OUTPUT);
//        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false);
        ObjectWriter writer = mapper.writerFor(JsonDTO2.class);
        List<JsonDTO2.CharDTO> charDTOS = new ArrayList<>();
        for(int idx = 0; idx < 100; idx++) {
            JsonDTO2.CharDTO charDTO = new JsonDTO2.CharDTO();
            charDTO.setText(idx + "");
            // 在 Jackson 中，double[] 的序列化性能明显优于 List<Double>。
            charDTO.setBox(new Double[]{Double.parseDouble(idx + ""), Double.parseDouble(idx + ""), Double.parseDouble(idx + ""), Double.parseDouble(idx + "")});
            charDTO.setCoords(new Double[]{Double.parseDouble(idx + ""), Double.parseDouble(idx + ""), Double.parseDouble(idx + ""), Double.parseDouble(idx + "")});
            charDTO.setDistance(idx);
            charDTOS.add(charDTO);
        }
        JsonDTO2 jsonDTO2 = new JsonDTO2();
        jsonDTO2.setChars(charDTOS);

        long start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            writer.writeValueAsString(jsonDTO2);
        }
        long end = System.nanoTime();

        log.info("{}", "Time per serialization: " +
                (end - start) / 10000 / 1_000 + "μs");
    }

    @Test
    public void benchmarkSerializationTest4() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new AfterburnerModule());
        ObjectWriter writer = mapper.writerFor(JsonDTO.class);
        List<JsonDTO.CharDTO> charDTOS = new ArrayList<>();
        for(int idx = 0; idx < 100; idx++) {
            JsonDTO.CharDTO charDTO = new JsonDTO.CharDTO();
            charDTO.setText(idx + "");
            charDTO.setBox(List.of(Double.parseDouble(idx + ""), Double.parseDouble(idx + ""), Double.parseDouble(idx + ""), Double.parseDouble(idx + "")));
            charDTO.setCoords(List.of(Double.parseDouble(idx + ""), Double.parseDouble(idx + ""), Double.parseDouble(idx + ""), Double.parseDouble(idx + "")));
            charDTO.setDistance(idx);
            charDTOS.add(charDTO);
        }
        JsonDTO jsonDTO2 = new JsonDTO();
        jsonDTO2.setChars(charDTOS);

        long start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            writer.writeValueAsString(jsonDTO2);
        }
        long end = System.nanoTime();

        log.info("{}", "Time per serialization: " +
                (end - start) / 10000 / 1_000 + "μs");
    }

    @Test
    public void benchmarkSerializationTest5() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // 创建一个复杂的 JsonArray 对象
        ArrayNode jsonArray = new JsonNodeFactory(false).arrayNode();
        for(int idx = 0; idx < 100; idx++) {
            ObjectNode jsonObject = new JsonNodeFactory(false).objectNode();
            jsonObject.put("text", idx + "");
            jsonObject.set("box", new JsonNodeFactory(false).arrayNode()
                    .add(Double.parseDouble(idx + ""))
                    .add(Double.parseDouble(idx + ""))
                    .add(Double.parseDouble(idx + ""))
                    .add(Double.parseDouble(idx + ""))
            );
            jsonObject.set("coords", new JsonNodeFactory(false).arrayNode()
                    .add(Double.parseDouble(idx + ""))
                    .add(Double.parseDouble(idx + ""))
                    .add(Double.parseDouble(idx + ""))
                    .add(Double.parseDouble(idx + ""))
            );
            jsonObject.put("distance", idx);
            jsonArray.add(jsonObject);
        }

        long start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            mapper.writeValueAsString(jsonArray);
        }
        long end = System.nanoTime();

        log.info("{}", "Time per serialization: " +
                (end - start) / 10000 / 1_000 + "μs");
    }

    @Test
    public void benchmarkSerializationTest6() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new AfterburnerModule());

        // 创建一个复杂的 JsonArray 对象
        ArrayNode jsonArray = new JsonNodeFactory(false).arrayNode();
        for(int idx = 0; idx < 100; idx++) {
            ObjectNode jsonObject = new JsonNodeFactory(false).objectNode();
            jsonObject.put("text", idx + "");
            jsonObject.set("box", new JsonNodeFactory(false).arrayNode()
                    .add(Double.parseDouble(idx + ""))
                    .add(Double.parseDouble(idx + ""))
                    .add(Double.parseDouble(idx + ""))
                    .add(Double.parseDouble(idx + ""))
            );
            jsonObject.set("coords", new JsonNodeFactory(false).arrayNode()
                    .add(Double.parseDouble(idx + ""))
                    .add(Double.parseDouble(idx + ""))
                    .add(Double.parseDouble(idx + ""))
                    .add(Double.parseDouble(idx + ""))
            );
            jsonObject.put("distance", idx);
            jsonArray.add(jsonObject);
        }

        long start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            mapper.writeValueAsString(jsonArray);
        }
        long end = System.nanoTime();

        log.info("{}", "Time per serialization: " +
                (end - start) / 10000 / 1_000 + "μs");
    }

}
