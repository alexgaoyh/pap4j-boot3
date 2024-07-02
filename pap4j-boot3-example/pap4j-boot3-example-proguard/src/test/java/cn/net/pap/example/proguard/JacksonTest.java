package cn.net.pap.example.proguard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class JacksonTest {

    @Test
    public void parseTest() throws Exception {
        String cellContent = "{\"threadId\":\"http-nio-30000-exec-2\",\"timeswap\":1719883262069}";
        Map o = new ObjectMapper().readValue(cellContent, Map.class);
        System.out.println(o);
    }
}
