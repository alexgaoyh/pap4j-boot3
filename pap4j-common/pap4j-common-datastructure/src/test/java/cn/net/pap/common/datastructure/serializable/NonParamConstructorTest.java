package cn.net.pap.common.datastructure.serializable;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class NonParamConstructorTest {

    private static final Logger log = LoggerFactory.getLogger(NonParamConstructorTest.class);

    // @Test
    public void throwException() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try {
            NonParamConstructor nonParamConstructor = new NonParamConstructor("Alice", 30);
            // 序列化 - 成功
            String json = mapper.writeValueAsString(nonParamConstructor);
            log.info("序列化结果: {}", json);

            // 反序列化 - 失败 -  todo 需无参构造函数
            NonParamConstructor deserializedBook = mapper.readValue(json, NonParamConstructor.class);
            log.info("反序列化结果: {}", deserializedBook);
        } catch (Exception e) {
            log.error("Exception occurred during serialization/deserialization", e);
        }
    }
}
