package cn.net.pap.common.jsonorm.util;

import cn.net.pap.common.jsonorm.util.dto.ValidationDTO;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import com.fasterxml.jackson.module.jsonSchema.jakarta.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.jakarta.JsonSchemaGenerator;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JsonSchemaUtil {

    /**
     * toSchema
     *
     * @param clazz
     * @return
     * @throws Exception
     */
    public static String toSchema(Class clazz) throws Exception {
        Map<String, Object> returnMap = new HashMap<String, Object>();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(objectMapper);
        JsonSchema schema = schemaGen.generateSchema(clazz);
        returnMap.put("schema", schema);

        List<Map<String, Object>> validatorList = new ArrayList<Map<String, Object>>();
        for (Field field : clazz.getDeclaredFields()) {
            Map<String, Object> fieldMap = new HashMap<>();
            fieldMap.put("name", field.getName());
            fieldMap.put("type", field.getType().getSimpleName());

            List<ValidationDTO> validationDTOList = new ArrayList<>();
            Annotation[] annotations = field.getAnnotations();
            for (Annotation annotation : annotations) {
                validationDTOList.add(convert(annotation));
            }
            fieldMap.put("validation", validationDTOList);
            validatorList.add(fieldMap);
        }
        returnMap.put("validator", validatorList);
        String schemaJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(returnMap);
        return schemaJson;

    }

    private static ValidationDTO convert(Annotation annotation) {
        if (annotation instanceof JsonPropertyDescription) {
            String description = ((JsonPropertyDescription) annotation).value();
            ValidationDTO validationDTO = new ValidationDTO();
            validationDTO.setType("JsonPropertyDescription");
            validationDTO.setMessage(description);
            return validationDTO;
        }
        if (annotation instanceof NotNull) {
            String message = ((NotNull) annotation).message();
            ValidationDTO validationDTO = new ValidationDTO();
            validationDTO.setType("NotNull");
            validationDTO.setMessage(message);
            return validationDTO;
        }
        if (annotation instanceof NotEmpty) {
            String message = ((NotEmpty) annotation).message();
            ValidationDTO validationDTO = new ValidationDTO();
            validationDTO.setType("NotEmpty");
            validationDTO.setMessage(message);
            return validationDTO;
        }
        if (annotation instanceof Size) {
            Size size = ((Size) annotation);
            ValidationDTO validationDTO = new ValidationDTO();
            validationDTO.setType("Size");
            validationDTO.setMessage(size.message());
            validationDTO.setMin(size.min());
            validationDTO.setMax(size.max());
            return validationDTO;
        }
        // todo some other annotation
        return null;
    }

    /**
     * json schema to create table sql
     *
     * @param tableName
     * @param jsonSchema
     * @return
     * @throws Exception
     */
    public static String generateCreateTable(String tableName, String jsonSchema) throws Exception {
        ObjectMapper mapper = JsonMapper.builder().addModule(new AfterburnerModule()).build();
        JsonNode root = mapper.readTree(jsonSchema);

        JsonNode properties = root.get("properties");
        Set<String> required = parseRequired(root);

        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE ").append(tableName).append(" (\n");
        ddl.append("  id BIGINT PRIMARY KEY AUTO_INCREMENT,\n");

        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            ddl.append(buildColumn(entry.getKey(), entry.getValue(), required));
        }

        ddl.setLength(ddl.length() - 2); // 去掉最后逗号
        ddl.append("\n);");

        return ddl.toString();
    }

    private static Set<String> parseRequired(JsonNode root) {
        Set<String> required = new HashSet<>();
        JsonNode req = root.get("required");
        if (req != null && req.isArray()) {
            req.forEach(n -> required.add(n.asText()));
        }
        return required;
    }

    private static String buildColumn(String name, JsonNode schema, Set<String> required) {
        String sqlType = resolveSqlType(schema);
        boolean notNull = required.contains(name);

        StringBuilder col = new StringBuilder();
        col.append("  ").append(name).append(" ").append(sqlType);

        if (notNull) {
            col.append(" NOT NULL");
        }

        if (schema.has("description")) {
            col.append(" COMMENT '").append(schema.get("description").asText().replace("'", "")).append("'");
        }

        col.append(",\n");
        return col.toString();
    }

    private static String resolveSqlType(JsonNode schema) {
        String type = schema.has("type") ? schema.get("type").asText() : null;

        if (type == null) {
            return "JSON";
        }

        switch (type) {
            case "string":
                return resolveString(schema);
            case "integer":
                return "BIGINT";
            case "number":
                return "DECIMAL(18,6)";
            case "boolean":
                return "BOOLEAN";
            case "object":
            case "array":
                return "JSON";
            default:
                return "JSON";
        }
    }

    private static String resolveString(JsonNode schema) {
        if (schema.has("format")) {
            switch (schema.get("format").asText()) {
                case "date":
                    return "DATE";
                case "date-time":
                    return "TIMESTAMP";
                case "email":
                case "uuid":
                    return "VARCHAR(128)";
            }
        }

        if (schema.has("maxLength")) {
            return "VARCHAR(" + schema.get("maxLength").asInt() + ")";
        }

        return "VARCHAR(255)";
    }

}
