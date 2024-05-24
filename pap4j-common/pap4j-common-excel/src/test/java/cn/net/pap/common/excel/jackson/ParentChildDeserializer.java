package cn.net.pap.common.excel.jackson;

import cn.net.pap.common.excel.dto.ParentChildDTO;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.*;

/**
 * 反序列化器
 */
public class ParentChildDeserializer extends StdDeserializer<ParentChildDTO> {

    private Map<String, ParentChildDTO> dtoMap = new HashMap<>();

    public ParentChildDeserializer() {
        this(null);
    }

    public ParentChildDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public ParentChildDTO deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = jp.getCodec().readTree(jp);
        return deserializeNode(node, null);
    }

    private ParentChildDTO deserializeNode(JsonNode node, ParentChildDTO parent) {
        String remark = node.get("remark").asText();
        ParentChildDTO dto = new ParentChildDTO();
        dto.setRemark(remark);
        dto.setParent(parent);

        if (node.has("child")) {
            Iterator<JsonNode> elements = node.get("child").elements();
            while (elements.hasNext()) {
                ParentChildDTO childDTO = deserializeNode(elements.next(), dto);
                List<ParentChildDTO> child = dto.getChild();
                if(child == null) {
                    child = new ArrayList<>();
                }
                child.add(childDTO);
                dto.setChild(child);
            }
        }

        dtoMap.put(remark, dto);
        return dto;
    }
}
