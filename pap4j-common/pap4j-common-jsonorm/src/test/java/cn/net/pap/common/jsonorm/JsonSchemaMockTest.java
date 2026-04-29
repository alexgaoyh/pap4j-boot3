package cn.net.pap.common.jsonorm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * 根据 json schema 生成 json 数据，应用场景可以是生成一个大 json，用于后面的解析试验
 */
public class JsonSchemaMockTest {
    private static final Logger log = LoggerFactory.getLogger(JsonSchemaMockTest.class);

    // @Test
    public void geneTest1() throws Exception {
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";
        String filePath = desktop + File.separator + "schema.json";
        File file = new File(filePath);
        if (file.exists()) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode schema = mapper.readTree(new File(filePath));
            // may OOM
            JsonNode mock = mock(schema);
            // log.info(mapper.writeValueAsString(mock));
            mapper.writeValue(new File(desktop + File.separator + "data.json"), mock);

        }
    }

    // @Test
    public void geneTestStreaming() throws Exception {
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";
        File schemaFile = new File(desktop + File.separator + "schema.json");
        if (!schemaFile.exists()) {
            return;
        }
        JsonNode schema = mapper.readTree(schemaFile);
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("data_", ".json");
        try {
            FileOutputStream out = new FileOutputStream(tempFile.toFile());
            JsonFactory factory = new JsonFactory();
            JsonGenerator gen = factory.createGenerator(out);
            // 启动递归生成
            writeMockJson(gen, schema);
            gen.flush();
            gen.close();
            out.close();
            log.info("生成完成：" + tempFile.toAbsolutePath());
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Random random = new Random();

    public static JsonNode mock(JsonNode schema) {
        String type = schema.has("type") ? schema.get("type").asText() : null;

        if ("object".equals(type)) {
            return mockObject(schema);
        }
        if ("array".equals(type)) {
            return mockArray(schema);
        }
        if ("string".equals(type)) {
            return new TextNode(randomString(5));
        }
        if ("integer".equals(type)) {
            return new IntNode(random.nextInt(100));
        }
        if ("number".equals(type)) {
            return new DoubleNode(random.nextDouble() * 100);
        }
        if ("boolean".equals(type)) {
            return BooleanNode.valueOf(random.nextBoolean());
        }
        return NullNode.getInstance();
    }


    /**
     * 根据 schema，直接写 JSON（不生成任何中间 JsonNode）
     */
    private static void writeMockJson(JsonGenerator gen, JsonNode schema) throws Exception {
        String type = schema.has("type") ? schema.get("type").asText() : null;

        switch (type) {
            case "object":
                writeObject(gen, schema);
                break;
            case "array":
                writeArray(gen, schema);
                break;
            case "string":
                gen.writeString(randomString(5));
                break;
            case "integer":
                gen.writeNumber(random.nextInt(100));
                break;
            case "number":
                gen.writeNumber(random.nextDouble() * 100);
                break;
            case "boolean":
                gen.writeBoolean(random.nextBoolean());
                break;
            default:
                gen.writeNull();
                break;
        }
    }

    private static void writeObject(JsonGenerator gen, JsonNode schema) throws Exception {
        gen.writeStartObject();

        JsonNode props = schema.path("properties");
        if (props.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = props.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                gen.writeFieldName(e.getKey());
                writeMockJson(gen, e.getValue());
            }
        }

        gen.writeEndObject();
    }

    private static void writeArray(JsonGenerator gen, JsonNode schema) throws Exception {
        gen.writeStartArray();

        JsonNode itemSchema = schema.path("items");
        int size = 1 + random.nextInt(500);

        for (int i = 0; i < size; i++) {
            writeMockJson(gen, itemSchema);
        }

        gen.writeEndArray();
    }

    private static String randomString(int len) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static ObjectNode mockObject(JsonNode schema) {
        ObjectNode obj = mapper.createObjectNode();
        JsonNode props = schema.path("properties");

        if (props.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = props.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                String fieldName = e.getKey();
                JsonNode fieldSchema = e.getValue();
                obj.set(fieldName, mock(fieldSchema));
            }
        }
        return obj;
    }

    private static ArrayNode mockArray(JsonNode schema) {
        ArrayNode arr = mapper.createArrayNode();

        JsonNode itemSchema = schema.path("items");
        int size = 1 + random.nextInt(500);

        for (int i = 0; i < size; i++) {
            arr.add(mock(itemSchema));
        }

        return arr;
    }

}
