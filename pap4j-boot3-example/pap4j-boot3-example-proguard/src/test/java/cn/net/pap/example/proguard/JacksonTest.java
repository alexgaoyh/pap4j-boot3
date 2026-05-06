package cn.net.pap.example.proguard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class JacksonTest {

    private static final Logger log = LoggerFactory.getLogger(JacksonTest.class);

    @Test
    public void parseTest() throws Exception {
        String cellContent = "{\"threadId\":\"http-nio-30000-exec-2\",\"timeswap\":1719883262069}";
        Map o = new ObjectMapper().readValue(cellContent, Map.class);
        log.info("{}", o);
    }
}
