package cn.net.pap.common.jsonorm.serializer;

import cn.net.pap.common.jsonorm.dto.TableFieldValueDTO;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * 自定义序列的字段 - 外部传参
 */
public class TableFieldValueDTOSerializer extends JsonSerializer<TableFieldValueDTO> {

    private java.util.List<String> fieldList;

    public TableFieldValueDTOSerializer(java.util.List<String> fieldList) {
        this.fieldList = fieldList;
    }

    @Override
    public void serialize(TableFieldValueDTO value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        if(fieldList != null && fieldList.size() > 0) {
            for(String fieldStr : fieldList) {
                try {
                    Field field = TableFieldValueDTO.class.getDeclaredField(fieldStr);
                    field.setAccessible(true);
                    Object object = field.get(value);
                    gen.writeObjectField(fieldStr, object);
                } catch (Exception e) {
                }
            }
        }

        gen.writeEndObject();
    }

}
