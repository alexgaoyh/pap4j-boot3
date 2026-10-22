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
    @DisplayName("测试 URL 探测 - 非法或空参数保护")
    void testIsUrlReachableInvalidArgs() {
        assertFalse(NetworkUtil.isUrlReachable(null));
        assertFalse(NetworkUtil.isUrlReachable(""));
        assertFalse(NetworkUtil.isUrlReachable("   "));
        assertFalse(NetworkUtil.isUrlReachable("not-a-valid-url"));
        assertFalse(NetworkUtil.isUrlReachable("http://127.0.0.1:65534/dummy", 200));
    }

}
