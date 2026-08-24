package cn.net.pap.example.proguard;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 演示：服务端只支持 PORT(主动)模式时，固定客户端活动数据端口区间以配合防火墙入站放行。
 */
public class FtpActivePortRangeTest {

    private static final String FTP_HOST = "127.0.0.1";
    private static final int FTP_PORT = 21;
    private static final String FTP_USER = "bj";
    private static final String FTP_PASS = "123456";

    @Test
    public void testActiveModeWithFixedPortRange() throws IOException {
        // 服务端只支持 PORT(主动)模式时：enterLocalActiveMode() 开启主动模式，
        // setActivePortRange(min, max) 固定客户端活动数据端口区间，防火墙只需放行该区间。
        FTPClient client = new FTPClient();
        boolean connected = false;
        try {
            client.setControlEncoding("UTF-8");
            client.enterLocalActiveMode();
            client.setActivePortRange(30000, 30100);

            // 若客户端处于 NAT / 容器网络环境且服务端位于外网，还可配合设置外部映射 IP:
            // client.setActiveExternalIPAddress("xxx.xxx.xxx.xxx");

            try {
                client.connect(FTP_HOST, FTP_PORT);
                connected = client.login(FTP_USER, FTP_PASS);
            } catch (IOException e) {
                // FTP 服务未启动时跳过测试 (JUnit 5 报告中显示 SKIPPED 而非假 PASSED)
                Assumptions.abort("FTP 服务不可用，跳过测试: " + e.getMessage());
            }

            Assumptions.assumeTrue(connected, "FTP 登录失败，跳过测试");

            // 主动模式下列一次目录，验证带固定端口区间的连接可用
            FTPFile[] files = client.listFiles("/");
            assertNotNull(files, "主动模式 + 固定活动端口区间下列目录应成功");
        } finally {
            if (client.isConnected()) {
                try {
                    client.logout();
                } catch (IOException ignored) {
                }
                try {
                    client.disconnect();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
