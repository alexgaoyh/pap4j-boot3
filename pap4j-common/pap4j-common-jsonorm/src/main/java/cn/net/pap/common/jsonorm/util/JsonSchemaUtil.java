package cn.net.pap.common.jsonorm.util;

import cn.net.pap.common.jsonorm.util.dto.ValidationDTO;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.jakarta.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.jakarta.JsonSchemaGenerator;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        if(annotation instanceof JsonPropertyDescription) {
            String description = ((JsonPropertyDescription) annotation).value();
            ValidationDTO validationDTO = new ValidationDTO();
            validationDTO.setType("JsonPropertyDescription");
            validationDTO.setMessage(description);
            return validationDTO;
        }
        if(annotation instanceof NotNull) {
            String message = ((NotNull) annotation).message();
            ValidationDTO validationDTO = new ValidationDTO();
            validationDTO.setType("NotNull");
            validationDTO.setMessage(message);
            return validationDTO;
        }
        if(annotation instanceof NotEmpty) {
            String message = ((NotEmpty) annotation).message();
            ValidationDTO validationDTO = new ValidationDTO();
            validationDTO.setType("NotEmpty");
            validationDTO.setMessage(message);
            return validationDTO;
        }
        if(annotation instanceof Size) {
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
}
