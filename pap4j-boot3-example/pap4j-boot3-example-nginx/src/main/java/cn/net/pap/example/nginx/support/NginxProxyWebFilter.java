package cn.net.pap.example.nginx.support;

import cn.net.pap.example.nginx.config.NginxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>反向代理过滤器，等价于 Nginx 的 {@code location /xxx} + {@code proxy_pass}。</p>
 * <p>在过滤器层直接通过 {@code ServerHttpResponse.writeWith} 流式回写上游响应体，
 * 避免 {@code ServerResponse} 延迟消费导致上游 body 流失效；预检请求直接短路返回 204。</p>
 * <p>同时负责：请求路径穿越拒绝（400）、请求体超限拒绝（413）、上游故障错误页（502）。</p>
 *
 * @author alexgaoyh
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
public class NginxProxyWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(NginxProxyWebFilter.class);

    private static final int BUFFER_SIZE = 8192;

    /**
     * 反向代理路由列表。
     */
    private final List<NginxProxyRoute> routes;

    /**
     * 构造反向代理过滤器。
     *
     * @param routes 反向代理路由列表
     */
    public NginxProxyWebFilter(List<NginxProxyRoute> routes) {
        List<NginxProxyRoute> sorted = new ArrayList<>(routes);
        sorted.sort((a, b) -> Integer.compare(b.getLocationPath().length(), a.getLocationPath().length()));
        this.routes = List.copyOf(sorted);
    }

    /**
     * 命中代理前缀时执行反向代理，否则放行给后续过滤器链。
     *
     * @param exchange 当前请求响应交换
     * @param chain    过滤器链
     * @return 处理结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        NginxProxyRoute route = match(request.getPath().value());
        if (route == null) {
            return chain.filter(exchange);
        }
        NginxProperties.Proxy proxy = route.getProxy();
        NginxProperties.Cors cors = proxy.getCors();
        String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (isPreflight(request, cors)) {
            return handlePreflight(exchange, cors, origin);
        }
        long maxBytes = proxy.getMaxRequestSize().toBytes();
        if (request.getHeaders().getContentLength() > maxBytes) {
            log.warn("[Nginx-Proxy-Reject] 请求体超过上限, path: {}, max: {}", request.getPath().value(), maxBytes);
            ProxyHeaderSupport.applyCors(exchange.getResponse().getHeaders(), cors, origin, false);
            return completeWithStatus(exchange, HttpStatus.PAYLOAD_TOO_LARGE);
        }
        Optional<URI> target = route.buildTargetUri(request);
        if (target.isEmpty()) {
            log.warn("[Nginx-Proxy-BadPath] 代理路径包含目录回溯被拒绝, uri: {}", request.getPath().value());
            ProxyHeaderSupport.applyCors(exchange.getResponse().getHeaders(), cors, origin, false);
            return completeWithStatus(exchange, HttpStatus.BAD_REQUEST);
        }
        WebClient.RequestBodySpec requestSpec = route.getWebClient().method(request.getMethod())
                .uri(target.get())
                .headers(headers -> ProxyHeaderSupport.copyRequestHeaders(request, headers, proxy.isTrustForwardedHeaders()));
        return attachBody(requestSpec, request, maxBytes)
                .exchangeToMono(clientResponse -> writeResponse(exchange, clientResponse, cors, origin))
                .onErrorResume(ex -> isTooLarge(ex) ? payloadTooLarge(exchange, cors, origin)
                        : handleProxyError(exchange, route, cors, origin, ex));
    }

    /**
     * 在 exchangeToMono 内直接流式回写上游响应，确保上游 body 在同一次订阅内被消费。
     *
     * @param exchange       当前请求响应交换
     * @param clientResponse 上游响应
     * @param cors           跨域配置
     * @param origin         请求来源
     * @return 回写结果
     */
    private Mono<Void> writeResponse(ServerWebExchange exchange, ClientResponse clientResponse,
                                     NginxProperties.Cors cors, String origin) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(clientResponse.statusCode());
        ProxyHeaderSupport.copyResponseHeaders(clientResponse, response.getHeaders());
        ProxyHeaderSupport.applyCors(response.getHeaders(), cors, origin, false);
        return response.writeWith(clientResponse.bodyToFlux(DataBuffer.class));
    }

    /**
     * 上游连接异常时返回 502，并在配置了错误页时输出错误页内容。
     *
     * @param exchange 当前请求响应交换
     * @param route    命中的代理路由
     * @param cors     跨域配置
     * @param origin   请求来源
     * @param ex       异常
     * @return 502 响应
     */
    private Mono<Void> handleProxyError(ServerWebExchange exchange, NginxProxyRoute route,
                                        NginxProperties.Cors cors, String origin, Throwable ex) {
        log.error("[Nginx-Proxy-Error] 反向代理失败, path: {}, target: {}",
                route.getProxy().getPath(), route.getProxy().getTarget(), ex);
        ProxyHeaderSupport.applyCors(exchange.getResponse().getHeaders(), cors, origin, false);
        return writeErrorPage(exchange, route.getProxy().getErrorPage(), HttpStatus.BAD_GATEWAY);
    }

    /**
     * 请求体超过上限时返回 413。
     *
     * @param exchange 当前请求响应交换
     * @param cors     跨域配置
     * @param origin   请求来源
     * @return 413 响应
     */
    private Mono<Void> payloadTooLarge(ServerWebExchange exchange, NginxProperties.Cors cors, String origin) {
        log.warn("[Nginx-Proxy-Reject] 流式请求体超过上限, path: {}", exchange.getRequest().getPath().value());
        ProxyHeaderSupport.applyCors(exchange.getResponse().getHeaders(), cors, origin, false);
        return completeWithStatus(exchange, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    /**
     * 写出错误页（配置了文件且可读时输出内容，否则输出空响应体）。
     *
     * @param exchange  当前请求响应交换
     * @param errorPage 错误页文件路径
     * @param status    错误状态码
     * @return 写出结果
     */
    private Mono<Void> writeErrorPage(ServerWebExchange exchange, String errorPage, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(new IllegalStateException("响应已提交，无法写入错误页: " + status.value()));
        }
        response.setStatusCode(status);
        if (!StringUtils.hasText(errorPage)) {
            return response.setComplete();
        }
        return Mono.fromCallable(() -> resolveErrorPage(errorPage))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(path -> writeErrorResource(response, path));
    }

    /**
     * 以指定状态码结束响应。
     *
     * @param exchange 当前请求响应交换
     * @param status   响应状态码
     * @return 结束结果
     */
    private Mono<Void> completeWithStatus(ServerWebExchange exchange, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(new IllegalStateException("响应已提交，无法返回 " + status.value()));
        }
        response.setStatusCode(status);
        return response.setComplete();
    }

    /**
     * 解析错误页文件，不存在时返回 null。
     *
     * @param errorPage 错误页文件路径
     * @return 错误页文件路径，不存在时返回 null
     */
    private static Path resolveErrorPage(String errorPage) {
        Path path = Paths.get(errorPage);
        return Files.isRegularFile(path) ? path : null;
    }

    /**
     * 流式写出错误页资源。
     *
     * @param response 下游响应
     * @param path     错误页文件路径
     * @return 写出结果
     */
    private Mono<Void> writeErrorResource(ServerHttpResponse response, Path path) {
        if (path == null) {
            return response.setComplete();
        }
        response.getHeaders().setContentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8));
        Resource resource = new FileSystemResource(path);
        Flux<DataBuffer> body = DataBufferUtils.read(resource, response.bufferFactory(), BUFFER_SIZE)
                .subscribeOn(Schedulers.boundedElastic());
        return response.writeWith(body);
    }

    /**
     * 预检（OPTIONS）请求直接返回 204，不访问上游。
     *
     * @param exchange 当前请求响应交换
     * @param cors     跨域配置
     * @param origin   请求来源
     * @return 204 响应
     */
    private Mono<Void> handlePreflight(ServerWebExchange exchange, NginxProperties.Cors cors, String origin) {
        ServerHttpResponse response = exchange.getResponse();
        ProxyHeaderSupport.applyCors(response.getHeaders(), cors, origin, true);
        response.setStatusCode(HttpStatus.NO_CONTENT);
        return response.setComplete();
    }

    /**
     * 判断是否为合法的 CORS 预检请求（必须开启 CORS、请求方法为 OPTIONS，且携带 Origin 与 Access-Control-Request-Method）。
     *
     * @param request 客户端请求
     * @param cors    跨域配置
     * @return 是否为预检请求
     */
    private static boolean isPreflight(ServerHttpRequest request, NginxProperties.Cors cors) {
        if (!HttpMethod.OPTIONS.equals(request.getMethod())) {
            return false;
        }
        if (cors == null || !cors.isEnabled()) {
            return false;
        }
        HttpHeaders headers = request.getHeaders();
        return headers.containsKey(HttpHeaders.ORIGIN)
               && headers.containsKey(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
    }

    /**
     * <p>仅在请求确实携带请求体时挂载流式 body，并施加大小上限。</p>
     * <p>GET/HEAD 等无 body 请求若强行挂载空的 dataBuffer 流，会导致上游请求无法正常收尾。</p>
     *
     * @param spec     代理请求
     * @param request  原始请求
     * @param maxBytes 请求体最大字节数
     * @return 可继续发送的请求
     */
    private static WebClient.RequestHeadersSpec<?> attachBody(WebClient.RequestBodySpec spec, ServerHttpRequest request,
                                                              long maxBytes) {
        HttpHeaders headers = request.getHeaders();
        boolean hasBody = headers.getContentLength() > 0 || headers.containsKey(HttpHeaders.TRANSFER_ENCODING);
        if (!hasBody) {
            return spec;
        }
        return spec.body(BodyInserters.fromDataBuffers(limitBody(request.getBody(), maxBytes)));
    }

    /**
     * 对请求体流施加字节数上限，超限抛出 {@link RequestTooLargeException}。
     *
     * @param body     请求体流
     * @param maxBytes 最大字节数
     * @return 限制后的请求体流
     */
    private static Flux<DataBuffer> limitBody(Flux<DataBuffer> body, long maxBytes) {
        AtomicLong total = new AtomicLong();
        return body.map(buffer -> {
            long size = total.addAndGet(buffer.readableByteCount());
            if (size > maxBytes) {
                DataBufferUtils.release(buffer);
                throw new RequestTooLargeException("请求体超过上限: " + maxBytes + " bytes");
            }
            return buffer;
        });
    }

    /**
     * 判断异常链中是否包含请求体超限异常。
     *
     * @param ex 异常
     * @return 是否为请求体超限异常
     */
    private static boolean isTooLarge(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof RequestTooLargeException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 按顺序匹配命中的代理路由。
     *
     * @param path 请求路径
     * @return 命中的代理路由，未命中时返回 null
     */
    private NginxProxyRoute match(String path) {
        for (NginxProxyRoute route : routes) {
            if (route.matches(path)) {
                return route;
            }
        }
        return null;
    }

}
