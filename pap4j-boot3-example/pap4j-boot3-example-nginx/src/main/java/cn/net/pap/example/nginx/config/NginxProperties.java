package cn.net.pap.example.nginx.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Nginx 替代服务的配置属性，前缀为 {@code pap4j.nginx}。</p>
 * <p>配置结构对照原生 Nginx：{@code resources} 对应静态资源 location 块，{@code proxies} 对应反向代理 location 块。</p>
 *
 * @author alexgaoyh
 */
@ConfigurationProperties(prefix = "pap4j.nginx")
public class NginxProperties {

    /**
     * 静态资源托管配置。
     */
    private Resources resources = new Resources();

    /**
     * 反向代理路由列表。
     */
    private List<Proxy> proxies = new ArrayList<>();

    /**
     * 反向代理共享连接池配置。
     */
    private Pool pool = new Pool();

    /**
     * <p>配置加载后的自检钩子。</p>
     * <p>拒绝 CORS 方案 A 中的危险组合：允许携带凭证时使用 {@code *} 通配符来源。</p>
     */
    @PostConstruct
    public void validate() {
        for (Proxy proxy : proxies) {
            validateProxy(proxy);
        }
        if (resources.isEnabled()) {
            for (Location location : resources.getLocations()) {
                if (location.isEnabled() && !StringUtils.hasText(location.getRoot())) {
                    throw new IllegalStateException("pap4j.nginx.resources.locations[" + location.getPath() + "].root 不能为空");
                }
            }
        }
        validatePool();
    }

    /**
     * 校验单条代理路由配置的必填项、超时与跨域组合。
     *
     * @param proxy 代理路由配置
     */
    private void validateProxy(Proxy proxy) {
        if (!StringUtils.hasText(proxy.getPath())) {
            throw new IllegalStateException("pap4j.nginx.proxies[*].path 不能为空");
        }
        if (!StringUtils.hasText(proxy.getTarget())) {
            throw new IllegalStateException("pap4j.nginx.proxies[" + proxy.getPath() + "].target 不能为空");
        }
        if (isNotPositive(proxy.getConnectTimeout()) || isNotPositive(proxy.getResponseTimeout())
            || isNotPositive(proxy.getReadTimeout())) {
            throw new IllegalStateException("pap4j.nginx.proxies[" + proxy.getPath() + "] 超时时间必须为正数");
        }
        if (proxy.getMaxRequestSize() == null || proxy.getMaxRequestSize().toBytes() <= 0) {
            throw new IllegalStateException("pap4j.nginx.proxies[" + proxy.getPath() + "].max-request-size 必须为正数");
        }
        Cors cors = proxy.getCors();
        if (cors != null && cors.isEnabled() && cors.isAllowCredentials()
            && cors.getAllowedOrigins().contains("*")) {
            throw new IllegalStateException("CORS 方案A禁止在允许凭证时使用 * 通配符，请为代理路径 ["
                                            + proxy.getPath() + "] 配置精准域名白名单");
        }
    }

    /**
     * 校验反向代理连接池参数均为正数。
     */
    private void validatePool() {
        if (pool.getMaxConnections() <= 0 || pool.getPendingAcquireMaxCount() <= 0) {
            throw new IllegalStateException("pap4j.nginx.pool 连接数配置必须为正数");
        }
        if (isNotPositive(pool.getPendingAcquireTimeout()) || isNotPositive(pool.getMaxIdleTime())) {
            throw new IllegalStateException("pap4j.nginx.pool 超时时间必须为正数");
        }
    }

    /**
     * 判断时长是否为空或非正数。
     *
     * @param duration 时长
     * @return 是否为空或非正数
     */
    private static boolean isNotPositive(Duration duration) {
        return duration == null || duration.isNegative() || duration.isZero();
    }

    /**
     * 获取静态资源托管配置。
     *
     * @return 静态资源托管配置
     */
    public Resources getResources() {
        return resources;
    }

    /**
     * 设置静态资源托管配置。
     *
     * @param resources 静态资源托管配置
     */
    public void setResources(Resources resources) {
        this.resources = resources;
    }

    /**
     * 获取反向代理路由列表。
     *
     * @return 反向代理路由列表
     */
    public List<Proxy> getProxies() {
        return proxies;
    }

    /**
     * 设置反向代理路由列表。
     *
     * @param proxies 反向代理路由列表
     */
    public void setProxies(List<Proxy> proxies) {
        this.proxies = proxies;
    }

    /**
     * 获取反向代理共享连接池配置。
     *
     * @return 反向代理共享连接池配置
     */
    public Pool getPool() {
        return pool;
    }

    /**
     * 设置反向代理共享连接池配置。
     *
     * @param pool 反向代理共享连接池配置
     */
    public void setPool(Pool pool) {
        this.pool = pool;
    }

    /**
     * 静态资源托管配置，对应 Nginx 的静态 location 块。
     */
    public static class Resources {

        /**
         * 是否启用静态资源托管。
         */
        private boolean enabled = true;

        /**
         * 静态资源位置列表。
         */
        private List<Location> locations = new ArrayList<>();

        /**
         * 获取是否启用静态资源托管。
         *
         * @return 是否启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用静态资源托管。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取静态资源位置列表。
         *
         * @return 静态资源位置列表
         */
        public List<Location> getLocations() {
            return locations;
        }

        /**
         * 设置静态资源位置列表。
         *
         * @param locations 静态资源位置列表
         */
        public void setLocations(List<Location> locations) {
            this.locations = locations;
        }
    }

    /**
     * 单个静态资源位置，对应 Nginx 的 {@code location} + {@code root} + {@code try_files}。
     */
    public static class Location {

        /**
         * 是否启用该位置。
         */
        private boolean enabled = true;

        /**
         * 匹配的路径前缀，例如 {@code /}。
         */
        private String path = "/";

        /**
         * 静态文件根目录的绝对路径。
         */
        private String root;

        /**
         * 目录默认索引文件列表。
         */
        private List<String> index = new ArrayList<>(List.of("index.html", "index.htm"));

        /**
         * SPA 回退文件，文件不存在时返回该文件，等价于 {@code try_files $uri $uri/ /index.html}。
         */
        private String spaFallback = "/index.html";

        /**
         * 可选的 Cache-Control 响应头值，为空时不输出该响应头。
         */
        private String cacheControl = "";

        /**
         * 响应字符集，为空时不追加 charset 参数，等价于 Nginx 的 charset 指令。
         */
        private String charset = "utf-8";

        /**
         * 获取是否启用该位置。
         *
         * @return 是否启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用该位置。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取匹配路径前缀。
         *
         * @return 匹配路径前缀
         */
        public String getPath() {
            return path;
        }

        /**
         * 设置匹配路径前缀。
         *
         * @param path 匹配路径前缀
         */
        public void setPath(String path) {
            this.path = path;
        }

        /**
         * 获取静态文件根目录。
         *
         * @return 静态文件根目录
         */
        public String getRoot() {
            return root;
        }

        /**
         * 设置静态文件根目录。
         *
         * @param root 静态文件根目录
         */
        public void setRoot(String root) {
            this.root = root;
        }

        /**
         * 获取目录默认索引文件列表。
         *
         * @return 索引文件列表
         */
        public List<String> getIndex() {
            return index;
        }

        /**
         * 设置目录默认索引文件列表。
         *
         * @param index 索引文件列表
         */
        public void setIndex(List<String> index) {
            this.index = index;
        }

        /**
         * 获取 SPA 回退文件。
         *
         * @return SPA 回退文件
         */
        public String getSpaFallback() {
            return spaFallback;
        }

        /**
         * 设置 SPA 回退文件。
         *
         * @param spaFallback SPA 回退文件
         */
        public void setSpaFallback(String spaFallback) {
            this.spaFallback = spaFallback;
        }

        /**
         * 获取 Cache-Control 响应头值。
         *
         * @return Cache-Control 响应头值
         */
        public String getCacheControl() {
            return cacheControl;
        }

        /**
         * 设置 Cache-Control 响应头值。
         *
         * @param cacheControl Cache-Control 响应头值
         */
        public void setCacheControl(String cacheControl) {
            this.cacheControl = cacheControl;
        }

        /**
         * 获取响应字符集。
         *
         * @return 响应字符集
         */
        public String getCharset() {
            return charset;
        }

        /**
         * 设置响应字符集。
         *
         * @param charset 响应字符集
         */
        public void setCharset(String charset) {
            this.charset = charset;
        }
    }

    /**
     * 单条反向代理路由，对应 Nginx 的 {@code location} + {@code proxy_pass}。
     */
    public static class Proxy {

        /**
         * 匹配的路径前缀，例如 {@code /api}。
         */
        private String path;

        /**
         * 上游目标地址，例如 {@code http://127.0.0.1:8088}。
         */
        private String target;

        /**
         * 建连超时时间。
         */
        private Duration connectTimeout = Duration.ofSeconds(3);

        /**
         * 响应超时时间。
         */
        private Duration responseTimeout = Duration.ofSeconds(30);

        /**
         * 读取（空闲）超时时间，防止上游慢速 trickle 长期占用连接。
         */
        private Duration readTimeout = Duration.ofSeconds(30);

        /**
         * 允许的最大请求体大小，超限返回 413（对齐 Nginx client_max_body_size，默认 1MB）。
         */
        private DataSize maxRequestSize = DataSize.ofMegabytes(1);

        /**
         * 上游不可用时返回的错误页文件（绝对路径），为空时返回空响应体。
         */
        private String errorPage = "";

        /**
         * 是否信任客户端传入的 X-Forwarded-Proto；默认 false（边缘代理按连接协议取值，防伪造）。
         */
        private boolean trustForwardedHeaders = false;

        /**
         * 跨域配置。
         */
        private Cors cors = new Cors();

        /**
         * 获取匹配路径前缀。
         *
         * @return 匹配路径前缀
         */
        public String getPath() {
            return path;
        }

        /**
         * 设置匹配路径前缀。
         *
         * @param path 匹配路径前缀
         */
        public void setPath(String path) {
            this.path = path;
        }

        /**
         * 获取上游目标地址。
         *
         * @return 上游目标地址
         */
        public String getTarget() {
            return target;
        }

        /**
         * 设置上游目标地址。
         *
         * @param target 上游目标地址
         */
        public void setTarget(String target) {
            this.target = target;
        }

        /**
         * 获取建连超时时间。
         *
         * @return 建连超时时间
         */
        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        /**
         * 设置建连超时时间。
         *
         * @param connectTimeout 建连超时时间
         */
        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        /**
         * 获取响应超时时间。
         *
         * @return 响应超时时间
         */
        public Duration getResponseTimeout() {
            return responseTimeout;
        }

        /**
         * 设置响应超时时间。
         *
         * @param responseTimeout 响应超时时间
         */
        public void setResponseTimeout(Duration responseTimeout) {
            this.responseTimeout = responseTimeout;
        }

        /**
         * 获取读取（空闲）超时时间。
         *
         * @return 读取超时时间
         */
        public Duration getReadTimeout() {
            return readTimeout;
        }

        /**
         * 设置读取（空闲）超时时间。
         *
         * @param readTimeout 读取超时时间
         */
        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        /**
         * 获取最大请求体大小。
         *
         * @return 最大请求体大小
         */
        public DataSize getMaxRequestSize() {
            return maxRequestSize;
        }

        /**
         * 设置最大请求体大小。
         *
         * @param maxRequestSize 最大请求体大小
         */
        public void setMaxRequestSize(DataSize maxRequestSize) {
            this.maxRequestSize = maxRequestSize;
        }

        /**
         * 获取错误页文件路径。
         *
         * @return 错误页文件路径
         */
        public String getErrorPage() {
            return errorPage;
        }

        /**
         * 设置错误页文件路径。
         *
         * @param errorPage 错误页文件路径
         */
        public void setErrorPage(String errorPage) {
            this.errorPage = errorPage;
        }

        /**
         * 获取是否信任客户端传入的转发协议头。
         *
         * @return 是否信任
         */
        public boolean isTrustForwardedHeaders() {
            return trustForwardedHeaders;
        }

        /**
         * 设置是否信任客户端传入的转发协议头。
         *
         * @param trustForwardedHeaders 是否信任
         */
        public void setTrustForwardedHeaders(boolean trustForwardedHeaders) {
            this.trustForwardedHeaders = trustForwardedHeaders;
        }

        /**
         * 获取跨域配置。
         *
         * @return 跨域配置
         */
        public Cors getCors() {
            return cors;
        }

        /**
         * 设置跨域配置。
         *
         * @param cors 跨域配置
         */
        public void setCors(Cors cors) {
            this.cors = cors;
        }
    }

    /**
     * <p>跨域配置（方案 A：精准域名白名单 + 允许凭证）。</p>
     * <p>只有当请求 {@code Origin} 命中 {@link #allowedOrigins} 白名单时，才会回显跨域响应头。</p>
     */
    public static class Cors {

        /**
         * 是否启用跨域处理。
         */
        private boolean enabled = false;

        /**
         * 允许的来源白名单，禁止使用 {@code *} 与 allowCredentials 组合。
         */
        private List<String> allowedOrigins = new ArrayList<>();

        /**
         * 是否允许携带凭证。
         */
        private boolean allowCredentials = true;

        /**
         * 允许的请求方法。
         */
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "OPTIONS"));

        /**
         * 允许的请求头。
         */
        private List<String> allowedHeaders = new ArrayList<>();

        /**
         * 允许前端读取的响应头（Access-Control-Expose-Headers）。
         */
        private List<String> exposeHeaders = new ArrayList<>();

        /**
         * 预检结果缓存时长。
         */
        private Duration maxAge = Duration.ofHours(1);

        /**
         * 判断来源是否命中白名单。
         *
         * @param origin 请求来源
         * @return 是否命中
         */
        public boolean isAllowedOrigin(String origin) {
            return origin != null && allowedOrigins.contains(origin);
        }

        /**
         * 获取是否启用跨域处理。
         *
         * @return 是否启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用跨域处理。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取允许的来源白名单。
         *
         * @return 允许的来源白名单
         */
        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        /**
         * 设置允许的来源白名单。
         *
         * @param allowedOrigins 允许的来源白名单
         */
        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        /**
         * 获取是否允许携带凭证。
         *
         * @return 是否允许携带凭证
         */
        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        /**
         * 设置是否允许携带凭证。
         *
         * @param allowCredentials 是否允许携带凭证
         */
        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        /**
         * 获取允许的请求方法。
         *
         * @return 允许的请求方法
         */
        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        /**
         * 设置允许的请求方法。
         *
         * @param allowedMethods 允许的请求方法
         */
        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        /**
         * 获取允许的请求头。
         *
         * @return 允许的请求头
         */
        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        /**
         * 设置允许的请求头。
         *
         * @param allowedHeaders 允许的请求头
         */
        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        /**
         * 获取允许前端读取的响应头。
         *
         * @return 允许前端读取的响应头
         */
        public List<String> getExposeHeaders() {
            return exposeHeaders;
        }

        /**
         * 设置允许前端读取的响应头。
         *
         * @param exposeHeaders 允许前端读取的响应头
         */
        public void setExposeHeaders(List<String> exposeHeaders) {
            this.exposeHeaders = exposeHeaders;
        }

        /**
         * 获取预检结果缓存时长。
         *
         * @return 预检结果缓存时长
         */
        public Duration getMaxAge() {
            return maxAge;
        }

        /**
         * 设置预检结果缓存时长。
         *
         * @param maxAge 预检结果缓存时长
         */
        public void setMaxAge(Duration maxAge) {
            this.maxAge = maxAge;
        }
    }

    /**
     * <p>反向代理共享连接池配置。</p>
     * <p>所有参数均为有界配置，避免无界队列导致的资源耗尽。</p>
     */
    public static class Pool {

        /**
         * 最大连接数。
         */
        private int maxConnections = 500;

        /**
         * 等待获取连接的最大排队数量（有界）。
         */
        private int pendingAcquireMaxCount = 1000;

        /**
         * 等待获取连接的超时时间。
         */
        private Duration pendingAcquireTimeout = Duration.ofSeconds(30);

        /**
         * 连接最大空闲时间。
         */
        private Duration maxIdleTime = Duration.ofSeconds(30);

        /**
         * 获取最大连接数。
         *
         * @return 最大连接数
         */
        public int getMaxConnections() {
            return maxConnections;
        }

        /**
         * 设置最大连接数。
         *
         * @param maxConnections 最大连接数
         */
        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        /**
         * 获取等待获取连接的最大排队数量。
         *
         * @return 最大排队数量
         */
        public int getPendingAcquireMaxCount() {
            return pendingAcquireMaxCount;
        }

        /**
         * 设置等待获取连接的最大排队数量。
         *
         * @param pendingAcquireMaxCount 最大排队数量
         */
        public void setPendingAcquireMaxCount(int pendingAcquireMaxCount) {
            this.pendingAcquireMaxCount = pendingAcquireMaxCount;
        }

        /**
         * 获取等待获取连接的超时时间。
         *
         * @return 等待获取连接的超时时间
         */
        public Duration getPendingAcquireTimeout() {
            return pendingAcquireTimeout;
        }

        /**
         * 设置等待获取连接的超时时间。
         *
         * @param pendingAcquireTimeout 等待获取连接的超时时间
         */
        public void setPendingAcquireTimeout(Duration pendingAcquireTimeout) {
            this.pendingAcquireTimeout = pendingAcquireTimeout;
        }

        /**
         * 获取连接最大空闲时间。
         *
         * @return 连接最大空闲时间
         */
        public Duration getMaxIdleTime() {
            return maxIdleTime;
        }

        /**
         * 设置连接最大空闲时间。
         *
         * @param maxIdleTime 连接最大空闲时间
         */
        public void setMaxIdleTime(Duration maxIdleTime) {
            this.maxIdleTime = maxIdleTime;
        }
    }

}
