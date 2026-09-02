package cn.net.pap.logback.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <h2>ReqResLoggerHttpFilter 录制与回放功能一体化演示单测</h2>
 *
 * <p>本类不仅是 {@link ReqResLoggerHttpFilter} 的功能单元测试，同时也做为整个项目的最佳实践案例。</p>
 *
 * <h3>录制与回放 (Record & Replay) 核心理念与流程：</h3>
 * <ol>
 *   <li><b>录制 (Record)：</b>当业务接口运行中，响应状态码返回 4xx/5xx，或抛出未捕获异常时，
 *       过滤器自动将请求的上下文（方法、URI、参数、请求体）和异常调用栈以结构化的 JSON 文件写出到本地临时目录。</li>
 *   <li><b>传输/提炼：</b>开发人员将该 JSON 文件提供给 AI，或者由 CI/CD 自动扫描出错目录下的 JSON 报文。</li>
 *   <li><b>回放 (Replay)：</b>AI 根据 JSON 里的 HTTP 信息自动生成轻量级的 MockMvc 单元测试（无需启动真实的 Web 容器或占用端口），
 *       以“最小上下文” 100% 重现生产/联调环境下的报错，并在本地修复该 Bug。</li>
 * </ol>
 */
public class ReqResLoggerReplayTest {

    private MockMvc mockMvc;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReqResLoggerReplayTest.class);

    @BeforeEach
    public void setUp() {
        // 使用 MockMvcBuilders 构建最小容器，并装配日志过滤器
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController()).addFilters(new ReqResLoggerHttpFilter()).build();
    }

    @AfterEach
    public void tearDown() {
        // 测试执行完毕后，清理生成的 bug 录制临时文件，保持工作区干净整洁
        String targetPathStr = getRecordedBugsPath();
        cleanRecordedBugsDir(targetPathStr);
    }

    @AfterAll
    public static void tearDownAll() {
        // 停止 Logback 上下文以释放所有日志文件句柄和锁，防止 Windows 下无法删除文件
        org.slf4j.ILoggerFactory factory = org.slf4j.LoggerFactory.getILoggerFactory();
        if (factory instanceof ch.qos.logback.classic.LoggerContext context) {
            context.stop();
        }

        // 删除整个 logs 临时目录
        try {
            Path logsPath = Paths.get("logs");
            if (Files.exists(logsPath)) {
                try (Stream<Path> walk = Files.walk(logsPath)) {
                    walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
            }
        } catch (Exception e) {
            log.error("清理 logs 临时目录失败", e);
            // Ignore
        }
    }

    @Test
    public void testDemo_Error400_RecordAndReplay() throws Exception {
        // 清理已有的 bug 录制目录，以便测试我们这次生成的快照
        String targetPathStr = getRecordedBugsPath();
        cleanRecordedBugsDir(targetPathStr);

        // 1. 【触发与录制】发起模拟的 400 BadRequest 接口请求
        mockMvc.perform(get("/test/error400")).andExpect(status().isBadRequest());

        // 2. 【自动生成快照验证】读取录制的快照文件
        File bugFile = getLatestBugFile(targetPathStr);
        assertNotNull(bugFile, "应该生成了 bug 快照文件");

        JsonNode snapshot = OBJECT_MAPPER.readTree(bugFile);

        // 3. 【回放断言】分析录制的 JSON 结构，验证快照保存的数据与当时请求完全吻合
        assertEquals("GET", snapshot.path("request").path("method").asText());
        assertEquals("/test/error400", snapshot.path("request").path("uri").asText());
        assertEquals(400, snapshot.path("response").path("status").asInt());
        assertEquals("{\"error\": \"Bad request parameter\"}", snapshot.path("response").path("body").asText());
    }

    @Test
    public void testDemo_Error500_RecordAndReplay() throws Exception {
        String targetPathStr = getRecordedBugsPath();
        cleanRecordedBugsDir(targetPathStr);

        // 1. 【触发与录制】发起模拟抛出异常的 500 请求
        try {
            mockMvc.perform(get("/test/error500")).andExpect(status().isInternalServerError());
        } catch (Exception e) {
            log.error("触发 500 异常场景时请求失败", e);
            // 在 StandaloneSetup 下，异常会直接向外抛出，这代表了 Servlet 容器里的原始报错
        }

        // 2. 【自动生成快照验证】读取录制的快照文件
        File bugFile = getLatestBugFile(targetPathStr);
        assertNotNull(bugFile, "应该生成了 bug 快照文件");

        JsonNode snapshot = OBJECT_MAPPER.readTree(bugFile);

        // 3. 【回放断言】分析录制的异常信息
        assertEquals("GET", snapshot.path("request").path("method").asText());
        assertEquals("/test/error500", snapshot.path("request").path("uri").asText());
        assertEquals("jakarta.servlet.ServletException", snapshot.path("exception").path("className").asText());
        assertTrue(snapshot.path("exception").path("message").asText().contains("Simulated server exception"));
        assertTrue(snapshot.path("exception").path("stackTrace").isArray());
        assertTrue(snapshot.path("exception").path("stackTrace").size() > 0);
    }

    private String getRecordedBugsPath() {
        String logHome = null;
        if (org.slf4j.LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext context) {
            logHome = context.getProperty("LOG_HOME");
            if (logHome != null) {
                try {
                    logHome = ch.qos.logback.core.util.OptionHelper.substVars(logHome, context);
                } catch (Exception e) {
                    log.error("解析 LOG_HOME 变量失败", e);
                    // Ignore
                }
            }
        }
        if (logHome == null || logHome.trim().isEmpty()) {
            logHome = "logs";
        }
        return logHome + "/recorded-bugs";
    }

    private void cleanRecordedBugsDir(String dirPathStr) {
        try {
            Path path = Paths.get(dirPathStr);
            if (Files.exists(path)) {
                try (Stream<Path> walk = Files.walk(path)) {
                    walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
            }
        } catch (Exception e) {
            log.error("清理 bug 录制目录失败", e);
            // Ignore
        }
    }

    private File getLatestBugFile(String dirPathStr) {
        File dir = new File(dirPathStr);
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }
        File[] files = dir.listFiles((d, name) -> name.startsWith("bug_") && name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return null;
        }
        // 按最后修改时间倒序排列，取最新的一个
        Stream.of(files).sorted((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        return files[0];
    }

    /**
     * 演示用 RestController
     */
    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/error400")
        public ResponseEntity<String> error400() {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"error\": \"Bad request parameter\"}");
        }

        @GetMapping("/error500")
        public String error500() {
            throw new RuntimeException("Simulated server exception");
        }
    }
}
