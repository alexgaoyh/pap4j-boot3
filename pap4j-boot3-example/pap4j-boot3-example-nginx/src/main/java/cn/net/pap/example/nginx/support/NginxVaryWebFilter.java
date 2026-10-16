package cn.net.pap.example.nginx.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * <p>为可压缩响应补充 {@code Vary: Accept-Encoding} 响应头。</p>
 * <p>Reactor Netty 的压缩不会自动写入该头，缺失时共享缓存可能把 gzip 响应发给不支持压缩的客户端；
 * 仅在开启 {@code server.compression.enabled} 时生效。</p>
 *
 * @author alexgaoyh
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class NginxVaryWebFilter implements WebFilter {

    /**
     * 是否开启服务端压缩。
     */
    private final boolean compressionEnabled;

    /**
     * 构造 Vary 响应头过滤器。
     *
     * @param compressionEnabled 是否开启服务端压缩
     */
    public NginxVaryWebFilter(@Value("${server.compression.enabled:false}") boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
    }

    /**
     * 开启压缩时，在响应提交前补充 {@code Vary: Accept-Encoding}。
     *
     * @param exchange 当前请求响应交换
     * @param chain    过滤器链
     * @return 处理结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!compressionEnabled) {
            return chain.filter(exchange);
        }
        ServerHttpResponse response = exchange.getResponse();
        response.beforeCommit(() -> {
            HttpHeaders headers = response.getHeaders();
            boolean present = headers.getOrEmpty(HttpHeaders.VARY).stream()
                    .anyMatch(value -> value != null && value.toLowerCase(Locale.ROOT).contains("accept-encoding"));
            if (!present) {
                headers.add(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

}
