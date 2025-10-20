package cn.net.pap.example.javafx.h2;

import org.h2.tools.Server;

import java.sql.SQLException;

public class H2ServerManager {

    private static Server tcpServer;

    private static Server webServer;

    private static int tcpPort = 9092;

    private static int webPort = 8082;

    private static String dbPath = "jdbc:h2:file:./h2db;DB_CLOSE_ON_EXIT=FALSE;AUTO_RECONNECT=TRUE";

    private static final int MAX_PORT_ATTEMPTS = 10;

    public static void startH2Servers() throws SQLException {

        // 启动 TCP 服务器（自动处理端口占用）
        tcpServer = startTcpServer();

        // 启动 Web 控制台（自动处理端口占用）
        webServer = startWebServer();

        System.out.println("H2 TCP Server started on port: " + tcpServer.getPort());
        System.out.println("H2 Web Console started on port: " + webServer.getPort());
        System.out.println("管理界面URL: http://localhost:" + webServer.getPort());
    }

    private static Server startTcpServer() throws SQLException {
        SQLException lastException = null;

        for (int i = 0; i < MAX_PORT_ATTEMPTS; i++) {
            try {
                System.out.println("尝试启动 TCP 服务器，端口: " + tcpPort);
                return Server.createTcpServer("-tcpPort", String.valueOf(tcpPort), "-tcpAllowOthers", "-ifNotExists").start();
            } catch (SQLException e) {
                if (e.getMessage().contains("Port is already in use")) {
                    System.out.println("端口 " + tcpPort + " 被占用，尝试下一个端口...");
                    lastException = e;
                    tcpPort++; // 端口递增
                } else {
                    // 其他异常直接抛出
                    throw e;
                }
            }
        }

        // 所有端口尝试都失败
        throw new SQLException("无法启动 TCP 服务器，尝试了 " + MAX_PORT_ATTEMPTS + " 个端口(" + (tcpPort - MAX_PORT_ATTEMPTS) + "-" + (tcpPort - 1) + ")，全部被占用", lastException);
    }

    private static Server startWebServer() throws SQLException {
        SQLException lastException = null;

        for (int i = 0; i < MAX_PORT_ATTEMPTS; i++) {
            try {
                System.out.println("尝试启动 Web 控制台，端口: " + webPort);
                return Server.createWebServer("-web", "-webPort", String.valueOf(webPort), "-webAllowOthers", "-ifNotExists").start();
            } catch (SQLException e) {
                if (e.getMessage().contains("Port is already in use")) {
                    System.out.println("端口 " + webPort + " 被占用，尝试下一个端口...");
                    lastException = e;
                    webPort++; // 端口递增
                } else {
                    // 其他异常直接抛出
                    throw e;
                }
            }
        }

        // 所有端口尝试都失败
        throw new SQLException("无法启动 Web 控制台，尝试了 " + MAX_PORT_ATTEMPTS + " 个端口(" + (webPort - MAX_PORT_ATTEMPTS) + "-" + (webPort - 1) + ")，全部被占用", lastException);
    }

    // 获取当前使用的端口信息
    public static String getConnectionInfo() {
        if (tcpServer == null || webServer == null) {
            return "服务器未启动";
        }

        return String.format("""
                        H2 数据库服务器运行信息:
                        TCP 服务器端口: %d
                        Web 管理界面: http://localhost:%d
                        数据库连接URL: jdbc:h2:tcp://localhost:%d/%s
                        用户名: sa
                        密码: (空)
                        """,
                tcpServer.getPort(),
                webServer.getPort(),
                tcpServer.getPort(),
                dbPath);  // 现在有4个参数对应4个占位符
    }

    // 停止服务器
    public static void stopServers() {
        if (tcpServer != null) {
            tcpServer.stop();
            System.out.println("TCP 服务器已停止");
        }
        if (webServer != null) {
            webServer.stop();
            System.out.println("Web 控制台已停止");
        }
    }

    // 获取 Web 管理界面的完整 URL
    public static String getWebConsoleUrl() {
        if (webServer == null) {
            return "Web 控制台未启动";
        }
        return "http://localhost:" + webServer.getPort();
    }

    // 获取数据库连接 URL
    public static String getDatabaseUrl() {
        if (tcpServer == null) {
            return "TCP 服务器未启动";
        }
        return "jdbc:h2:tcp://localhost:" + tcpServer.getPort() + "/" + dbPath;
    }

}
