package cn.net.pap.example.proguard.convert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.AttributeConverter;

/**
 * Jackson ObjectNode converter
 */
public class JacksonObjectNodeConverter implements AttributeConverter<ObjectNode, String> {

    @Override
    public String convertToDatabaseColumn(ObjectNode objectNode) {
        if (objectNode == null) {
            return null;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(objectNode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not convert ObjectNode to JSON string", e);
        }
    }

    @Override
    public ObjectNode convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(dbData);
            if (jsonNode.isObject()) {
                return (ObjectNode)jsonNode;
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not convert JSON to ObjectNode", e);
        }
        return null;
    }

}