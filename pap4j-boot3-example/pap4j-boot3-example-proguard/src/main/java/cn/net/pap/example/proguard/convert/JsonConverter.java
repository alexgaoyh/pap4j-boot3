package cn.net.pap.example.proguard.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;

/**
 * JSON support
 */
public class JsonConverter implements AttributeConverter<Object, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JsonConverter.class);

    @Override
    public String convertToDatabaseColumn(Object attribute) {
        try {
            return attribute == null ? null : MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            log.error("JSON serialize error", e);
            throw new IllegalArgumentException("JSON serialize error", e);
        }
    }

    @Override
    public Object convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null) {
                return null;
            }
            com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(dbData);
            if (node.isTextual()) {
                return MAPPER.readTree(node.asText());
            }
            return node;
        } catch (Exception e) {
            log.error("JSON deserialize error", e);
            throw new IllegalArgumentException("JSON deserialize error", e);
        }
    }
}

