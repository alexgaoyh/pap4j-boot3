package cn.net.pap.common.jsonorm;

import cn.net.pap.common.jsonorm.util.JsonSchemaGeneratorUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class JsonSchemaGeneratorUtilTest {

    @Test
    public void test() {
        String json = "{\"name\":\"alexgaoyh\", \"age\":35, \"city\":\"China\"}";

        try {
            String jsonSchema = JsonSchemaGeneratorUtil.convertJsonToJsonSchema(json);
            System.out.println(jsonSchema);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
