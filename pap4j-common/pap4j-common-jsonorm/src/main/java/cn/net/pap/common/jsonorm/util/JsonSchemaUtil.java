package cn.net.pap.common.jsonorm.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.jakarta.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.jakarta.JsonSchemaGenerator;
import com.fasterxml.jackson.module.jsonSchema.jakarta.types.ObjectSchema;
import com.fasterxml.jackson.module.jsonSchema.jakarta.types.StringSchema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.lang.reflect.Field;

public class JsonSchemaUtil {

    /**
     * toSchema
     *
     * @param clazz
     * @return
     * @throws Exception
     */
    public static String toSchema(Class clazz) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(objectMapper);

        JsonSchema schema = schemaGen.generateSchema(clazz);

        for (Field field : clazz.getDeclaredFields()) {
            JsonSchema fieldJsonSchema = ((ObjectSchema) schema).getProperties().get(field.getName());
            // 处理 @NotNull 注解
            if (field.isAnnotationPresent(NotNull.class)) {
                fieldJsonSchema.setRequired(true);
            }

            // 处理 @Size 注解
            if (field.isAnnotationPresent(Size.class)) {
                Size size = field.getAnnotation(Size.class);
                if (size.min() > 0) {
                    if (fieldJsonSchema instanceof StringSchema) {
                        ((StringSchema) fieldJsonSchema).setMinLength(size.min());
                    }
                    // todo another jsonSchema Type
                }
                if (size.max() > 0) {
                    if (fieldJsonSchema instanceof StringSchema) {
                        ((StringSchema) fieldJsonSchema).setMaxLength(size.max());
                    }
                }
            }

            // todo another Annotation
        }
        String schemaJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
        return schemaJson;

    }
}
