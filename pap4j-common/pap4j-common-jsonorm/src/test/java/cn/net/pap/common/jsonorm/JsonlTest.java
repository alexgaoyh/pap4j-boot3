package cn.net.pap.common.jsonorm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.net.pap.common.jsonorm.util.JsonlUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.Map;

@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
public class JsonlTest {
    private static final Logger log = LoggerFactory.getLogger(JsonlTest.class);

    @Test
    @Order(1)
    public void writeLastLineTest() throws Exception {
        Map<String, Object> tmp = new HashMap<>();
        tmp.put("timeswap", System.currentTimeMillis());
        ObjectMapper objectMapper = new ObjectMapper();
        boolean b = JsonlUtil.writeLastLine("jsonl.jsonl", objectMapper.writeValueAsString(tmp));
        log.info("{}", b);
    }

    @Test
    @Order(2)
    public void readLastLineTest() {
        log.info("{}", JsonlUtil.readLastLine("jsonl.jsonl"));
    }


}
