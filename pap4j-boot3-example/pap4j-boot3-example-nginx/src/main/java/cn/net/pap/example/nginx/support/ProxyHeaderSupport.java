package cn.net.pap.example.nginx.support;

import cn.net.pap.example.nginx.config.NginxProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * <p>反向代理请求/响应头处理工具。</p>
 * <p>负责 hop-by-hop 头过滤、转发头（X-Real-IP / X-Forwarded-*）重建以及 CORS 响应头组装。</p>
 *
 * @author alexgaoyh
 */
public final class ProxyHeaderSupport {

    /**
     * hop-by-hop 头，禁止在代理链路中透传。
     */
    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade");

    /**
     * 仅丢弃 Host 头（由代理重新设置）；Content-Length 需保留，避免强制切换为 chunked 传输。
     */
    private static final Set<String> REQUEST_DROP = Set.of("host");

    private ProxyHeaderSupport() {
    }

    /**
     * 将客户端请求头复制到代理目标请求头（默认不信任客户端转发协议头）。
     *
     * @param request 客户端请求
     * @param target  代理目标请求头容器
     */
    public static void copyRequestHeaders(ServerHttpRequest request, HttpHeaders target) {
        copyRequestHeaders(request, target, false);
    }

    /**
     * 将客户端请求头复制到代理目标请求头，并重建转发类请求头。
     *
     * @param request               客户端请求
     * @param target                代理目标请求头容器
     * @param trustForwardedHeaders 是否信任客户端传入的 X-Forwarded-Proto
     */
    public static void copyRequestHeaders(ServerHttpRequest request, HttpHeaders target, boolean trustForwardedHeaders) {
        HttpHeaders source = request.getHeaders();
        Set<String> hopByHopHeaders = resolveHopByHopHeaders(source);
        source.forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!hopByHopHeaders.contains(lower) && !REQUEST_DROP.contains(lower)) {
                target.put(name, values);
            }
        });
        String host = source.getFirst(HttpHeaders.HOST);
        if (StringUtils.hasText(host)) {
            target.set(HttpHeaders.HOST, host);
            target.set("X-Forwarded-Host", host);
        }
        String remoteIp = resolveRemoteIp(request);
        if (StringUtils.hasText(remoteIp)) {
            target.set("X-Real-IP", remoteIp);
            target.set("REMOTE-HOST", remoteIp);
            target.set("X-Forwarded-For", appendForwardedFor(source.getFirst("X-Forwarded-For"), remoteIp));
        }
        String proto = resolveForwardedProto(request, source, trustForwardedHeaders);
        target.set("X-Forwarded-Proto", proto);
        target.set("X-Forwarded-Port", resolveForwardedPort(request, proto));
    }

    /**
     * 将上游响应头复制到下游响应头，过滤 hop-by-hop 头。
     *
     * @param response 上游响应
     * @param target   下游响应头容器
     */
    public static void copyResponseHeaders(ClientResponse response, HttpHeaders target) {
        HttpHeaders source = response.headers().asHttpHeaders();
        Set<String> hopByHopHeaders = resolveHopByHopHeaders(source);
        source.forEach((name, values) -> {
            if (!hopByHopHeaders.contains(name.toLowerCase(Locale.ROOT))) {
                target.put(name, values);
            }
        });
    }

    /**
     * <p>按方案 A 组装 CORS 响应头。</p>
     * <p>仅当来源命中精准域名白名单时回显跨域头，绝不回显 {@code *}。</p>
     *
     * @param headers   响应头容器
     * @param cors      跨域配置
     * @param origin    请求来源
     * @param preflight 是否为预检请求
     */
    public static void applyCors(HttpHeaders headers, NginxProperties.Cors cors, String origin, boolean preflight) {
        if (cors == null || !cors.isEnabled() || !cors.isAllowedOrigin(origin)) {
            return;
        }
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        addVaryOrigin(headers);
        if (cors.isAllowCredentials()) {
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
        if (!preflight) {
            if (!cors.getExposeHeaders().isEmpty()) {
                headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, String.join(", ", cors.getExposeHeaders()));
            }
            return;
        }
        if (!cors.getAllowedMethods().isEmpty()) {
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, String.join(", ", cors.getAllowedMethods()));
        }
        if (!cors.getAllowedHeaders().isEmpty()) {
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, String.join(", ", cors.getAllowedHeaders()));
        }
        headers.set(HttpHeaders.ACCESS_CONTROL_MAX_AGE, String.valueOf(cors.getMaxAge().toSeconds()));
    }

    /**
     * 向响应头追加 {@code Vary: Origin}，已存在时不重复追加。
     *
     * @param headers 响应头容器
     */
    private static void addVaryOrigin(HttpHeaders headers) {
        if (!headers.getOrEmpty(HttpHeaders.VARY).contains(HttpHeaders.ORIGIN)) {
            headers.add(HttpHeaders.VARY, HttpHeaders.ORIGIN);
        }
    }

    /**
     * 解析静态及通过 Connection 头动态声明的 hop-by-hop 请求头。
     *
     * @param headers 请求头或响应头容器
     * @return 完整的 hop-by-hop 头小写集合
     */
    private static Set<String> resolveHopByHopHeaders(HttpHeaders headers) {
        List<String> connectionHeaders = headers.get(HttpHeaders.CONNECTION);
        if (connectionHeaders == null || connectionHeaders.isEmpty()) {
            return HOP_BY_HOP;
        }
        Set<String> extra = null;
        for (String value : connectionHeaders) {
            if (value == null) {
                continue;
            }
            for (String token : value.split(",")) {
                String trimmed = token.trim().toLowerCase(Locale.ROOT);
                if (!trimmed.isEmpty() && !HOP_BY_HOP.contains(trimmed)) {
                    if (extra == null) {
                        extra = new HashSet<>(HOP_BY_HOP);
                    }
                    extra.add(trimmed);
                }
            }
        }
        return extra == null ? HOP_BY_HOP : extra;
    }

    /**
     * 解析客户端真实 IP。
     *
     * @param request 客户端请求
     * @return 客户端 IP，无法解析时返回空字符串
     */
    private static String resolveRemoteIp(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    /**
     * 追加 X-Forwarded-For 链。
     *
     * @param existing 已存在的 X-Forwarded-For 值
     * @param remoteIp 当前客户端 IP
     * @return 追加后的 X-Forwarded-For 值
     */
    private static String appendForwardedFor(String existing, String remoteIp) {
        if (!StringUtils.hasText(existing)) {
            return remoteIp;
        }
        return existing + ", " + remoteIp;
    }

    /**
     * 解析 X-Forwarded-Proto。
     *
     * @param request               客户端请求
     * @param source                客户端请求头
     * @param trustForwardedHeaders 是否信任客户端传入的协议头
     * @return 协议名，默认 http
     */
    private static String resolveForwardedProto(ServerHttpRequest request, HttpHeaders source, boolean trustForwardedHeaders) {
        if (trustForwardedHeaders) {
            String existing = source.getFirst("X-Forwarded-Proto");
            if (StringUtils.hasText(existing)) {
                String first = existing.split(",")[0].trim().toLowerCase(Locale.ROOT);
                if ("http".equals(first) || "https".equals(first)) {
                    return first;
                }
            }
        }
        String scheme = request.getURI().getScheme();
        return StringUtils.hasText(scheme) ? scheme.toLowerCase(Locale.ROOT) : "http";
    }

    /**
     * 解析 X-Forwarded-Port。
     *
     * @param request 客户端请求
     * @param proto   协议名
     * @return 端口号字符串
     */
    private static String resolveForwardedPort(ServerHttpRequest request, String proto) {
        int port = request.getURI().getPort();
        if (port > 0) {
            return String.valueOf(port);
        }
        return "https".equalsIgnoreCase(proto) ? "443" : "80";
    }

}
