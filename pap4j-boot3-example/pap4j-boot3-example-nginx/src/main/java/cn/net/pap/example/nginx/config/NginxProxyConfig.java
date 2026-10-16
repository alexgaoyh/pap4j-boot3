package cn.net.pap.example.nginx.config;

import cn.net.pap.example.nginx.support.NginxProxyRoute;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.ConnectionProvider.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>反向代理组件装配。</p>
 * <p>负责有界共享连接池与每条代理路由的专用 WebClient（独立超时 + 读取超时处理器）。</p>
 *
 * @author alexgaoyh
 */
@Configuration
public class NginxProxyConfig {

    /**
     * 反向代理共享连接池，所有参数均有界，防止资源耗尽。
     *
     * @param properties 配置属性
     * @return 有界连接池
     */
    @Bean(destroyMethod = "dispose")
    public ConnectionProvider proxyConnectionProvider(NginxProperties properties) {
        NginxProperties.Pool pool = properties.getPool();
        Builder builder = ConnectionProvider.builder("pap4j-nginx-proxy")
                .maxConnections(pool.getMaxConnections())
                .pendingAcquireMaxCount(pool.getPendingAcquireMaxCount())
                .pendingAcquireTimeout(pool.getPendingAcquireTimeout())
                .maxIdleTime(pool.getMaxIdleTime());
        return builder.build();
    }

    /**
     * 构建反向代理路由视图列表（每条路由独立超时、共享有界连接池）。
     *
     * @param properties 配置属性
     * @param provider   共享连接池
     * @return 代理路由视图列表
     */
    @Bean
    public List<NginxProxyRoute> nginxProxyRoutes(NginxProperties properties, ConnectionProvider provider) {
        List<NginxProxyRoute> routes = new ArrayList<>(properties.getProxies().size());
        for (NginxProperties.Proxy proxy : properties.getProxies()) {
            routes.add(new NginxProxyRoute(proxy, buildProxyWebClient(proxy, provider)));
        }
        routes.sort((a, b) -> Integer.compare(b.getLocationPath().length(), a.getLocationPath().length()));
        return routes;
    }

    /**
     * 为单条代理路由构建 WebClient（独立超时，共享连接池）。
     *
     * @param proxy    代理配置
     * @param provider 共享连接池
     * @return 代理 WebClient
     */
    private static WebClient buildProxyWebClient(NginxProperties.Proxy proxy, ConnectionProvider provider) {
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(proxy.getConnectTimeout().toMillis()))
                .responseTimeout(proxy.getResponseTimeout())
                .doOnConnected(connection -> connection.addHandlerLast(
                        new ReadTimeoutHandler(proxy.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS)));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

}
