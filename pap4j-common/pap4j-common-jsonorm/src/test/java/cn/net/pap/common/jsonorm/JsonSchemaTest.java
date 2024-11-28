package cn.net.pap.common.jsonorm;

import cn.net.pap.common.jsonorm.dto.MappingORMDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.jakarta.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.jakarta.JsonSchemaGenerator;
import org.junit.jupiter.api.Test;

public class JsonSchemaTest {

    @Test
    public void test1() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(objectMapper);

        JsonSchema schema = schemaGen.generateSchema(MappingORMDTO.class);

        String schemaJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
        System.out.println(schemaJson);
    }


}
