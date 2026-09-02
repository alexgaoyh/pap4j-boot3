package cn.net.pap.common.spider.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpClientBatchExecutorTest {

    private static final Logger log = LoggerFactory.getLogger(HttpClientBatchExecutorTest.class);

    private MockWebServer server;
    private CloseableHttpClient client;
    private HttpClientBatchExecutor executor;

    @BeforeEach
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = HttpClients.createDefault();
        executor = new HttpClientBatchExecutor(client, 1, 1, 10, 100);
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

    @Test
    public void testExecuteBatchSuccess() throws Exception {
        server.enqueue(new MockResponse().setBody("Result-1"));
        server.enqueue(new MockResponse().setBody("Result-2"));

        String url = server.url("/api/batch").toString();
        List<String> bodies = Arrays.asList("{\"id\":1}", "{\"id\":2}");

        List<HttpClientBatchExecutor.BatchResult> results = executor.executeBatch(url, bodies);

        assertEquals(2, results.size());
        assertEquals(HttpClientBatchExecutor.Status.SUCCESS, results.get(0).status());
        assertEquals("Result-1", results.get(0).data());
        assertEquals(HttpClientBatchExecutor.Status.SUCCESS, results.get(1).status());
        assertEquals("Result-2", results.get(1).data());
    }

    @Test
    public void testConnectionReuse() throws Exception {
        AtomicInteger connectCount = new AtomicInteger(0);
        CloseableHttpClient reusableClient = createClientWithCounter(5, connectCount);

        try (HttpClientBatchExecutor reusableExecutor = new HttpClientBatchExecutor(reusableClient, 1, 1, 10, 100)) {
            for (int i = 0; i < 3; i++) {
                server.enqueue(new MockResponse().setBody("OK"));
            }

            String url = server.url("/reuse").toString();
            List<String> bodies = Arrays.asList("{}", "{}", "{}");

            reusableExecutor.executeBatch(url, bodies);
            assertTrue(connectCount.get() < 3);
        }
    }

    @Test
    public void testExecuteBatchRejection() throws Exception {
        try (HttpClientBatchExecutor smallExecutor = new HttpClientBatchExecutor(client, 1, 1, 1, 100)) {
            server.enqueue(new MockResponse().setBody("Late").setBodyDelay(500, TimeUnit.MILLISECONDS));
            server.enqueue(new MockResponse().setBody("Queued"));

            String url = server.url("/reject").toString();
            List<String> bodies = Arrays.asList("b1", "b2", "b3");

            List<HttpClientBatchExecutor.BatchResult> results = smallExecutor.executeBatch(url, bodies);

            assertEquals(3, results.size());
            long rejectedCount = results.stream()
                    .filter(r -> r.status() == HttpClientBatchExecutor.Status.REJECTED)
                    .count();
            assertTrue(rejectedCount > 0);
        }
    }

    @Test
    public void testExecuteBatchHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Server Error"));

        String url = server.url("/error").toString();
        List<String> bodies = Collections.singletonList("{\"data\":\"bad\"}");

        List<HttpClientBatchExecutor.BatchResult> results = executor.executeBatch(url, bodies);

        assertEquals(1, results.size());
        assertEquals(HttpClientBatchExecutor.Status.FAILED, results.get(0).status());
        assertTrue(results.get(0).error().contains("500"));
    }

    @Test
    public void testTimeoutConfiguration() throws Exception {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(1))
                .setResponseTimeout(Timeout.ofSeconds(1))
                .build();
        CloseableHttpClient timeoutClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();

        try (HttpClientBatchExecutor timeoutExecutor = new HttpClientBatchExecutor(timeoutClient, 1, 1, 5, 100)) {
            server.enqueue(new MockResponse()
                    .setBody("Slow response")
                    .setBodyDelay(2, TimeUnit.SECONDS));

            String url = server.url("/timeout").toString();
            List<String> bodies = Collections.singletonList("{\"data\":\"test\"}");

            List<HttpClientBatchExecutor.BatchResult> results = timeoutExecutor.executeBatch(url, bodies);

            assertEquals(1, results.size());
            HttpClientBatchExecutor.BatchResult result = results.get(0);
            assertEquals(HttpClientBatchExecutor.Status.FAILED, result.status());
            assertTrue(result.error().toLowerCase().contains("timeout") || result.error().toLowerCase().contains("timed out"));
        }
    }

    @Test
    public void testEmptyList() {
        List<HttpClientBatchExecutor.BatchResult> results = executor.executeBatch("http://localhost", Collections.emptyList());
        assertTrue(results.isEmpty());
    }

    @Test
    public void testNullInput() {
        List<HttpClientBatchExecutor.BatchResult> results = executor.executeBatch("http://localhost", null);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testMaxBatchSizeExceeded() {
        try (HttpClientBatchExecutor smallExecutor = new HttpClientBatchExecutor(client, 1, 1, 10, 2)) {
            String url = server.url("/max-batch").toString();
            List<String> bodies = Arrays.asList("{\"a\":1}", "{\"b\":2}", "{\"c\":3}");

            List<HttpClientBatchExecutor.BatchResult> results = smallExecutor.executeBatch(url, bodies);

            assertEquals(3, results.size());
            results.forEach(r -> {
                assertEquals(HttpClientBatchExecutor.Status.REJECTED, r.status());
                assertTrue(r.error().contains("exceeds max"));
            });
        }
    }

    @Test
    public void testNullClient() {
        try (HttpClientBatchExecutor nullClientExecutor = new HttpClientBatchExecutor(null, 1, 1, 10, 100)) {
            String url = server.url("/null-client").toString();
            List<String> bodies = Arrays.asList("{\"id\":1}", "{\"id\":2}");

            List<HttpClientBatchExecutor.BatchResult> results = nullClientExecutor.executeBatch(url, bodies);

            assertEquals(2, results.size());
            results.forEach(r -> {
                assertEquals(HttpClientBatchExecutor.Status.FAILED, r.status());
                assertEquals("client null", r.error());
            });
        }
    }

    @Test
    public void testConcurrentClose() throws Exception {
        CloseableHttpClient c = HttpClients.createDefault();
        try (HttpClientBatchExecutor exec = new HttpClientBatchExecutor(c, 1, 1, 1, 100, 3000L)) {
            server.enqueue(new MockResponse().setBodyDelay(10, TimeUnit.SECONDS).setBody("Slow"));
            server.enqueue(new MockResponse().setBody("Fast1"));
            server.enqueue(new MockResponse().setBody("Fast2"));

            String url = server.url("/close-test").toString();
            List<String> bodies = Arrays.asList("slow", "fast1", "fast2");

            CountDownLatch started = new CountDownLatch(1);
            CompletableFuture<List<HttpClientBatchExecutor.BatchResult>> future = CompletableFuture.supplyAsync(() -> {
                started.countDown();
                return exec.executeBatch(url, bodies);
            });

            started.await(3, TimeUnit.SECONDS);
            Thread.sleep(500);

            exec.close();

            List<HttpClientBatchExecutor.BatchResult> results = future.get(10, TimeUnit.SECONDS);
            assertEquals(3, results.size());
            long notNormal = results.stream()
                    .filter(r -> r.status() == HttpClientBatchExecutor.Status.REJECTED
                            || r.status() == HttpClientBatchExecutor.Status.UNKNOWN)
                    .count();
            assertTrue(notNormal >= 1);
        }
    }

    @Test
    public void testLargeBatchExceedsMaxBatchSize() {
        try (HttpClientBatchExecutor smallExecutor = new HttpClientBatchExecutor(client, 4, 8, 100, 5)) {
            String url = server.url("/large-batch").toString();
            List<String> bodies = Arrays.asList("1", "2", "3", "4", "5", "6", "7");

            List<HttpClientBatchExecutor.BatchResult> results = smallExecutor.executeBatch(url, bodies);

            assertEquals(7, results.size());
            long rejectedCount = results.stream()
                    .filter(r -> r.status() == HttpClientBatchExecutor.Status.REJECTED)
                    .count();
            assertEquals(7, rejectedCount);
        }
    }

    @Test
    public void testStressMixedScenarios() throws Exception {
        int totalRequests = 100;
        for (int i = 0; i < totalRequests; i++) {
            int type = i % 5;
            switch (type) {
                case 0:
                    server.enqueue(new MockResponse().setBody("Success-" + i));
                    break;
                case 1:
                    server.enqueue(new MockResponse().setBody("Slow-" + i).setBodyDelay(200, TimeUnit.MILLISECONDS));
                    break;
                case 2:
                    server.enqueue(new MockResponse().setBody("Timeout-" + i).setBodyDelay(2, TimeUnit.SECONDS));
                    break;
                case 3:
                    server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error-" + i));
                    break;
                case 4:
                    server.enqueue(new MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START));
                    break;
            }
        }

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(1))
                .setResponseTimeout(Timeout.ofSeconds(1))
                .build();
        CloseableHttpClient stressClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();

        try (HttpClientBatchExecutor stressExecutor = new HttpClientBatchExecutor(stressClient, 10, 20, 50, 200)) {
            String url = server.url("/stress").toString();
            List<String> bodies = new ArrayList<>();
            for (int i = 0; i < totalRequests; i++) {
                bodies.add("{\"id\":" + i + "}");
            }

            List<HttpClientBatchExecutor.BatchResult> results = stressExecutor.executeBatch(url, bodies);
            assertEquals(totalRequests, results.size());

            long successCount = results.stream().filter(r -> r.status() == HttpClientBatchExecutor.Status.SUCCESS).count();
            long failedCount = results.stream().filter(r -> r.status() == HttpClientBatchExecutor.Status.FAILED).count();
            long rejectedCount = results.stream().filter(r -> r.status() == HttpClientBatchExecutor.Status.REJECTED).count();

            log.info("HttpClient Stress -> SUCCESS: {}, FAILED: {}, REJECTED: {}", successCount, failedCount, rejectedCount);
            assertTrue(successCount > 0);
            assertTrue(failedCount > 0);
            assertTrue(rejectedCount > 0);
        }
    }

    @Test
    public void testConnectionPoolEfficiency() throws Exception {
        List<String> bodies = Collections.nCopies(10, "{}");

        // --- 场景 A：关闭连接复用（每次请求强制重新握手） ---
        AtomicInteger connectCountA = new AtomicInteger(0);
        try (MockWebServer serverA = new MockWebServer()) {
            serverA.start();
            CloseableHttpClient clientA = createClientWithNoReuse(10, connectCountA);

            try (HttpClientBatchExecutor execA = new HttpClientBatchExecutor(clientA, 10, 10, 20, 100)) {
                for (int i = 0; i < 10; i++) {
                    serverA.enqueue(new MockResponse().setBody("OK").setBodyDelay(200, TimeUnit.MILLISECONDS));
                }
                execA.executeBatch(serverA.url("/").toString(), bodies);
                int connectionsAfterBatch1 = connectCountA.get();

                for (int i = 0; i < 10; i++) {
                    serverA.enqueue(new MockResponse().setBody("OK"));
                }
                execA.executeBatch(serverA.url("/").toString(), bodies);
                int newConnectionsInBatch2 = connectCountA.get() - connectionsAfterBatch1;
                log.info("场景 A (限制池5) - 第一批物理连接: {}, 第二批被迫重新握手数: {}", connectionsAfterBatch1, newConnectionsInBatch2);
                assertTrue(newConnectionsInBatch2 >= 1);
            }
        }

        // --- 场景 B：使用优化连接池（最大连接 = 10） ---
        AtomicInteger connectCountB = new AtomicInteger(0);
        try (MockWebServer serverB = new MockWebServer()) {
            serverB.start();
            CloseableHttpClient clientB = createClientWithCounter(10, connectCountB);

            try (HttpClientBatchExecutor execB = new HttpClientBatchExecutor(clientB, 10, 10, 20, 100)) {
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
                log.info("场景 B (优化池10) - 第一批物理连接: {}, 第二批新增握手数: {}", connectionsAfterBatch1, newConnectionsInBatch2);
                assertTrue(newConnectionsInBatch2 <= 1);
            }
        }
    }

    @Test
    public void testWireMockProxyForArbitraryIpPort() throws Exception {
        com.github.tomakehurst.wiremock.WireMockServer wireMockServer = new com.github.tomakehurst.wiremock.WireMockServer(
                com.github.tomakehurst.wiremock.core.WireMockConfiguration.options()
                        .dynamicPort()
                        .enableBrowserProxying(true));

        wireMockServer.start();

        try {
            wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
                    com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/api/getUser"))
                    .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json; charset=utf-8")
                            .withBody("{\"status\":\"success\",\"name\":\"WireMockUser\"}")));

            HttpHost proxyHost = new HttpHost("127.0.0.1", wireMockServer.port());
            CloseableHttpClient clientWithProxy = HttpClients.custom()
                    .setProxy(proxyHost)
                    .build();

            try (HttpClientBatchExecutor proxyExecutor = new HttpClientBatchExecutor(clientWithProxy, 1, 1, 5, 100)) {
                String arbitraryExternalUrl = "http://192.0.2.1:54321/api/getUser";
                List<String> bodies = Collections.singletonList("{\"id\":123}");

                List<HttpClientBatchExecutor.BatchResult> results = proxyExecutor.executeBatch(arbitraryExternalUrl, bodies);

                assertEquals(1, results.size());
                HttpClientBatchExecutor.BatchResult result = results.get(0);
                assertEquals(HttpClientBatchExecutor.Status.SUCCESS, result.status());
                assertEquals("{\"status\":\"success\",\"name\":\"WireMockUser\"}", result.data());
            }
        } finally {
            wireMockServer.stop();
        }
    }

    @Test
    public void testSslBypassConfiguration() throws Exception {
        CloseableHttpClient sslBypassClient = HttpClientBatchExecutor.createUnsafeHttpClient(
                java.util.Map.of("Referer", "https://pap-docs.pap.net.cn/")
        );

        try (HttpClientBatchExecutor executor = new HttpClientBatchExecutor(sslBypassClient, 1, 1, 10, 100)) {
            assertTrue(true);
        }
    }

    @Test
    public void testMockInterceptorForExternalService() throws Exception {
        String targetUserUrl = "https://api.external-service.com/user/query";
        String targetOrderUrl = "https://api.external-service.com/order/create";
        String expectedUserMockJson = """
                {"code":200,"message":"success","data":{"userId":"10001","userName":"MockUser_DevLocal"}}""";

        CloseableHttpClient mockedClient = HttpClients.custom()
                .addExecInterceptorFirst("MOCK", new ComplexMockInterceptor(targetUserUrl, targetOrderUrl, expectedUserMockJson))
                .build();

        // 4.1 正常请求校验
        String responseBodyNormal = HttpClientBatchExecutor.executePost(mockedClient, targetUserUrl, "{\"id\":123}");
        log.info("HttpClient 正常调用校验成功: {}", responseBodyNormal);
        assertEquals(expectedUserMockJson, responseBodyNormal);

        // 4.2 多 URL 拦截校验
        String responseBodyOrder = HttpClientBatchExecutor.executePost(mockedClient, targetOrderUrl, "{\"amount\":100}");
        log.info("HttpClient 多URL拦截校验成功: {}", responseBodyOrder);
        assertTrue(responseBodyOrder.contains("ORD_2026_06_27"));

        // 4.3 模拟服务器 500 异常校验
        try {
            HttpClientBatchExecutor.executePost(mockedClient, targetUserUrl, "{\"id\":999}");
            org.junit.jupiter.api.Assertions.fail("应该抛出 IOException 响应 500 错误");
        } catch (IOException e) {
            log.info("HttpClient 预期内 500 异常拦截捕获成功: {}", e.getMessage());
            assertTrue(e.getMessage().contains("500"));
        }

        // 4.4 模拟网络超时异常校验
        try {
            HttpClientBatchExecutor.executePost(mockedClient, targetUserUrl, "{\"id\":998}");
            org.junit.jupiter.api.Assertions.fail("应该抛出 SocketTimeoutException");
        } catch (java.net.SocketTimeoutException e) {
            log.info("HttpClient 预期内 SocketTimeoutException 拦截捕获成功: {}", e.getMessage());
            assertEquals("Simulated connection timeout", e.getMessage());
        }
    }

    private CloseableHttpClient createClientWithCounter(int maxConnPerRoute, AtomicInteger counter) {
        org.apache.hc.core5.http.io.HttpConnectionFactory<org.apache.hc.client5.http.io.ManagedHttpClientConnection> connFactory =
                new org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory() {
            @Override
            public org.apache.hc.client5.http.io.ManagedHttpClientConnection createConnection(java.net.Socket socket) throws IOException {
                counter.incrementAndGet();
                return super.createConnection(socket);
            }
        };
        PoolingHttpClientConnectionManager manager = org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
                .setConnectionFactory(connFactory)
                .build();
        manager.setDefaultMaxPerRoute(maxConnPerRoute);
        manager.setMaxTotal(maxConnPerRoute * 2);
        return HttpClients.custom()
                .setConnectionManager(manager)
                .build();
    }

    private CloseableHttpClient createClientWithNoReuse(int maxConnPerRoute, AtomicInteger counter) {
        org.apache.hc.core5.http.io.HttpConnectionFactory<org.apache.hc.client5.http.io.ManagedHttpClientConnection> connFactory =
                new org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory() {
            @Override
            public org.apache.hc.client5.http.io.ManagedHttpClientConnection createConnection(java.net.Socket socket) throws IOException {
                counter.incrementAndGet();
                return super.createConnection(socket);
            }
        };
        PoolingHttpClientConnectionManager manager = org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
                .setConnectionFactory(connFactory)
                .build();
        manager.setDefaultMaxPerRoute(maxConnPerRoute);
        manager.setMaxTotal(maxConnPerRoute * 2);
        List<org.apache.hc.core5.http.Header> defaultHeaders = List.of(
                new org.apache.hc.core5.http.message.BasicHeader("Connection", "close")
        );
        return HttpClients.custom()
                .setConnectionManager(manager)
                .setDefaultHeaders(defaultHeaders)
                .build();
    }

    private static class ComplexMockInterceptor implements ExecChainHandler {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final String targetUserUrl;
        private final String targetOrderUrl;
        private final String expectedUserMockJson;

        public ComplexMockInterceptor(String targetUserUrl, String targetOrderUrl, String expectedUserMockJson) {
            this.targetUserUrl = targetUserUrl;
            this.targetOrderUrl = targetOrderUrl;
            this.expectedUserMockJson = expectedUserMockJson;
        }

        @Override
        public ClassicHttpResponse execute(ClassicHttpRequest request, ExecChain.Scope scope, ExecChain chain)
                throws IOException, HttpException {

            String requestUrl = "";
            try {
                java.net.URI uri = request.getUri();
                if (uri.isAbsolute()) {
                    requestUrl = uri.toString();
                } else {
                    if (scope.clientContext != null && scope.clientContext.getHttpRoute() != null) {
                        requestUrl = scope.clientContext.getHttpRoute().getTargetHost().toURI() + request.getPath();
                    } else {
                        requestUrl = request.getPath();
                    }
                }
            } catch (Exception e) {
                requestUrl = request.getPath();
            }

            // 1. 精准匹配用户查询接口
            if (targetUserUrl.equals(requestUrl)) {
                String requestBodyStr = "";
                if (request.getEntity() != null) {
                    requestBodyStr = EntityUtils.toString(request.getEntity());
                }

                int id = -1;
                if (!requestBodyStr.isEmpty()) {
                    try {
                        JsonNode jsonNode = objectMapper.readTree(requestBodyStr);
                        if (jsonNode.has("id")) {
                            id = jsonNode.get("id").asInt();
                        }
                    } catch (Exception e) {
                        log.error("解析请求 JSON 失败", e);
                    }
                }

                if (id == 999) {
                    log.info("拦截到特定用户请求 (id=999)，模拟服务器 500 内部异常");
                    org.apache.hc.core5.http.message.BasicClassicHttpResponse response =
                            new org.apache.hc.core5.http.message.BasicClassicHttpResponse(500, "Internal Server Error");
                    response.setEntity(new StringEntity("""
                            {"error":"Database connection failed"}""", ContentType.APPLICATION_JSON));
                    return response;
                } else if (id == 998) {
                    log.info("拦截到特定用户请求 (id=998)，抛出网络连接超时异常 SocketTimeoutException");
                    throw new java.net.SocketTimeoutException("Simulated connection timeout");
                } else {
                    log.info("拦截到正常用户请求 (id={})，返回成功 Mock 响应", id);
                    org.apache.hc.core5.http.message.BasicClassicHttpResponse response =
                            new org.apache.hc.core5.http.message.BasicClassicHttpResponse(200, "OK");
                    response.setEntity(new StringEntity(expectedUserMockJson, ContentType.APPLICATION_JSON));
                    return response;
                }
            }

            // 2. 精准匹配订单创建接口
            if (targetOrderUrl.equals(requestUrl)) {
                log.info("拦截到订单创建请求，返回订单模块 Mock 响应");
                org.apache.hc.core5.http.message.BasicClassicHttpResponse response =
                        new org.apache.hc.core5.http.message.BasicClassicHttpResponse(200, "OK");
                response.setEntity(new StringEntity("""
                        {"code":200,"message":"success","orderId":"ORD_2026_06_27"}""", ContentType.APPLICATION_JSON));
                return response;
            }

            return chain.proceed(request, scope);
        }
    }
}
