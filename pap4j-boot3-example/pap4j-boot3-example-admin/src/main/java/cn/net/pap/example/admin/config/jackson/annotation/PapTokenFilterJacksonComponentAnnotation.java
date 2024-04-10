package cn.net.pap.example.admin.config.jackson.annotation;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import jakarta.servlet.http.HttpServletRequest;
import jdk.jfr.Description;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * 判断 request 的 header{field} 是否有当前属性，如果有则正常序列化，如果没有则忽略当前值
 */
public class PapTokenFilterJacksonComponentAnnotation {

    /**
     * 序列化器
     */
    public static class TokenFilterSerializer extends JsonSerializer<String> {

        /**
         * 序列化操作,继承 JsonSerializer，重写 Serialize 函数
         */
        @Override
        public void serialize(String value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            // 当前序列化的字段名
            String currentFieldName = jsonGenerator.getOutputContext().getCurrentName();
            // 当前的 httpRequest
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            if(request.getHeader("field") != null && request.getHeader("field").toString().contains(currentFieldName)) {
                jsonGenerator.writeString(value);
            } else {
                jsonGenerator.writeString("");
            }

            try {
                // 获取序列化对象，然后获得一些当前字段有哪些其他指定的注解。
                Object currentValue = jsonGenerator.getCurrentValue();
                Field currentFieldNameField = currentValue.getClass().getDeclaredField(currentFieldName);
                Description descriptionAnno = currentFieldNameField.getAnnotation(Description.class);
                if(descriptionAnno != null) {
                    String descriptionValue = descriptionAnno.value();
                    System.out.println(descriptionValue);
                    // 新增字段
//                jsonGenerator.writeFieldName(currentFieldName + "_Description");
//                jsonGenerator.writeString(descriptionValue);
                }
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 反序列化器
     */
    public static class TokenFilterDeserializer extends JsonDeserializer<String> {

        /**
         * 反序列化操作,继承 JsonDeserializer，重写 deserialize 函数
         */
        @Override
        public String deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            String dateStr = null;
            try {
                dateStr = jsonParser.getText();
            } catch (Exception e) {
            }
            return dateStr;
        }
    }

}
