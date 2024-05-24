package cn.net.pap.neo4j.deserializer.jackson;

import cn.net.pap.neo4j.entity.DepartmentEntity;
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
public class DepartmentEntityDeserializer extends StdDeserializer<DepartmentEntity> {

    private Map<String, DepartmentEntity> dtoMap = new HashMap<>();

    public DepartmentEntityDeserializer() {
        this(null);
    }

    public DepartmentEntityDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public DepartmentEntity deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = jp.getCodec().readTree(jp);
        return deserializeNode(node, null);
    }

    private DepartmentEntity deserializeNode(JsonNode node, DepartmentEntity parent) {
        String remark = node.get("remark").asText();
        DepartmentEntity dto = new DepartmentEntity();
        dto.setRemark(remark);
        dto.setParent(parent);

        if (node.has("child")) {
            Iterator<JsonNode> elements = node.get("child").elements();
            while (elements.hasNext()) {
                DepartmentEntity childDTO = deserializeNode(elements.next(), dto);
                List<DepartmentEntity> child = dto.getChild();
                if (child == null) {
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
