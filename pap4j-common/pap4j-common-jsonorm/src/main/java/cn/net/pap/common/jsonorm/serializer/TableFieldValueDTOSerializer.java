package cn.net.pap.common.jsonorm.serializer;

import cn.net.pap.common.jsonorm.dto.TableFieldValueDTO;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 自定义序列的字段
 */
public class TableFieldValueDTOSerializer extends JsonSerializer<TableFieldValueDTO> {

    @Override
    public void serialize(TableFieldValueDTO value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("tableName", value.getTableName());
        gen.writeStringField("pk", value.getPk());
        gen.writeObjectField("valueMap", value.getValueMap());
        gen.writeEndObject();
    }

}
