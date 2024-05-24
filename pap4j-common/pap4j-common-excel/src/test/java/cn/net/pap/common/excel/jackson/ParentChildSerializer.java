package cn.net.pap.common.excel.jackson;

import cn.net.pap.common.excel.dto.ParentChildDTO;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 序列化器
 */
public class ParentChildSerializer extends JsonSerializer<ParentChildDTO> {

    private ThreadLocal<Map<ParentChildDTO, Boolean>> seen = ThreadLocal.withInitial(IdentityHashMap::new);

    @Override
    public void serialize(ParentChildDTO person, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        Map<ParentChildDTO, Boolean> seenObjects = seen.get();
        if (seenObjects.containsKey(person)) {
            jgen.writeStartObject();
            jgen.writeStringField("remark", person.getRemark());
            jgen.writeEndObject();
            return;
        }

        seenObjects.put(person, Boolean.TRUE);

        jgen.writeStartObject();
        jgen.writeStringField("remark", person.getRemark());

        if (person.getChild() != null) {
            jgen.writeArrayFieldStart("child");
            for (ParentChildDTO child : person.getChild()) {
                serialize(child, jgen, provider);
            }
            jgen.writeEndArray();
        }

        if (person.getParent() != null) {
            jgen.writeObjectFieldStart("parent");
            jgen.writeStringField("remark", person.getParent().getRemark());
            jgen.writeEndObject();
        }

        jgen.writeEndObject();
        seenObjects.remove(person);
    }

}

