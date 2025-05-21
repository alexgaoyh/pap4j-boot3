package cn.net.pap.common.datastructure.serializable;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class NonParamConstructorTest {

    // @Test
    public void throwException() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try {
            NonParamConstructor nonParamConstructor = new NonParamConstructor("Alice", 30);
            // 序列化 - 成功
            String json = mapper.writeValueAsString(nonParamConstructor);
            System.out.println("序列化结果: " + json);

            // 反序列化 - 失败 -  todo 需无参构造函数
            NonParamConstructor deserializedBook = mapper.readValue(json, NonParamConstructor.class);
            System.out.println("反序列化结果: " + deserializedBook);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


