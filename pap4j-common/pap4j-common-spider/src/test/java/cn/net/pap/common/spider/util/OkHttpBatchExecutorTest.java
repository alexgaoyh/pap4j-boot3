package cn.net.pap.common.spider.util;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OkHttpBatchExecutorTest {

    private static final Logger log = LoggerFactory.getLogger(OkHttpBatchExecutorTest.class);

    private MockWebServer server;
    private OkHttpClient client;
    private OkHttpBatchExecutor executor;

    @BeforeEach
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new OkHttpClient();
        // 核心线程设为1，便于在测试中模拟排队或复用
        executor = new OkHttpBatchExecutor(client, 1, 1, 10);
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (executor != null) {
            executor.close();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * 测试批量 JSON POST 请求成功的情况
     */
    @Test
    public void testExecuteBatchSuccess() throws Exception {
        server.enqueue(new MockResponse().setBody("Result-1"));
        server.enqueue(new MockResponse().setBody("Result-2"));

        String url = server.url("/api/batch").toString();
        List<String> bodies = Arrays.asList("{\"id\":1}", "{\"id\":2}");

        List<OkHttpBatchExecutor.BatchResult> results = executor.executeBatch(url, bodies);

        assertEquals(2, results.size());
        assertEquals(OkHttpBatchExecutor.Status.SUCCESS, results.get(0).status());
        assertEquals("Result-1", results.get(0).data());
        assertEquals(OkHttpBatchExecutor.Status.SUCCESS, results.get(1).status());
        assertEquals("Result-2", results.get(1).data());

        // 验证 MockWebServer 接收到的内容
        assertEquals("{\"id\":1}", server.takeRequest().getBody().readUtf8());
        assertEquals("{\"id\":2}", server.takeRequest().getBody().readUtf8());
    }

    /**
     * 测试连接复用
     */
    @Test
    public void testConnectionReuse() throws Exception {
        AtomicInteger connectCount = new AtomicInteger(0);
        OkHttpClient reusableClient = new OkHttpClient.Builder()
                .eventListener(new okhttp3.EventListener() {
                    @Override
                    public void connectStart(okhttp3.Call call, java.net.InetSocketAddress inetSocketAddress, java.net.Proxy proxy) {
                        connectCount.incrementAndGet();
                    }
                })
                .build();

        try (OkHttpBatchExecutor reusableExecutor = new OkHttpBatchExecutor(reusableClient, 1, 1, 10)) {
            for (int i = 0; i < 3; i++) server.enqueue(new MockResponse().setBody("OK"));

            String url = server.url("/reuse").toString();
            List<String> bodies = Arrays.asList("{}", "{}", "{}");

            reusableExecutor.executeBatch(url, bodies);

            // 串行执行下，3个请求应复用1个物理连接
            assertTrue(connectCount.get() < 3);
        }
    }

    /**
     * 测试任务拒绝（队列满）
     */
    @Test
    public void testExecuteBatchRejection() throws Exception {
        // 容量极小：1个线程，1个队列位
        try (OkHttpBatchExecutor smallExecutor = new OkHttpBatchExecutor(client, 1, 1, 1)) {
            // 第一个请求慢一点，占住线程
            server.enqueue(new MockResponse().setBody("Late").setBodyDelay(500, TimeUnit.MILLISECONDS));
            server.enqueue(new MockResponse().setBody("Queued"));

            String url = server.url("/reject").toString();
            List<String> bodies = Arrays.asList("b1", "b2", "b3");

            List<OkHttpBatchExecutor.BatchResult> results = smallExecutor.executeBatch(url, bodies);

            assertEquals(3, results.size());

            // 验证状态精确度
            long rejectedCount = results.stream()
                    .filter(r -> r.status() == OkHttpBatchExecutor.Status.REJECTED)
                    .count();

            assertTrue(rejectedCount > 0, "应该存在被拒绝的任务");

            // 验证具体的拒绝信息
            results.stream()
                    .filter(r -> r.status() == OkHttpBatchExecutor.Status.REJECTED)
                    .forEach(r -> {
                        assertEquals("b3", r.input());
                        assertEquals("Queue full", r.error());
                    });
        }
    }

    /**
     * 测试 HTTP 错误状态码
     */
    @Test
    public void testExecuteBatchHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Server Error"));

        String url = server.url("/error").toString();
        List<String> bodies = Collections.singletonList("{\"data\":\"bad\"}");

        List<OkHttpBatchExecutor.BatchResult> results = executor.executeBatch(url, bodies);

        assertEquals(1, results.size());
        assertEquals(OkHttpBatchExecutor.Status.FAILED, results.get(0).status());
        assertTrue(results.get(0).error().contains("500"));
    }

    /**
     * 测试 OkHttpClient 超时时间配置规范
     * 体现 connectTimeout (连接), readTimeout (读取), writeTimeout (写入) 的协作
     */
    @Test
    public void testTimeoutConfiguration() throws Exception {
        // 生产规范：明确设置三类超时，防止线程被无限期挂起
        OkHttpClient timeoutClient = new OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS) // TCP 握手超时
                .readTimeout(1, TimeUnit.SECONDS)    // 等待响应体数据超时
                .writeTimeout(1, TimeUnit.SECONDS)   // 发送请求体数据超时
                .build();

        try (OkHttpBatchExecutor timeoutExecutor = new OkHttpBatchExecutor(timeoutClient, 1, 1, 5)) {
            // 模拟服务器响应过慢：设置 2 秒延迟，超过客户端配置的 1 秒 readTimeout
            server.enqueue(new MockResponse()
                    .setBody("Slow response")
                    .setBodyDelay(2, TimeUnit.SECONDS));

            String url = server.url("/timeout").toString();
            List<String> bodies = Collections.singletonList("{\"data\":\"test\"}");

            List<OkHttpBatchExecutor.BatchResult> results = timeoutExecutor.executeBatch(url, bodies);

            assertEquals(1, results.size());
            OkHttpBatchExecutor.BatchResult result = results.get(0);

            // 验证状态应为 FAILED，且错误信息包含 timeout 关键字
            assertEquals(OkHttpBatchExecutor.Status.FAILED, result.status());
            assertTrue(result.error().toLowerCase().contains("timeout") || result.error().toLowerCase().contains("timed out"),
                    "应该捕获到超时异常，实际错误信息为: " + result.error());

        }
    }

    @Test
    public void testEmptyList() {
        List<OkHttpBatchExecutor.BatchResult> results = executor.executeBatch("http://localhost", Collections.emptyList());
        assertTrue(results.isEmpty());
    }
}
