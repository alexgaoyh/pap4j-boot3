package cn.net.pap.example.websocket;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.server.ServerEndpoint;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * WebSocket 缓冲区配置内存占用对比测试
 */
public class WebSocketBufferMemoryCompareTest {

    // ==========================================
    // 1. 模拟的 Spring Boot 应用和端点配置
    // ==========================================

    @SpringBootApplication
    public static class WsTestApplication {

        // 用于将 @ServerEndpoint 注解的类注册到底层WebSocket容器（Tomcat/Jetty/Undertow）
        @Bean
        public ServerEndpointExporter serverEndpointExporter() {
            return new ServerEndpointExporter();
        }

        // 用于配置WebSocket容器的参数
        @Bean
        public ServletServerContainerFactoryBean createWebSocketContainer(@Value("${ws.buffer.size}") int bufferSize) {
            ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
            // 核心：动态读取配置来设置 Buffer 大小
            // 设置文本消息缓冲区大小 文本缓冲区因 char 数组会翻倍
            // 如果是 tomcat 的话，在 WsFrameBase 源码中，可以清晰地看到文本缓冲区翻倍的效果。关键代码位于构造函数中：
            //   messageBufferText = CharBuffer.allocate(wsSession.getMaxTextMessageBufferSize());
            //   messageBufferBinary = ByteBuffer.allocate(wsSession.getMaxBinaryMessageBufferSize());
            container.setMaxTextMessageBufferSize(bufferSize);
            // 设置二进制消息缓冲区大小
            container.setMaxBinaryMessageBufferSize(bufferSize);
            return container;
        }
    }

    // 定义 WebSocket 服务端路径，客户端可以连接到ws://localhost:port/ws/memory-test,使用注解标记WebSocket的生命周期方法,实现简单的回声功能
    @ServerEndpoint("/ws/memory-test")
    public static class TestServerEndpoint {
        // 当客户端连接时触发
        @OnOpen
        public void onOpen(Session session) {
            // 连接建立，此时底层 Tomcat 已经为 session 分配了 Buffer
        }

        // 当收到消息时触发 回声服务：将收到的消息返回给客户端
        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }

    // 标记这是一个WebSocket客户端
    @ClientEndpoint
    public static class TestClientEndpoint {
        // 客户端连接建立时的回调
        @OnOpen
        public void onOpen(Session session) {
        }
    }

    // ==========================================
    // 2. 核心测试逻辑：建立连接并计算内存差值
    // ==========================================

    static void executeMemoryTest(int port, String scenarioName) throws Exception {
        int connectionCount = 50; // 测试连接数
        Runtime runtime = Runtime.getRuntime();

        // 测前：强制 GC 并记录初始内存
        System.gc();
        Thread.sleep(1000);
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // 建立 WebSocket 连接
        List<Session> sessions = new ArrayList<>();
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();

        System.out.println("正在为场景 [" + scenarioName + "] 建立 " + connectionCount + " 个连接...");
        for (int i = 0; i < connectionCount; i++) {
            URI uri = URI.create("ws://localhost:" + port + "/ws/memory-test");
            // 这是真正建立连接的地方：
            // - 创建一个TestClientEndpoint实例 - 向服务器发起WebSocket握手 - 握手成功后，服务器为这个连接创建Session和缓冲区 - 返回代表这个连接的Session对象
            sessions.add(container.connectToServer(TestClientEndpoint.class, uri));
        }

        // 测后：等待内存分配完毕，强制 GC 掉中间垃圾，记录当前内存
        Thread.sleep(1000);
        System.gc();
        Thread.sleep(1000);
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

        long usedMegabytes = Math.max(0, (memoryAfter - memoryBefore) / (1024 * 1024));

        System.out.println("\n==================================================");
        System.out.printf("测试场景: %s\n", scenarioName);
        System.out.printf("连接数量: %d\n", connectionCount);
        System.out.printf("堆内存净增长: ~ %d MB\n", usedMegabytes);
        System.out.printf("单连接平均预占: ~ %.2f MB\n", (double) usedMegabytes / connectionCount);
        System.out.println("==================================================\n");

        // 清理现场
        for (Session session : sessions) {
            session.close();
        }
    }

    // ==========================================
    // 3. 运行环境一：小 Buffer (8KB) 使用@Nested组织不同配置下的测试场景，每个嵌套类都有自己的Spring Boot上下文配置。
    // ==========================================

    @Nested
    @SpringBootTest(classes = {WsTestApplication.class, TestServerEndpoint.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ws.buffer.size=8192" // 8KB
    )
    class SmallBufferTest {
        @LocalServerPort
        private int port;

        @Test
        void testSmallBufferMemory() throws Exception {
            executeMemoryTest(port, "正常 Buffer 配置 (8KB)");
        }
    }

    // ==========================================
    // 4. 运行环境二：大 Buffer (10MB) 使用@Nested组织不同配置下的测试场景，每个嵌套类都有自己的Spring Boot上下文配置。
    // ==========================================

    @Nested
    @SpringBootTest(classes = {WsTestApplication.class, TestServerEndpoint.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ws.buffer.size=10485760" // 10MB
    )
    class LargeBufferTest {
        @LocalServerPort
        private int port;

        @Test
        void testLargeBufferMemory() throws Exception {
            executeMemoryTest(port, "超大 Buffer 配置 (10MB)");
        }
    }
}