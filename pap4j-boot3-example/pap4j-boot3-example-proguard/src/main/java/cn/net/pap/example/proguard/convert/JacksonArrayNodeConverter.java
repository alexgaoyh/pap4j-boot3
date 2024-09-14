package cn.net.pap.example.proguard.convert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.persistence.AttributeConverter;

/**
 * Jackson ArrayNode converter
 */
public class JacksonArrayNodeConverter implements AttributeConverter<ArrayNode, String> {

    @Override
    public String convertToDatabaseColumn(ArrayNode arrayNode) {
        if (arrayNode == null) {
            return null;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(arrayNode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not convert ArrayNode to JSON string", e);
        }
    }

    @Override
    public ArrayNode convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(dbData);
            if (jsonNode.isArray()) {
                return (ArrayNode) jsonNode;
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not convert JSON to ArrayNode", e);
        }
        return null;
    }

}