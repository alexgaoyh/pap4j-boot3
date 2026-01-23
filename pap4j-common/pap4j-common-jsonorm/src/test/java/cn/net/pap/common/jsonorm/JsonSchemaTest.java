package cn.net.pap.common.jsonorm;

import cn.net.pap.common.jsonorm.dto.MappingORMDTO;
import cn.net.pap.common.jsonorm.util.JsonSchemaUtil;
import cn.net.pap.common.jsonorm.util.dto.SchemaDTO;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

}
