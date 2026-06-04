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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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
        // 核心线程设为1，便于在测试中模拟排队或复用；maxBatchSize=100 以免干扰现有用例
        executor = new OkHttpBatchExecutor(client, 1, 1, 10, 100);
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

        try (OkHttpBatchExecutor reusableExecutor = new OkHttpBatchExecutor(reusableClient, 1, 1, 10, 100)) {
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
        try (OkHttpBatchExecutor smallExecutor = new OkHttpBatchExecutor(client, 1, 1, 1, 100)) {
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

        try (OkHttpBatchExecutor timeoutExecutor = new OkHttpBatchExecutor(timeoutClient, 1, 1, 5, 100)) {
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

    /**
     * 测试空列表输入
     */
    @Test
    public void testEmptyList() {
        List<OkHttpBatchExecutor.BatchResult> results = executor.executeBatch("http://localhost", Collections.emptyList());
        assertTrue(results.isEmpty());
    }

    /**
     * 测试 null 输入（应等同空列表）
     */
    @Test
    public void testNullInput() {
        List<OkHttpBatchExecutor.BatchResult> results = executor.executeBatch("http://localhost", null);
        assertTrue(results.isEmpty());
    }

    /**
     * 测试 maxBatchSize 限制：超出上限时全部返回 REJECTED
     */
    @Test
    public void testMaxBatchSizeExceeded() {
        // maxBatchSize=2，传入 3 个请求
        try (OkHttpBatchExecutor smallExecutor = new OkHttpBatchExecutor(client, 1, 1, 10, 2)) {
            String url = server.url("/max-batch").toString();
            List<String> bodies = Arrays.asList("{\"a\":1}", "{\"b\":2}", "{\"c\":3}");

            List<OkHttpBatchExecutor.BatchResult> results = smallExecutor.executeBatch(url, bodies);

            // 结果长度与输入一致
            assertEquals(3, results.size());

            // 全部被拒绝
            results.forEach(r -> {
                assertEquals(OkHttpBatchExecutor.Status.REJECTED, r.status(),
                        "input=" + r.input() + " 应为 REJECTED");
                assertTrue(r.error().contains("exceeds max"),
                        "错误信息应包含 exceeds max，实际: " + r.error());
            });

            // 确认没有任何请求发到服务端
            assertEquals(0, server.getRequestCount());
        }
    }

    /**
     * 测试 OkHttpClient 为 null 时的保护
     */
    @Test
    public void testNullClient() {
        try (OkHttpBatchExecutor nullClientExecutor = new OkHttpBatchExecutor(null, 1, 1, 10, 100)) {
            String url = server.url("/null-client").toString();
            List<String> bodies = Arrays.asList("{\"id\":1}", "{\"id\":2}");

            List<OkHttpBatchExecutor.BatchResult> results = nullClientExecutor.executeBatch(url, bodies);

            // 结果长度与输入一致
            assertEquals(2, results.size());
            results.forEach(r -> {
                assertEquals(OkHttpBatchExecutor.Status.FAILED, r.status());
                assertEquals("client null", r.error());
            });

            // 确认没有任何请求发到服务端
            assertEquals(0, server.getRequestCount());
        }
    }

    /**
     * 测试 executeBatch 与 close() 并发调用时的安全性：
     * 确保结果列表长度始终与输入一致，且不会无限阻塞
     */
    @Test
    public void testConcurrentClose() throws Exception {
        OkHttpClient c = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();

        // 使用较短的 join 超时（3 秒），避免测试等待太久
        // 线程池：1 线程 + 1 队列位，确保第三个请求被拒绝
        try (OkHttpBatchExecutor exec = new OkHttpBatchExecutor(c, 1, 1, 1, 100, 3000L)) {
            // 第一个请求慢（10 秒），占住唯一线程
            server.enqueue(new MockResponse().setBodyDelay(10, TimeUnit.SECONDS).setBody("Slow"));
            // 第二个请求进入队列，但永远不会被执行
            server.enqueue(new MockResponse().setBody("Fast1"));
            // 第三个请求会被拒绝（队列满）
            server.enqueue(new MockResponse().setBody("Fast2"));

            String url = server.url("/close-test").toString();
            List<String> bodies = Arrays.asList("slow", "fast1", "fast2");

            CountDownLatch started = new CountDownLatch(1);

            // 异步执行 batch
            CompletableFuture<List<OkHttpBatchExecutor.BatchResult>> future = CompletableFuture.supplyAsync(() -> {
                started.countDown();
                return exec.executeBatch(url, bodies);
            });

            // 等待 executeBatch 进入（此时所有任务已提交）
            started.await(3, TimeUnit.SECONDS);
            // 给 executeBatch 一点时间完成 submit 阶段
            Thread.sleep(500);

            // 并发关闭执行器
            exec.close();

            // 验证所有输入都有结果，且不会永久阻塞（最多等 10 秒）
            List<OkHttpBatchExecutor.BatchResult> results = future.get(10, TimeUnit.SECONDS);
            assertEquals(3, results.size());

            // 至少应有一条不是正常 SUCCESS 的（REJECTED 或 UNKNOWN）
            long notNormal = results.stream()
                    .filter(r -> r.status() == OkHttpBatchExecutor.Status.REJECTED
                            || r.status() == OkHttpBatchExecutor.Status.UNKNOWN)
                    .count();
            assertTrue(notNormal >= 1,
                    "close 后应至少有一个未被正常执行的请求: " + results);

            log.info("Concurrent close test results: {}", results);
        }
    }

    /**
     * 大量请求时 maxBatchSize 生效且结果数正确的冒烟
     */
    @Test
    public void testLargeBatchExceedsMaxBatchSize() {
        try (OkHttpBatchExecutor smallExecutor = new OkHttpBatchExecutor(client, 4, 8, 100, 5)) {
            String url = server.url("/large-batch").toString();
            List<String> bodies = Arrays.asList("1", "2", "3", "4", "5", "6", "7");

            List<OkHttpBatchExecutor.BatchResult> results = smallExecutor.executeBatch(url, bodies);

            assertEquals(7, results.size());
            long rejectedCount = results.stream()
                    .filter(r -> r.status() == OkHttpBatchExecutor.Status.REJECTED)
                    .count();
            assertEquals(7, rejectedCount, "超过 maxBatchSize 应全部拒绝");
            assertEquals(0, server.getRequestCount());
        }
    }
}
