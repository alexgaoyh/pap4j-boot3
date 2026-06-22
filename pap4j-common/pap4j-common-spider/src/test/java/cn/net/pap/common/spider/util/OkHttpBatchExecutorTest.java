package cn.net.pap.common.spider.util;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.Proxy;
import java.net.InetSocketAddress;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

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
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

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

    /**
     * 压力测试：模拟混合场景
     * 包含：正常、延迟、超时、服务器错误、连接中断、队列拒绝
     */
    @Test
    public void testStressMixedScenarios() throws Exception {
        int totalRequests = 100;
        // 构造混合响应
        for (int i = 0; i < totalRequests; i++) {
            int type = i % 5;
            switch (type) {
                case 0: // 正常成功
                    server.enqueue(new MockResponse().setBody("Success-" + i));
                    break;
                case 1: // 正常但有一定延迟
                    server.enqueue(new MockResponse().setBody("Slow-" + i).setBodyDelay(200, TimeUnit.MILLISECONDS));
                    break;
                case 2: // 读取超时（设置延迟超过客户端 readTimeout）
                    server.enqueue(new MockResponse().setBody("Timeout-" + i).setBodyDelay(2, TimeUnit.SECONDS));
                    break;
                case 3: // 服务器 500 错误
                    server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error-" + i));
                    break;
                case 4: // 连接直接中断
                    server.enqueue(new MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START));
                    break;
            }
        }

        // 配置客户端：1秒读取超时
        OkHttpClient stressClient = new OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .writeTimeout(1, TimeUnit.SECONDS)
                .build();

        // 配置执行器：核心 10，最大 20，队列 50，总容量 70。
        // 100 个请求中预计会有部分被直接 REJECTED。
        try (OkHttpBatchExecutor stressExecutor = new OkHttpBatchExecutor(stressClient, 10, 20, 50, 200)) {
            String url = server.url("/stress").toString();
            List<String> bodies = new java.util.ArrayList<>();
            for (int i = 0; i < totalRequests; i++) {
                bodies.add("{\"id\":" + i + "}");
            }

            long start = System.currentTimeMillis();
            List<OkHttpBatchExecutor.BatchResult> results = stressExecutor.executeBatch(url, bodies);
            long end = System.currentTimeMillis();

            log.info("Stress Test Finished in {}ms for {} requests", end - start, totalRequests);

            // 1. 验证结果数量
            assertEquals(totalRequests, results.size());

            // 2. 统计状态
            long successCount = results.stream().filter(r -> r.status() == OkHttpBatchExecutor.Status.SUCCESS).count();
            long failedCount = results.stream().filter(r -> r.status() == OkHttpBatchExecutor.Status.FAILED).count();
            long rejectedCount = results.stream().filter(r -> r.status() == OkHttpBatchExecutor.Status.REJECTED).count();

            log.info("Results -> SUCCESS: {}, FAILED: {}, REJECTED: {}",
                    successCount, failedCount, rejectedCount);

            // 调试：打印前 20 个结果的详细信息
            for (int i = 0; i < Math.min(results.size(), 20); i++) {
                log.info("Result {}: status={}, error={}", i, results.get(i).status(), results.get(i).error());
            }

            // 3. 基本断言
            // case 0, 1 应有成功 为什么 SUCCESS 偏多？：是因为在高并发下，请求顺序乱了，且 OkHttp 的重试机制让某些本来该失败的请求，通过消耗掉服务器队列后方的成功响应，最终变为了成功。
            assertTrue(successCount > 0, "应有成功的请求");
            // case 2, 3, 4 应有失败
            assertTrue(failedCount > 0, "应有失败的请求");
            // 100 请求 > 70 容量，在高并发提交下必有部分被拒绝（除非执行太快）
            // 考虑到 200ms 的延迟，拒绝几乎是肯定的
            assertTrue(rejectedCount > 0, "应有被拒绝的请求");
        }
    }

    /**
     * 深度演示：连接池大小对“唯一URL”高并发请求的影响
     * 使用 BodyDelay 强制并发，确保 10 个线程必须同时持有 10 个物理连接
     */
    @Test
    public void testConnectionPoolEfficiency() throws Exception {
        List<String> bodies = Collections.nCopies(10, "{}");

        // --- 场景 A：使用默认连接池（最大空闲 = 5） ---
        AtomicInteger connectCountA = new AtomicInteger(0);
        try (MockWebServer serverA = new MockWebServer()) {
            serverA.start();
            OkHttpClient clientA = new OkHttpClient.Builder()
                    .protocols(Arrays.asList(Protocol.HTTP_1_1))
                    .eventListener(new okhttp3.EventListener() {
                        @Override
                        public void connectStart(okhttp3.Call call, java.net.InetSocketAddress addr, java.net.Proxy p) {
                            connectCountA.incrementAndGet();
                        }
                    }).build();

            try (OkHttpBatchExecutor execA = new OkHttpBatchExecutor(clientA, 10, 10, 20, 100)) {
                // 第一批：设置延迟，强制所有线程同时开线
                for (int i = 0; i < 10; i++) {
                    serverA.enqueue(new MockResponse().setBody("OK").setBodyDelay(200, TimeUnit.MILLISECONDS));
                }
                execA.executeBatch(serverA.url("/").toString(), bodies);
                int connectionsAfterBatch1 = connectCountA.get();

                // 第二批：无延迟执行
                for (int i = 0; i < 10; i++) {
                    serverA.enqueue(new MockResponse().setBody("OK"));
                }
                execA.executeBatch(serverA.url("/").toString(), bodies);
                int newConnectionsInBatch2 = connectCountA.get() - connectionsAfterBatch1;
                System.out.println("场景 A (默认池) - 第一批物理连接: " + connectionsAfterBatch1 + ", 第二批【被迫重新握手】数: " + newConnectionsInBatch2);
                // 默认池只能留 5 个
                assertTrue(newConnectionsInBatch2 >= 1, "默认池应该因为空闲位不足而产生大量重新握手");
            }
        }

        // --- 场景 B：使用优化连接池（最大空闲 = 10） ---
        AtomicInteger connectCountB = new AtomicInteger(0);
        try (MockWebServer serverB = new MockWebServer()) {
            serverB.start();
            ConnectionPool poolB = new ConnectionPool(10, 5, TimeUnit.MINUTES);
            OkHttpClient clientB = new OkHttpClient.Builder()
                    .protocols(Arrays.asList(Protocol.HTTP_1_1))
                    .connectionPool(poolB)
                    .eventListener(new okhttp3.EventListener() {
                        @Override
                        public void connectStart(okhttp3.Call call, java.net.InetSocketAddress addr, java.net.Proxy p) {
                            connectCountB.incrementAndGet();
                        }
                    }).build();

            try (OkHttpBatchExecutor execB = new OkHttpBatchExecutor(clientB, 10, 10, 20, 100)) {
                for (int i = 0; i < 10; i++) {
                    serverB.enqueue(new MockResponse().setBody("OK").setBodyDelay(200, TimeUnit.MILLISECONDS));
                }
                execB.executeBatch(serverB.url("/").toString(), bodies);
                int connectionsAfterBatch1 = connectCountB.get();

                for (int i = 0; i < 10; i++) {
                    serverB.enqueue(new MockResponse().setBody("OK"));
                }
                execB.executeBatch(serverB.url("/").toString(), bodies);

                int newConnectionsInBatch2 = connectCountB.get() - connectionsAfterBatch1;
                System.out.println("场景 B (优化池) - 第一批物理连接: " + connectionsAfterBatch1 + ", 第二批【新增握手】数: " + newConnectionsInBatch2);
                // 优化池保留了全部连接，第二批应该几乎 0 新增
                assertTrue(newConnectionsInBatch2 <= 1, "优化池应该实现连接完美复用，几乎不产生新握手");
            }
        }
    }

    /**
     * 测试通过 WireMock 开启浏览器代理模式 (enableBrowserProxying = true)。
     * 当 OkHttpClient 配置该代理后，发往外部任意 IP:Port 的请求（如 192.0.2.1:54321） 均会自动被代理路由至本地 WireMock，实现无需修改业务端 API 地址的物理拦截。
     */
    @Test
    public void testWireMockProxyForArbitraryIpPort() throws Exception {
        // 1. 初始化并启动配置了浏览器代理的 WireMock 实例
        WireMockServer wireMockServer = new WireMockServer(options()
                .dynamicPort()
                .enableBrowserProxying(true));

        wireMockServer.start();

        try {
            // 2. 为目标路由注册 Stub 定义：匹配任意 Host/IP/Port，只要请求的 Path 匹配即可返回定义的值
            wireMockServer.stubFor(post(urlEqualTo("/api/getUser"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json; charset=utf-8")
                            .withBody("{\"status\":\"success\",\"name\":\"WireMockUser\"}")));

            // 3. 构建配置了 WireMock 为代理服务器的 OkHttpClient
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", wireMockServer.port()));
            OkHttpClient clientWithProxy = new OkHttpClient.Builder()
                    .proxy(proxy)
                    .build();

            // 4. 将配置好代理的客户端注入批处理器，发起针对外部随机 IP 和端口的请求
            try (OkHttpBatchExecutor proxyExecutor = new OkHttpBatchExecutor(clientWithProxy, 1, 1, 5, 100)) {
                String arbitraryExternalUrl = "http://192.0.2.1:54321/api/getUser";
                List<String> bodies = Collections.singletonList("{\"id\":123}");

                List<OkHttpBatchExecutor.BatchResult> results = proxyExecutor.executeBatch(arbitraryExternalUrl, bodies);

                // 5. 校验返回结果是否符合 Mock 定义
                assertEquals(1, results.size());
                OkHttpBatchExecutor.BatchResult result = results.get(0);
                assertEquals(OkHttpBatchExecutor.Status.SUCCESS, result.status());
                assertEquals("{\"status\":\"success\",\"name\":\"WireMockUser\"}", result.data());
            }
        } finally {
            // 6. 销毁并关闭本地 WireMock 服务
            wireMockServer.stop();
        }
    }

    /**
     * 技术示范：展示如何构造一个忽略 SSL 校验的 OkHttpClient 并与 OkHttpBatchExecutor 配合使用。
     * 本方法仅做初始化与调用语法示范，不发起真实网络请求。
     */
    @Test
    public void testSslBypassConfiguration() throws Exception {
        // 1. 构造一个信任所有证书的 TrustManager
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{};
                    }
                }
        };

        // 2. 初始化 SSLContext 并产生 SSLSocketFactory
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        // 3. 将 SSL 配置及 HostnameVerifier 注入 OkHttpClient Builder
        OkHttpClient sslBypassClient = new OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true) // 忽略域名校验不匹配问题
                .build();

        // 4. 将配置好的客户端注入批量执行器
        try (OkHttpBatchExecutor executor = new OkHttpBatchExecutor(sslBypassClient, 1, 1, 10, 100)) {
            // 此处即可安全发起针对自签名证书等不可信 HTTPS 站点的批量请求
            // List<OkHttpBatchExecutor.BatchResult> results = executor.executeBatch("https://untrusted-ssl-site.com/api", List.of("{}"));
            // 语法验证断言
            assertTrue(true);
        }
    }

}
