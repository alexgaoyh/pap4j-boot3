package cn.net.pap.neo4j.serializer.jackson;

import cn.net.pap.neo4j.entity.PersonEntity;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * 使用 JsonSerializer 处理序列化过程中的循环引用
 *             ObjectMapper objectMapperSerializer = new ObjectMapper();
 *             SimpleModule module = new SimpleModule();
 *             module.addSerializer(PersonEntity.class, new PersonEntitySerializer());
 *             objectMapperSerializer.registerModule(module);
 *             String serializerStr = objectMapperSerializer.writeValueAsString(p2);
 */
public class PersonEntitySerializer extends JsonSerializer<PersonEntity> {

    private Set<PersonEntity> seen = new HashSet<>();

    @Override
    public void serialize(PersonEntity person, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        if (seen.contains(person)) {
            // 这里可以更改逻辑， 使用 jgen.writeNull() 不再写入数据.
            jgen.writeStartObject();
            jgen.writeStringField("personId", person.getPersonId());
            jgen.writeStringField("personName", person.getPersonName());
            jgen.writeStringField("description", person.getDescription());
            jgen.writeObjectField("hobbys", person.getHobbys());
            jgen.writeEndObject();
        } else {
            seen.add(person);

            jgen.writeStartObject();
            jgen.writeStringField("personId", person.getPersonId());
            jgen.writeStringField("personName", person.getPersonName());
            jgen.writeStringField("description", person.getDescription());
            jgen.writeObjectField("hobbys", person.getHobbys());

            if (person.getChildrens() != null) {
                jgen.writeArrayFieldStart("childrens");
                for (PersonEntity child : person.getChildrens()) {
                    serialize(child, jgen, provider);
                }
                jgen.writeEndArray();
            }

            if (person.getParents() != null) {
                jgen.writeArrayFieldStart("parents");
                for (PersonEntity parent : person.getParents()) {
                    serialize(parent, jgen, provider);
                }
                jgen.writeEndArray();
            }

            jgen.writeEndObject();
        }
    }

}

