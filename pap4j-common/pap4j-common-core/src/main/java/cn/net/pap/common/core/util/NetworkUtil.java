package cn.net.pap.common.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网络连通性与端口探活工具类。
 * <p>基于纯 JDK 实现 TCP 端口探活、JDBC 数据库 URL 连通性探测以及 HTTP URL 轻量级连通性探测，消除各模块单元测试与集成环境中的重复探测逻辑。</p>
 */
public final class NetworkUtil {

    private static final Logger log = LoggerFactory.getLogger(NetworkUtil.class);

    private static final int DEFAULT_PORT_TIMEOUT_MS = 1000;
    private static final int DEFAULT_URL_TIMEOUT_MS = 2000;

    private static final Pattern JDBC_URL_PATTERN = Pattern.compile(
            "jdbc:(?:[a-zA-Z0-9_]+:)+(?:thin:)?(?:@//?|@|//)([^:/?#;]+)(?::(\\d+))?"
    );

    private NetworkUtil() {
    }

    /**
     * 检测指定主机与端口是否可连接（默认 1000 毫秒超时）。
     *
     * @param host 目标主机名或 IP 地址
     * @param port 目标端口号 (1-65535)
     * @return 端口可连接返回 true，不可连接或超时返回 false
     */
    public static boolean isPortOpen(String host, int port) {
        return isPortOpen(host, port, DEFAULT_PORT_TIMEOUT_MS);
    }

    /**
     * 检测指定主机与端口是否在指定超时时间内可连接。
     *
     * @param host      目标主机名或 IP 地址
     * @param port      目标端口号 (1-65535)
     * @param timeoutMs 连接超时时间（毫秒）
     * @return 端口可连接返回 true，不可连接或超时返回 false
     */
    public static boolean isPortOpen(String host, int port, int timeoutMs) {
        if (host == null || host.isBlank() || port <= 0 || port > 65535) {
            return false;
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), Math.max(timeoutMs, 1));
            return true;
        } catch (Exception ignored) {
            log.debug("TCP 端口探活失败: {}:{} ({})", host, port, ignored.getMessage());
            return false;
        }
    }

    /**
     * 检测指定的 JDBC URL 对应的目标数据库网络端口是否连通（默认 1000 毫秒超时）。
     * <p>智能解析 JDBC 连接串中的主机与端口并执行毫秒级 TCP 端口探活。若是嵌入式内存数据库（如 H2 mem、SQLite）则直接返回 true。</p>
     *
     * @param jdbcUrl JDBC 连接串，如 "jdbc:mysql://127.0.0.1:3306/db?useSSL=false"
     * @return 目标数据库端口畅通返回 true，未连接或解析失败返回 false
     */
    public static boolean isJdbcUrlReachable(String jdbcUrl) {
        return isJdbcUrlReachable(jdbcUrl, DEFAULT_PORT_TIMEOUT_MS);
    }

    /**
     * 检测指定的 JDBC URL 对应的目标数据库网络端口是否在指定超时时间内连通。
     *
     * @param jdbcUrl   JDBC 连接串，如 "jdbc:mysql://127.0.0.1:3306/db?useSSL=false"
     * @param timeoutMs 连接超时时间（毫秒）
     * @return 目标数据库端口畅通返回 true，未连接或解析失败返回 false
     */
    public static boolean isJdbcUrlReachable(String jdbcUrl, int timeoutMs) {
        if (jdbcUrl == null || jdbcUrl.isBlank() || !jdbcUrl.startsWith("jdbc:")) {
            return false;
        }

        // 嵌入式内存数据库无网络端口，直接视为就绪
        if (jdbcUrl.startsWith("jdbc:h2:mem:") || jdbcUrl.startsWith("jdbc:sqlite:") || jdbcUrl.startsWith("jdbc:derby:memory:")) {
            return true;
        }

        Matcher matcher = JDBC_URL_PATTERN.matcher(jdbcUrl);
        if (matcher.find()) {
            String host = matcher.group(1);
            String portStr = matcher.group(2);
            int port = -1;
            if (portStr != null) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException ignored) {
                    return false;
                }
            } else {
                port = getDefaultPortForJdbc(jdbcUrl);
            }

            if (port > 0) {
                return isPortOpen(host, port, timeoutMs);
            }
        }

        log.debug("未能从 JDBC URL 中解析出有效的主机或端口: {}", jdbcUrl);
        return false;
    }

    /**
     * 检测指定的 HTTP / HTTPS URL 是否可访问（默认 2000 毫秒超时）。
     * <p>采用 HEAD 请求方式探测，状态码在 200~399 之间均视为可正常访问。</p>
     *
     * @param urlStr 目标 URL 字符串
     * @return URL 响应正常返回 true，否则返回 false
     */
    public static boolean isUrlReachable(String urlStr) {
        return isUrlReachable(urlStr, DEFAULT_URL_TIMEOUT_MS);
    }

    /**
     * 检测指定的 HTTP / HTTPS URL 是否在指定超时时间内可访问。
     *
     * @param urlStr    目标 URL 字符串
     * @param timeoutMs 连接与读取超时时间（毫秒）
     * @return URL 响应正常返回 true，否则返回 false
     */
    public static boolean isUrlReachable(String urlStr, int timeoutMs) {
        if (urlStr == null || urlStr.isBlank()) {
            return false;
        }

        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(Math.max(timeoutMs, 1));
            conn.setReadTimeout(Math.max(timeoutMs, 1));
            conn.setRequestMethod("HEAD");
            conn.setInstanceFollowRedirects(true);
            int responseCode = conn.getResponseCode();
            return (responseCode >= 200 && responseCode < 400);
        } catch (Exception ignored) {
            log.debug("HTTP URL 探测失败: {} ({})", urlStr, ignored.getMessage());
            return false;
        }
    }

    private static int getDefaultPortForJdbc(String jdbcUrl) {
        String lower = jdbcUrl.toLowerCase();
        if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) {
            return 3306;
        } else if (lower.startsWith("jdbc:postgresql:")) {
            return 5432;
        } else if (lower.startsWith("jdbc:kingbase") || lower.startsWith("jdbc:kingbase8:")) {
            return 54321;
        } else if (lower.startsWith("jdbc:oracle:")) {
            return 1521;
        } else if (lower.startsWith("jdbc:sqlserver:")) {
            return 1433;
        } else if (lower.startsWith("jdbc:clickhouse:")) {
            return 8123;
        } else if (lower.startsWith("jdbc:h2:tcp:")) {
            return 9092;
        }
        return -1;
    }

}
