package cn.net.pap.common.jsonorm.util;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.util.List;
import java.util.stream.Collectors;

public class JacksonUtil {

    /**
     * ObjectMapper
     *
     * @param fieldsToExclude
     * @return
     */
    public static ObjectMapper createObjectMapper(List<String> fieldsToExclude) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializerFactory(mapper.getSerializerFactory().withSerializerModifier(new DynamicFieldExclusionModifier(fieldsToExclude)));
        return mapper;
    }

    private static class DynamicFieldExclusionModifier extends BeanSerializerModifier {
        private final List<String> fieldsToExclude;

        public DynamicFieldExclusionModifier(List<String> fieldsToExclude) {
            this.fieldsToExclude = fieldsToExclude;
        }

        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
            return beanProperties.stream().filter(writer -> !fieldsToExclude.contains(writer.getName()))
                    .collect(Collectors.toList());
        }
    }

}
