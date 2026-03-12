package cn.net.pap.task.webclient;

import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.TcpClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WebClientUtil {

    private static final WebClient webClient;
    private static final HttpClient httpClient;

    static {
        // 完整 Cookie 存储：域名 -> path -> cookie name -> Cookie。此处是全局的，后续可以尝试进行调整，比如上 redis
        Map<String, Map<String, Map<String, Cookie>>> nettyCookieStore = new ConcurrentHashMap<>();

        // 创建连接池 ConnectionProvider
        ConnectionProvider provider = ConnectionProvider.builder("pap4j-boot3-task-webclient")
                .maxConnections(2000)                        // 最大连接数（根据你业务调）
                .pendingAcquireMaxCount(5000)                // 等待队列
                .pendingAcquireTimeout(Duration.ofSeconds(2))// 等待超时
                .maxIdleTime(Duration.ofSeconds(30))         // 空闲连接存活
                .lifo()                                      // LIFO 方式减少队列抖动
                .build();

        // 构建 TcpClient，并保留你现有的 timeout 配置
        TcpClient tcpClient = TcpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)        // 连接超时
                .doOnConnected(connection -> {
                    connection.addHandlerLast(new ReadTimeoutHandler(10)); // 读超时
                    connection.addHandlerLast(new WriteTimeoutHandler(10));// 写超时
                });

        // 构建 HttpClient 并保留你的 responseTimeout
        httpClient = HttpClient.from(tcpClient)
                .responseTimeout(Duration.ofMillis(10000))
                .cookieCodec(ClientCookieEncoder.STRICT, ClientCookieDecoder.STRICT)
                // 请求前注入 cookie
                .doOnRequest((request, connection) -> {
                    InetSocketAddress remoteAddress = (InetSocketAddress) connection.channel().remoteAddress();
                    Map<String, Map<String, Cookie>> domainMap = nettyCookieStore.get(remoteAddress.toString());
                    if (domainMap != null) {
                        domainMap.forEach((cookiePath, cookieMap) -> {
                            cookieMap.values().forEach(request::addCookie);
                        });
                    }
                })
                // 响应后存储 Set-Cookie
                .doOnResponse((response, connection) -> {
                    InetSocketAddress remoteAddress = (InetSocketAddress) connection.channel().remoteAddress();
                    response.cookies().values().forEach(cookies -> {
                        cookies.forEach(cookie -> {
                            String cookiePath = cookie.path() != null ? cookie.path() : "/";
                            nettyCookieStore
                                    .computeIfAbsent(remoteAddress.toString(), k -> new ConcurrentHashMap<>())
                                    .computeIfAbsent(cookiePath, k -> new ConcurrentHashMap<>())
                                    .put(cookie.name(), cookie);
                        });
                    });
                });

        // 初始化 WebClient
        webClient = WebClient.builder()
                // setting timeout
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // add filter basicAuthentication   using  analysisBasicAuthentication() to analysis
                .filter(ExchangeFilterFunctions.basicAuthentication("alexgaoyh", "pap.net.cn"))
                // 添加 header traceId
                .filter(addTraceIdInHeader())
                // add filter logResponse  to record log
                .filter(logResponse())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 异步POST请求 - 返回Mono，由调用方决定是否阻塞
     */
    public static Mono<ClientResponse> postMono(String url, String bodyJSON, HttpHeaders headers) {
        String traceId = UUID.randomUUID().toString();

        return webClient.post()
                .uri(url)
                .headers(h -> {
                    if (headers != null && !headers.isEmpty()) {
                        h.addAll(headers);
                    }
                    h.set("pap-trace-id", traceId);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bodyJSON)
                .exchange()
                .onErrorResume(WebClientRequestException.class, err -> {
                    if (err.getCause() instanceof java.net.ConnectException) {
                        return Mono.just(ClientResponse.create(HttpStatus.GATEWAY_TIMEOUT)
                                .header("pap-retry-code", "NoRetry")
                                .header("exception-code", HttpStatus.GATEWAY_TIMEOUT + "")
                                .header("pap-trace-id", traceId)
                                .body("PAP: 连接超时").build());
                    } else if (err.getCause() instanceof ReadTimeoutException) {
                        return Mono.just(ClientResponse.create(HttpStatus.REQUEST_TIMEOUT)
                                .header("pap-retry-code", "NoRetry")
                                .header("exception-code", HttpStatus.REQUEST_TIMEOUT + "")
                                .header("pap-trace-id", traceId)
                                .body("PAP: 请求超时").build());
                    } else {
                        return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                                .header("exception-code", HttpStatus.INTERNAL_SERVER_ERROR + "")
                                .header("pap-trace-id", traceId)
                                .body("PAP: 发生其他错误").build());
                    }
                });
    }

    /**
     * simple http post request body
     *
     * @param url
     * @param objectJSON
     * @param headers
     * @param maxByteCount 10*1024*1024代表10MB
     * @return
     */
    public static String postBodyObjectSimple(String url,
                                              Object objectJSON,
                                              org.springframework.http.HttpHeaders headers,
                                              Integer maxByteCount) {
        // 配置连接超时和读取超时
        TcpClient tcpClient = TcpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .doOnConnected(connection ->
                        connection.addHandlerLast(new ReadTimeoutHandler(10))
                                .addHandlerLast(new WriteTimeoutHandler(10)));

        HttpClient httpClient = HttpClient.from(tcpClient)
                .responseTimeout(Duration.ofMillis(10000));

        Mono<ClientResponse> mono = WebClient.builder()
                .exchangeStrategies(
                        ExchangeStrategies.builder()
                                .codecs(configurer -> configurer
                                        .defaultCodecs().maxInMemorySize(maxByteCount)
                                ).build())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeaders(httpHeaders -> {
                    if (headers != null && !headers.isEmpty()) {
                        httpHeaders.addAll(headers);
                    }
                })
                .build()
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .body(BodyInserters.fromObject(objectJSON))
                .exchange();
        ClientResponse response = mono.block();
        Mono<String> resultMono = response.bodyToMono(String.class);
        String body = resultMono.block();
        return body;
    }

    /**
     * WebClient post body json
     *
     * @param url
     * @param bodyJSON
     * @return
     */
    public static ClientResponse postBody(String url, String bodyJSON, org.springframework.http.HttpHeaders headers) {
        // 配置连接超时和读取超时
        TcpClient tcpClient = TcpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .doOnConnected(connection ->
                        connection.addHandlerLast(new ReadTimeoutHandler(10))
                                .addHandlerLast(new WriteTimeoutHandler(10)));
        HttpClient httpClient = HttpClient.from(tcpClient)
                .responseTimeout(Duration.ofMillis(10000));

        String existingTraceId = null;
        String newTraceId = UUID.randomUUID().toString();
        if (headers != null && !headers.isEmpty()) {
            existingTraceId = headers.getFirst("pap-trace-id");
        }
        String combinedTraceId = existingTraceId != null ? existingTraceId + "," + newTraceId : newTraceId;

        Mono<ClientResponse> mono = WebClient.builder()
                // setting timeout
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // add filter basicAuthentication   using  analysisBasicAuthentication() to analysis
                .filter(ExchangeFilterFunctions.basicAuthentication("alexgaoyh", "pap.net.cn"))
                // 添加 header traceId
                .filter(addTraceIdInHeader())
                // add filter logResponse  to record log
                .filter(logResponse())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeaders(httpHeaders -> {
                    if (headers != null && !headers.isEmpty()) {
                        httpHeaders.addAll(headers);
                    }
                })
                .build()
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .body(BodyInserters.fromObject(bodyJSON))
                .exchange()
                .onErrorResume(WebClientRequestException.class, err -> {
                    if (err.getCause() instanceof java.net.ConnectException) {
                        return Mono.just(ClientResponse.create(HttpStatus.GATEWAY_TIMEOUT)
                                .header("pap-retry-code", "NoRetry")
                                .header("exception-code", HttpStatus.GATEWAY_TIMEOUT + "")
                                .header("pap-trace-id", combinedTraceId)
                                .body("PAP: 连接超时").build());
                    } else if (err.getCause() instanceof ReadTimeoutException) {
                        return Mono.just(ClientResponse.create(HttpStatus.REQUEST_TIMEOUT)
                                .header("pap-retry-code", "NoRetry")
                                .header("exception-code", HttpStatus.REQUEST_TIMEOUT + "")
                                .header("pap-trace-id", combinedTraceId)
                                .body("PAP: 请求超时").build());
                    } else {
                        return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                                .header("exception-code", HttpStatus.INTERNAL_SERVER_ERROR + "")
                                .header("pap-trace-id", combinedTraceId)
                                .body("PAP: 发生其他错误").build());
                    }
                });
        ClientResponse response = mono.block();
        return response;
    }

    public static void empty() {

    }

    /**
     * 极简异步 POST (JSON) - 盲发模式 (Fire-and-forget)
     * 复用全局配置的 webClient (包含连接池、Cookie存储、拦截器等机制)
     *
     * @param url      请求地址
     * @param bodyJSON 请求体 JSON 字符串
     * @param headers  额外的 HTTP 头 (可选，允许为 null)
     * @return 只要没有发生同步构建异常，就返回 true
     */
    public static boolean postBodyObjectSimpleBlindly(String url, String bodyJSON, HttpHeaders headers) {
        try {
            // 直接复用在 static 代码块中初始化好的全局 webClient
            webClient.post()
                    .uri(url)
                    .headers(h -> {
                        if (headers != null && !headers.isEmpty()) {
                            h.addAll(headers);
                        }
                    })
                    // 你的全局配置中已经有了 defaultHeader(CONTENT_TYPE, JSON)，这里其实可以省略，
                    // 但显式指定更稳妥，避免后续全局配置修改导致意外
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(bodyJSON == null ? "" : bodyJSON)
                    .retrieve()
                    // 彻底丢弃响应体，这对于节省内存非常关键
                    .bodyToMono(Void.class)
                    /*
                     * 核心机制：响应式流的终点必须有订阅者。
                     * 这里提供两个空的 Lambda：吞掉成功和失败的信号，不干扰主线程
                     */
                    .subscribe(
                            success -> {},
                            error -> {
                                // 虽然是盲发，但在排查问题时，如果完全没有日志可能会很痛苦。
                                // 这里你可以选择保留一句很轻量的 debug 或 trace 级别的日志。
                                // 如果追求绝对零开销，可以连下面这行注释掉。
                                // log.debug("盲发 POST 请求异步执行异常, URL: {}", url, error);
                            }
                    );
            return true;
        } catch (Exception e) {
            // 吞掉同步构建异常（例如传入了非法的 url 字符串）
            return false;
        }
    }

    /**
     * 创建响应拦截器 示例 响应体转大写。
     *
     * @return
     */
    private static ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            // 获取响应体
            Mono<String> body = clientResponse.bodyToMono(String.class);

            // 打印响应信息
//            System.out.println("Status code: " + clientResponse.statusCode());
//            clientResponse.headers().asHttpHeaders().forEach((name, values) -> {
//                System.out.println(name + ": " + values);
//            });

            // 修改响应体 转大写
            Mono<String> modifiedBody = body.map(str -> str.toUpperCase());

            // 添加额外的响应头信息，应用场景： 可以做一个约定，在遇到响应头信息包含当前信息的时候，直接不再进行重置返回。
            HttpHeaders additionalHeaders = new HttpHeaders();
            //additionalHeaders.add("pap-retry-code", "NoRetry");

            // 构建新的 ClientResponse 包含修改后的响应体
            return modifiedBody.flatMap(modifiedBodyContent -> {
                ClientResponse newResponse = ClientResponse.create(clientResponse.statusCode())
                        .headers(
                                headers -> {
                                    headers.addAll(clientResponse.headers().asHttpHeaders());
                                    headers.addAll(additionalHeaders);
                                }
                        )
                        .body(modifiedBodyContent)
                        .build();
                return Mono.just(newResponse);
            });
        });
    }

    /**
     * 请求头添加 pap-trace-id 链路ID
     *
     * @return
     */
    private static ExchangeFilterFunction addTraceIdInHeader() {
        return new ExchangeFilterFunction() {
            @Override
            public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
                String existingTraceId = request.headers().getFirst("pap-trace-id");
                // traceId from UUID
                String newTraceId = UUID.randomUUID().toString();

                String combinedTraceId = existingTraceId != null ? existingTraceId + "," + newTraceId : newTraceId;

                ClientRequest modifiedRequest = ClientRequest.from(request)
                        .headers(headers -> headers.set("pap-trace-id", combinedTraceId))
                        .build();

                // 继续执行链中的下一个过滤器
                return next.exchange(modifiedRequest);
            }
        };
    }

    /**
     * 解析 authorizationHeader
     * 参数获得方法： String authorizationHeader = request.getHeader("Authorization");
     * 调用添加filter : WebClient.builder().filter(ExchangeFilterFunctions.basicAuthentication("alexgaoyh", "pap.net.cn"))
     *
     * @param authorizationHeader
     * @return
     */
    private static String analysisBasicAuthentication(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Basic ")) {
            // 获取Base64编码的用户名和密码
            String base64Credentials = authorizationHeader.substring("Basic ".length());
            byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(credDecoded, StandardCharsets.UTF_8);

            // 分割用户名和密码
            final String[] values = credentials.split(":", 2);
            if (values.length == 2) {
                String username = values[0];
                String password = values[1];
                return username + ":" + password;
            }
        }
        return null;
    }
}
