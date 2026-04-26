package cn.net.pap.common.webdav;

import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.pool.PoolStats;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class PoolingHttpClientConnectionManagerTest {

    private static final Logger logger = LoggerFactory.getLogger(PoolingHttpClientConnectionManagerTest.class);

    private static final int MAX_TOTAL_CONNECTIONS = 200;
    private static final int DEFAULT_MAX_PER_ROUTE = 100;

    private static final int CONNECTION_REQUEST_TIMEOUT = 120000;
    private static final int CONNECT_TIMEOUT = 120000;
    private static final int SOCKET_TIMEOUT = 120000;

    private static final PoolingHttpClientConnectionManager HTTP_CLIENT_CONNECTION_MANAGER;

    private static final RequestConfig REQUEST_CONFIG;

    private static final CloseableHttpClient DEFAULT_HTTP_CLIENT;

    static {
        HTTP_CLIENT_CONNECTION_MANAGER = new PoolingHttpClientConnectionManager();
        HTTP_CLIENT_CONNECTION_MANAGER.setMaxTotal(MAX_TOTAL_CONNECTIONS);
        HTTP_CLIENT_CONNECTION_MANAGER.setDefaultMaxPerRoute(DEFAULT_MAX_PER_ROUTE);
        HttpRoute specificRoute = new HttpRoute(new HttpHost("127.0.0.1", 8080));
        HTTP_CLIENT_CONNECTION_MANAGER.setMaxPerRoute(specificRoute, 256);

        REQUEST_CONFIG = RequestConfig.custom().setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT).setConnectTimeout(CONNECT_TIMEOUT).setSocketTimeout(SOCKET_TIMEOUT).build();

        DEFAULT_HTTP_CLIENT = HttpClients.custom().setConnectionManager(HTTP_CLIENT_CONNECTION_MANAGER).setDefaultRequestConfig(REQUEST_CONFIG).build();

        new IdleConnectionMonitorThread(HTTP_CLIENT_CONNECTION_MANAGER).start();
    }

    static class IdleConnectionMonitorThread extends Thread {

        private final HttpClientConnectionManager connMgr;
        private volatile boolean shutdown;

        public IdleConnectionMonitorThread(HttpClientConnectionManager connMgr) {
            super();
            this.connMgr = connMgr;
        }

        @Override
        public void run() {
            try {
                while (!shutdown) {
                    synchronized (this) {
                        wait(5000);
                        connMgr.closeExpiredConnections();
                        connMgr.closeIdleConnections(30, TimeUnit.SECONDS);
                    }
                }
            } catch (InterruptedException ex) {
            }
        }

        public void shutdown() {
            shutdown = true;
            synchronized (this) {
                notifyAll();
            }
        }

    }

    private CloseableHttpClient createHttpClient() {
        return HttpClients.custom().setConnectionManager(HTTP_CLIENT_CONNECTION_MANAGER).setDefaultRequestConfig(REQUEST_CONFIG).build();
    }

    /**
     * 获取 HttpClient 实例
     */
    public static CloseableHttpClient getInstance() {
        return DEFAULT_HTTP_CLIENT;
    }

    /**
     * 检查 HTTP_CLIENT 和连接池的状态
     */
    private static boolean checkHttpClientState() {
        if (DEFAULT_HTTP_CLIENT == null) {
            logger.error("HTTP_CLIENT 未初始化");
            return false;
        }
        if (HTTP_CLIENT_CONNECTION_MANAGER == null) {
            logger.error("连接池已关闭");
            return false;
        }
        return true;
    }


    /**
     * 发送 GET 请求并返回响应内容
     */
    private String sendGetRequest(String url) {
        try {
            boolean checked = checkHttpClientState();
            if (!checked) {
                logger.error("系统HTTP状态失败!");
            } else {
                // 检查连接池状态
                PoolStats poolStats = HTTP_CLIENT_CONNECTION_MANAGER.getTotalStats();
                logger.info("连接池状态: 空闲连接数={}, 持久连接数={}, 阻塞线程数={}", poolStats.getAvailable(), poolStats.getLeased(), poolStats.getPending());
                if (poolStats.getAvailable() == 0 && poolStats.getLeased() >= poolStats.getMax()) {
                    logger.error("连接池已耗尽，无法执行请求");
                } else {
                    CloseableHttpClient httpClient = DEFAULT_HTTP_CLIENT;
                    HttpGet httpGet = new HttpGet(url);
                    try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                        int statusCode = response.getStatusLine().getStatusCode();
                        org.apache.http.HttpEntity entity = response.getEntity();
                        if (statusCode == HttpStatus.SC_OK) {
                            return EntityUtils.toString(entity, "utf-8");
                        } else {
                            logger.error("请求失败，状态码: {}", statusCode);
                            if (entity != null) {
                                EntityUtils.consume(entity);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error("请求发生异常: {}", e.getMessage(), e);
        } finally {
        }
        return null;
    }

    private static HttpServer mockServer;
    private static final int PORT = 8080;

    @BeforeAll
    static void setUpServer() throws IOException {
        // 创建一个监听 PORT 端口的 HTTP 服务器
        mockServer = HttpServer.create(new InetSocketAddress(PORT), 0);

        // 配置路由和处理逻辑
        mockServer.createContext("/direct", exchange -> {
            // 使用文本块构建 JSON 响应
            String responseBody = """
                    direct
                    """;

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            // 注意：必须先调用 sendResponseHeaders，再写入 body
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        // 使用默认的执行器启动
        mockServer.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(100));
        mockServer.start();
    }

    @AfterAll
    static void tearDownServer() {
        if (mockServer != null) {
            // 参数为最大等待延迟秒数
            mockServer.stop(0);
        }
    }

    @Test
    public void getTest() {
        String url = "http://127.0.0.1:8080/direct";
        String response = sendGetRequest(url);
        if (response != null) {
            logger.info("请求成功，响应内容: {}", response);
        } else {
            //System.out.println(1);
            logger.error("请求失败，未获取到有效响应");
        }
    }

    @Test
    public void getBatchTest() {
        ExecutorService executor = new ThreadPoolExecutor(
                100,
                100,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(6000),
                r -> new Thread(r, "concurrent-soft-bound-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        try {
            IntStream.range(5000, 11000).forEach(index -> {
                executor.submit(() -> {
                    String url = "http://127.0.0.1:8080/direct?index=" + index;
                    String response = sendGetRequest(url);
                    if (response != null) {
                        logger.info("请求成功，响应内容: {}", response);
                    } else {
                        //System.out.println(1);
                        logger.error("请求失败，未获取到有效响应");
                    }
                });
            });
        } finally {
            // 关闭线程池
            executor.shutdown();
            try {
                // 等待 2 秒让未完成的任务结束
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    // 超时后强制关闭，这会向所有池中线程发送 Interrupt 信号
                    logger.warn("部分线程池任务未在 2 秒内结束，强制关闭");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.error("关闭线程池时被中断", e);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }


    }


}
