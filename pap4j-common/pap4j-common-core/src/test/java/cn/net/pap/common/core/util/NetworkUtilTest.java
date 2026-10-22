package cn.net.pap.common.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkUtilTest {

    @Test
    @DisplayName("测试端口探活 - 本地真实监听端口与未监听端口")
    void testIsPortOpen() throws IOException {
        int port;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            port = serverSocket.getLocalPort();
            assertTrue(NetworkUtil.isPortOpen("127.0.0.1", port));
            assertTrue(NetworkUtil.isPortOpen("127.0.0.1", port, 500));
        }

        // ServerSocket 关闭后，连接应该失败
        assertFalse(NetworkUtil.isPortOpen("127.0.0.1", port, 200));
    }

    @Test
    @DisplayName("测试端口探活 - 非法参数保护")
    void testIsPortOpenInvalidArgs() {
        assertFalse(NetworkUtil.isPortOpen(null, 80));
        assertFalse(NetworkUtil.isPortOpen("", 80));
        assertFalse(NetworkUtil.isPortOpen("  ", 80));
        assertFalse(NetworkUtil.isPortOpen("127.0.0.1", 0));
        assertFalse(NetworkUtil.isPortOpen("127.0.0.1", -1));
        assertFalse(NetworkUtil.isPortOpen("127.0.0.1", 70000));
    }

    @Test
    @DisplayName("测试 JDBC URL 探活 - 内存数据库与真实端口探测")
    void testIsJdbcUrlReachable() throws IOException {
        // 嵌入式内存数据库直接返回 true
        assertTrue(NetworkUtil.isJdbcUrlReachable("jdbc:h2:mem:testdb"));
        assertTrue(NetworkUtil.isJdbcUrlReachable("jdbc:sqlite::memory:"));

        // 本地开启临时服务套接字测试动态端口解析
        int port;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            port = serverSocket.getLocalPort();
            String mysqlUrl = "jdbc:mysql://127.0.0.1:" + port + "/mydb?useSSL=false";
            assertTrue(NetworkUtil.isJdbcUrlReachable(mysqlUrl));

            String pgUrl = "jdbc:postgresql://127.0.0.1:" + port + "/mydb";
            assertTrue(NetworkUtil.isJdbcUrlReachable(pgUrl, 500));
        }

        // 端口关闭后探测失败
        assertFalse(NetworkUtil.isJdbcUrlReachable("jdbc:mysql://127.0.0.1:" + port + "/mydb", 200));
    }

    @Test
    @DisplayName("测试 JDBC URL 探活 - 非法参数与解析容错")
    void testIsJdbcUrlReachableInvalidArgs() {
        assertFalse(NetworkUtil.isJdbcUrlReachable(null));
        assertFalse(NetworkUtil.isJdbcUrlReachable(""));
        assertFalse(NetworkUtil.isJdbcUrlReachable("   "));
        assertFalse(NetworkUtil.isJdbcUrlReachable("http://example.com"));
        assertFalse(NetworkUtil.isJdbcUrlReachable("jdbc:unknown:"));
    }

    @Test
    @DisplayName("测试 URL 探测 - 非法或空参数保护")
    void testIsUrlReachableInvalidArgs() {
        assertFalse(NetworkUtil.isUrlReachable(null));
        assertFalse(NetworkUtil.isUrlReachable(""));
        assertFalse(NetworkUtil.isUrlReachable("   "));
        assertFalse(NetworkUtil.isUrlReachable("not-a-valid-url"));
        assertFalse(NetworkUtil.isUrlReachable("http://127.0.0.1:65534/dummy", 200));
    }

}
