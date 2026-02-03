package cn.net.pap.common.jsonorm;

import cn.net.pap.common.jsonorm.dto.HeadDTO;
import cn.net.pap.common.jsonorm.dto.MappingORMDTO;
import cn.net.pap.common.jsonorm.util.JsonSchemaFormatValidation;
import cn.net.pap.common.jsonorm.util.JsonSchemaUtil;
import cn.net.pap.common.jsonorm.util.dto.SchemaDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import jakarta.validation.constraints.Pattern;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonSchemaTest {

    @Test
    public void test1() throws Exception {
        System.out.println(JsonSchemaUtil.toSchema(MappingORMDTO.class));
    }

    @Test
    public void test2() throws Exception {
        System.out.println(JsonSchemaUtil.toSchema(SchemaDTO.class));
    }

    /**
     * json schema check json
     */
    // @Test
    public void test3() {
        try {
            // 读取Schema文件
            Path schemaPath = Paths.get("C:\\Users\\86181\\Desktop\\schema.json");
            if (!Files.exists(schemaPath)) {
                throw new FileNotFoundException("Schema文件不存在: " + schemaPath);
            }
            if (!Files.isReadable(schemaPath)) {
                throw new SecurityException("没有读取Schema文件的权限: " + schemaPath);
            }

            // 读取Data文件
            Path dataPath = Paths.get("C:\\Users\\86181\\Desktop\\data.json");
            if (!Files.exists(dataPath)) {
                throw new FileNotFoundException("Data文件不存在: " + dataPath);
            }
            if (!Files.isReadable(dataPath)) {
                throw new SecurityException("没有读取Data文件的权限: " + dataPath);
            }

            String schemaStr = Files.readString(schemaPath);
            String dataStr = Files.readString(dataPath);

            // 解析JSON
            JSONObject schemaJson;
            JSONObject dataJson;

            try {
                schemaJson = new JSONObject(new JSONTokener(schemaStr));
            } catch (JSONException e) {
                throw new JSONException("Schema JSON格式错误", e);
            }

            try {
                dataJson = new JSONObject(new JSONTokener(dataStr));
            } catch (JSONException e) {
                throw new JSONException("Data JSON格式错误", e);
            }

            // 加载Schema并进行验证
            Schema schema = SchemaLoader.load(schemaJson);
            schema.validate(dataJson);

            System.out.println("JSON数据验证成功！");

        } catch (FileNotFoundException e) {
            System.err.println("文件未找到异常: " + e.getMessage());
            e.printStackTrace();
        } catch (SecurityException e) {
            System.err.println("安全异常（权限不足）: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("IO异常: " + e.getMessage());
            e.printStackTrace();
        } catch (JSONException e) {
            System.err.println("JSON解析异常: " + e.getMessage());
            e.printStackTrace();
        } catch (org.everit.json.schema.SchemaException e) {
            System.err.println("Schema定义异常: " + e.getMessage());
            e.printStackTrace();
        } catch (org.everit.json.schema.ValidationException e) {
            System.err.println("数据验证失败，详细信息:");
            e.getAllMessages().forEach(System.err::println);
        } catch (Exception e) {
            System.err.println("未预期的异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    public void generateCreateTableTest1() throws Exception {
        String jsonSchema = """
                {
                  "type": "object",
                  "required": ["name", "meta"],
                  "properties": {
                    "name": {
                      "type": "string",
                      "description": "姓名"
                    },
                    "age": {
                      "type": "integer"
                    },
                    "meta": {
                      "type": "object",
                      "properties": {
                        "addr": { "type": "string" }
                      }
                    }
                  }
                }
                """;
        String createTableSQL = JsonSchemaUtil.generateCreateTable("user_info", jsonSchema);
        System.out.println(createTableSQL);
    }

    @Test
    @DisplayName("条件验证")
    public void conditionTest1() {
        // 显式地告诉校验器使用 Draft 7（这是引入 if/then 的版本）。
        String jsonSchema = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": {
                    "country": { "enum": ["US", "CA"] },
                    "postal_code": { "type": "string" }
                  },
                  "allOf": [
                    {
                      "if": {
                        "properties": { "country": { "const": "US" } }
                      },
                      "then": {
                        "properties": {
                          "postal_code": { "pattern": "^[0-9]{5}(-[0-9]{4})?$" }
                        },
                        "required": ["postal_code"]
                      }
                    },
                    {
                      "if": {
                        "properties": { "country": { "const": "CA" } }
                      },
                      "then": {
                        "properties": {
                          "postal_code": { "pattern": "^[A-Z][0-9][A-Z] [0-9][A-Z][0-9]$" }
                        },
                        "required": ["postal_code"]
                      }
                    }
                  ]
                }
                """;
        String jsonData = """
                {
                    "country":"US",
                    "postal_code":"12345",
                }
                """;

        Schema schema = SchemaLoader.load(new JSONObject(new JSONTokener(jsonSchema)));
        try {
            schema.validate(new JSONObject(new JSONTokener(jsonData)));
        } catch (org.everit.json.schema.ValidationException e) {
            System.err.println("数据验证失败，详细信息:");
            e.getAllMessages().forEach(System.err::println);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    @DisplayName("递归结构定义")
    public void treeTest1() {
        String jsonSchema = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["type"],
                  "properties": {
                    "type": {
                      "type": "string",
                      "description": "节点的类型标识"
                    },
                    "children": {
                      "type": "array",
                      "description": "子节点列表",
                      "items": { "$ref": "#" }
                    }
                  }
                }
                """;
        String jsonData = """
                    {
                      "type": "001",
                      "children": [
                        {
                          "type": "001001"
                        },
                        {
                          "type": "001002",
                          "children": [
                            {
                              "type": "001002001"
                            },
                            {
                              "type": "001002002"
                            }
                          ]
                        }
                      ]
                    }
                """;

        Schema schema = SchemaLoader.load(new JSONObject(new JSONTokener(jsonSchema)));
        try {
            schema.validate(new JSONObject(new JSONTokener(jsonData)));
        } catch (org.everit.json.schema.ValidationException e) {
            System.err.println("数据验证失败，详细信息:");
            e.getAllMessages().forEach(System.err::println);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("默认值")
    public void defaultValueTest1() {
        String jsonSchemaStr = """
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

        JSONObject rawSchema = new JSONObject(new JSONTokener(jsonSchemaStr));
        Schema schema = SchemaLoader.builder()
                .schemaJson(rawSchema)
                .useDefaults(true)
                .build()
                .load()
                .build();

        try {
            JSONObject jsonData = new JSONObject("{}");
            schema.validate(jsonData);
            assertEquals(10.5, jsonData.getDouble("price"), "Price 应该被填充默认值 10.5");
        } catch (org.everit.json.schema.ValidationException e) {
            System.err.println("数据验证失败，详细信息:");
            e.getAllMessages().forEach(System.err::println);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("模式组合与复用")
    public void definitionsTest1() {
        String jsonSchema = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "definitions": {
                    "address": {
                      "type": "object",
                      "properties": {
                        "street": { "type": "string" },
                        "city": { "type": "string" }
                      }
                    }
                  },
                  "allOf": [
                    { "$ref": "#/definitions/address" },
                    { "required": ["street"] }
                  ],
                  "oneOf": [
                    { "required": ["email"] },
                    { "required": ["phone"] }
                  ],
                  "not": {
                    "required": ["ssn"]
                  }
                }
                """;
        String jsonData = """
                {
                  "street": "001",
                  "phone": "123"
                }
                """;

        JSONObject rawSchema = new JSONObject(new JSONTokener(jsonData));
        Schema schema = SchemaLoader.load(new JSONObject(new JSONTokener(jsonSchema)));

        try {
            schema.validate(rawSchema);
        } catch (org.everit.json.schema.ValidationException e) {
            System.err.println("数据验证失败，详细信息:");
            e.getAllMessages().forEach(System.err::println);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("动态模板匹配")
    public void patternPropertiesTest1() {
        String jsonSchema = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "patternProperties": {
                    "^S_": { "type": "string" },
                    "^N_": { "type": "number" },
                    "^(?![SN]_).*": { "type": "boolean" }
                  }
                }
                """;
        String jsonData = """
                {
                  "S_name": "001",
                  "N_age": 123
                }
                """;

        JSONObject rawSchema = new JSONObject(new JSONTokener(jsonData));
        Schema schema = SchemaLoader.load(new JSONObject(new JSONTokener(jsonSchema)));

        try {
            schema.validate(rawSchema);
        } catch (org.everit.json.schema.ValidationException e) {
            System.err.println("数据验证失败，详细信息:");
            e.getAllMessages().forEach(System.err::println);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("自定义验证")
    public void customerFormatTest1() {
        String jsonSchema = """
                {
                  "type": "object",
                  "properties": {
                    "password": {
                      "type": "string",
                      "format": "strong-password"
                    }
                  },
                  "required": ["password"]
                }
                """;
        String jsonData = """
                {
                  "password": "1Q2W!q@w"
                }
                """;

        JSONObject rawSchema = new JSONObject(new JSONTokener(jsonData));
        SchemaLoader.SchemaLoaderBuilder loaderBuilder = SchemaLoader.builder().schemaJson(new JSONObject(new JSONTokener(jsonSchema)));
        loaderBuilder.addFormatValidator("strong-password", new JsonSchemaFormatValidation.StrongPasswordValidator());

        try {
            loaderBuilder.build().load().build().validate(rawSchema);
        } catch (org.everit.json.schema.ValidationException e) {
            System.err.println("数据验证失败，详细信息:");
            e.getAllMessages().forEach(System.err::println);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void victoolsJsonSchemaTest1() {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_7, OptionPreset.PLAIN_JSON)
                .with(new JacksonModule())
                .with(new JakartaValidationModule());

        configBuilder.forFields()
                .withInstanceAttributeOverride((node, field, config) -> {
                    Pattern pattern = field.getAnnotationConsideringFieldAndGetter(Pattern.class);
                    if (pattern != null) {
                        ((ObjectNode) node).put("pattern", pattern.regexp());
                    }
                });

        SchemaGeneratorConfig config = configBuilder.build();
        SchemaGenerator generator = new SchemaGenerator(config);

        JsonNode jsonSchema = generator.generateSchema(HeadDTO.class);
        System.out.println(jsonSchema.toPrettyString());
    }

}
