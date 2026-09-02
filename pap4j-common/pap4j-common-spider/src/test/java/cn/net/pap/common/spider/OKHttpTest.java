package cn.net.pap.common.spider;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class OKHttpTest {

    private static final Logger log = LoggerFactory.getLogger(OKHttpTest.class);

    /**
     * 在对 HTTP 请求结果进行处理时，如果先读取了一次响应体（例如为了打印日志），紧接着再次读取该响应体（例如交由业务逻辑解析），程序会在第二次读取时抛出异常。  相关单元测试详见：
     */
    @Test
    public void verifyClosedExceptionWithRealNetwork() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isUrlReachable("https://www.baidu.com"), "Baidu is not reachable. Skipping test.");
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("https://www.baidu.com").build();

        try (Response response = client.newCall(request).execute()) { // 使用 try-with-resources 确保资源关闭
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            try {
                // 第一次读取：流会被消耗并关闭
                String logContent = response.body().string();
                log.info("第一次读取成功 (模拟日志)，内容长度: {}", logContent.length());
            } catch (Exception e) {
                log.error("内层捕获到了异常: ", e);
            }

            log.info("准备进行第二次读取 (模拟业务处理)...");

            // 第二次读取：OkHttp 的 ResponseBody 会抛出 "java.lang.IllegalStateException: closed"
            String businessData = response.body().string();
            log.info("第二次读取成功: {}", businessData);

        } catch (Exception e) {
            log.error("最外层捕获到异常: ", e);
        }
    }

    private static boolean isUrlReachable(String urlStr) {
        try {
            java.net.URL url = new java.net.URL(urlStr);
            String host = url.getHost();
            int port = url.getPort() != -1 ? url.getPort() : (url.getProtocol().equalsIgnoreCase("https") ? 443 : 80);
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(host, port), 1500);
                return true;
            }
        } catch (Exception e) {
            log.error("检查 URL 可达性失败: ", e);
            return false;
        }
    }
}