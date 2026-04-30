package cn.net.pap.common.jsonorm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.net.pap.common.jsonorm.util.JsonlUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
public class JsonlTest {
    private static final Logger log = LoggerFactory.getLogger(JsonlTest.class);

    // 定义临时文件路径
    private static String tempFilePath;

    @BeforeAll
    public static void setUp() throws IOException {
        // 创建临时文件并获取其绝对路径
        Path tempFile = Files.createTempFile("jsonl_test_", ".jsonl");
        tempFilePath = tempFile.toAbsolutePath().toString();
        log.info("测试开始，创建临时文件: {}", tempFilePath);
    }

    @AfterAll
    public static void tearDown() throws IOException {
        // 测试结束后删除文件
        if (tempFilePath != null) {
            Files.deleteIfExists(Path.of(tempFilePath));
            log.info("测试结束，已删除临时文件: {}", tempFilePath);
        }
    }

    @Test
    @Order(1)
    public void writeLastLineTest() throws Exception {
        Map<String, Object> tmp = new HashMap<>();
        tmp.put("timeswap", System.currentTimeMillis());
        ObjectMapper objectMapper = new ObjectMapper();
        // 将原来的固定文件名改为 tempFilePath
        boolean b = JsonlUtil.writeLastLine(tempFilePath, objectMapper.writeValueAsString(tmp));
        log.info("{}", b);
    }

    @Test
    @Order(2)
    public void readLastLineTest() {
        // 将原来的固定文件名改为 tempFilePath
        log.info("{}", JsonlUtil.readLastLine(tempFilePath));
    }


}
