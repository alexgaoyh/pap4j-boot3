package cn.net.pap.example.proguard;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 演示：服务端只支持 PORT(主动)模式时，固定客户端活动数据端口区间以配合防火墙入站放行。
 *
 * <p>注意：本测试是仓库中唯一允许使用 FTP 主动模式（enterLocalActiveMode）的代码，仅用于演示
 * "服务端只支持主动模式 + 固定活动端口区间"的配置。生产代码必须使用被动模式（enterLocalPassiveMode），
 * 由根目录 checkstyle.xml 的三条规则强制：FTPClient-ActiveMode-Forbidden（禁止主动模式）、
 * FTPClient-Must-Use-Passive-Mode（强制被动模式）、FTPClient-Direct-Instantiation-Forbidden（禁止直接 new FTPClient）。
 * <p>本测试位于 src/test 目录，而 maven-checkstyle-plugin 默认只扫描 main 源码
 * （includeTestSourceDirectory=false），因此不受这三条规则约束。若未来开启测试源码扫描
 * （includeTestSourceDirectory=true），需将以下豁免加回根目录 checkstyle.xml：</p>
 * <pre>{@code
 * <!-- 唯一允许主动模式的例外：演示"服务端只支持 PORT(主动)模式 + 固定活动端口区间"的测试 -->
 *     <module name="SuppressionSingleFilter">
 *         <property name="id" value="FTPClient-ActiveMode-Forbidden"/>
 *         <property name="files" value="FtpActivePortRangeTest\.java"/>
 *     </module>
 *     <module name="SuppressionSingleFilter">
 *         <property name="id" value="FTPClient-Must-Use-Passive-Mode"/>
 *         <property name="files" value="FtpActivePortRangeTest\.java"/>
 *     </module>
 *     <module name="SuppressionSingleFilter">
 *         <property name="id" value="FTPClient-Direct-Instantiation-Forbidden"/>
 *         <property name="files" value="FtpActivePortRangeTest\.java"/>
 *     </module>
 * }</pre>
 *
 * <h2>运行所需配置</h2>
 * <p>本测试不独立可用，需配套启动 {@code pap4j-boot3-example-ftp-server} 模块，其
 * {@code application.properties} 与本测试常量保持一致：</p>
 * <pre>
 *   ftp.port=21
 *   ftp.users[0].username=bj
 *   ftp.users[0].password=123456
 *   ftp.users[0].homeDirectory=d:/knowledge
 * </pre>
 * <p>主动模式的数据连接是"服务端主动回连客户端"，因此客户端本机防火墙还需放行
 * 活动数据端口区间 {@code 30000~30100} 的入站连接（即下方 ACTIVE_PORT_RANGE_MIN/MAX）；
 * 若客户端处于 NAT / 容器网络且服务端在外网，还要把 EXTERNAL_IP 配成客户端对外可见的 IP，
 * 否则 PORT 命令携带的本地 IP 服务端无法回连。</p>
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
