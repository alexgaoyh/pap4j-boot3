package cn.net.pap.example.nginx.support;

import cn.net.pap.example.nginx.config.NginxProperties;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.Optional;

/**
 * <p>单条反向代理路由的运行时视图。</p>
 * <p>封装代理配置、专用 WebClient、上游目标 URI 解析以及路径匹配与转发路径计算逻辑。</p>
 * <p>转发前会对请求路径做穿越校验，拒绝归一化后包含 {@code ..} 或空字符的请求，对齐 Nginx 的 URI 归一化语义。</p>
 *
 * @author alexgaoyh
 */
public final class NginxProxyRoute {

    /**
     * 代理路由配置。
     */
    private final NginxProperties.Proxy proxy;

    /**
     * 代理专用 WebClient。
     */
    private final WebClient webClient;

    /**
     * 上游目标 URI。
     */
    private final URI targetUri;

    /**
     * 归一化后的匹配路径前缀。
     */
    private final String locationPath;

    /**
     * 上游目标是否携带路径（用于判断是否替换 location 前缀）。
     */
    private final boolean targetHasPath;

    /**
     * 构造代理路由视图。
     *
     * @param proxy     代理路由配置
     * @param webClient 代理专用 WebClient
     */
    public NginxProxyRoute(NginxProperties.Proxy proxy, WebClient webClient) {
        this.proxy = proxy;
        this.webClient = webClient;
        this.targetUri = URI.create(proxy.getTarget());
        this.locationPath = PathPrefixSupport.normalize(proxy.getPath());
        String targetPath = targetUri.getRawPath();
        this.targetHasPath = targetPath != null && !targetPath.isEmpty();
    }

    /**
     * 判断请求路径是否命中该代理前缀（按路径段匹配，避免 {@code /apixxx} 误命中 {@code /api}）。
     *
     * @param path 请求路径
     * @return 是否命中
     */
    public boolean matches(String path) {
        return PathPrefixSupport.matches(path, locationPath);
    }

    /**
     * <p>构建上游目标请求 URI（含原始查询串，遵循 Nginx proxy_pass 前缀替换语义）。</p>
     * <p>当请求路径归一化后包含 {@code ..} 或空字符时返回空，交由过滤器拒绝，防止前缀绕过。</p>
     *
     * @param request 客户端请求
     * @return 上游目标 URI；路径非法时为空
     */
    public Optional<URI> buildTargetUri(ServerHttpRequest request) {
        URI original = request.getURI();
        if (hasTraversal(original.getPath())) {
            return Optional.empty();
        }
        String forwardedPath = resolveForwardedPath(original.getRawPath());
        StringBuilder builder = new StringBuilder(targetUri.getScheme())
                .append("://").append(targetUri.getRawAuthority())
                .append(forwardedPath.startsWith("/") ? forwardedPath : "/" + forwardedPath);
        String rawQuery = original.getRawQuery();
        if (rawQuery != null && !rawQuery.isEmpty()) {
            builder.append('?').append(rawQuery);
        }
        return Optional.of(URI.create(builder.toString()));
    }

    /**
     * 判断解码后的路径是否包含目录回溯或空字符。
     *
     * @param decodedPath 解码后的请求路径
     * @return 是否包含目录回溯或空字符
     */
    private static boolean hasTraversal(String decodedPath) {
        if (decodedPath == null) {
            return false;
        }
        if (decodedPath.indexOf('\0') >= 0) {
            return true;
        }
        for (String segment : decodedPath.split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算转发到上游的路径。
     *
     * @param rawPath 原始请求路径
     * @return 转发路径
     */
    private String resolveForwardedPath(String rawPath) {
        if (!targetHasPath) {
            return rawPath;
        }
        String suffix = rawPath.length() >= locationPath.length() ? rawPath.substring(locationPath.length()) : "";
        String prefix = targetUri.getRawPath();
        if (prefix.endsWith("/") && suffix.startsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        String forwarded = prefix + suffix;
        return forwarded.isEmpty() ? "/" : forwarded;
    }

    /**
     * 获取归一化后的匹配路径前缀。
     *
     * @return 匹配路径前缀
     */
    public String getLocationPath() {
        return locationPath;
    }

    /**
     * 获取代理路由配置。
     *
     * @return 代理路由配置
     */
    public NginxProperties.Proxy getProxy() {
        return proxy;
    }

    /**
     * 获取代理专用 WebClient。
     *
     * @return 代理专用 WebClient
     */
    public WebClient getWebClient() {
        return webClient;
    }

}
