package cn.net.pap.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WebClientTest {

    // @Test
    public void WebClientGetTest() throws Exception {
        Mono<String> mono = WebClient.builder().build().get()
                .uri("")
                .retrieve().bodyToMono(String.class);
        System.out.println(mono.block());
    }

    // @Test
    public void WebClientPostTest() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> reqMap = new HashMap<>();
        reqMap.put("accessToken", "");
        reqMap.put("image64", getImgStr("input.jpg"));
        Mono<String> mono = WebClient.builder().defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build().post()
                .uri("")
                .bodyValue(objectMapper.writeValueAsString(reqMap))
                .retrieve().bodyToMono(String.class);
        System.out.println(mono.block());
    }

    // @Test
    public void WebClientPostTest2() throws Exception {
        // 配置连接超时和读取超时
        TcpClient tcpClient = TcpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .doOnConnected(connection ->
                        connection.addHandlerLast(new ReadTimeoutHandler(10))
                                .addHandlerLast(new WriteTimeoutHandler(10)));
        HttpClient httpClient = HttpClient.from(tcpClient)
                .responseTimeout(Duration.ofMillis(10000));

        String jsonStr = "{}";
        Mono<ClientResponse> mono = WebClient.builder()
                // setting timeout
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // add filter basicAuthentication   using  analysisBasicAuthentication() to analysis
                .filter(ExchangeFilterFunctions.basicAuthentication("alexgaoyh", "pap.net.cn"))
                // 添加 header traceId
                .filter(addTraceIdInHeader())
                // add filter logResponse  to record log
                .filter(logResponse())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build()
                .post()
                .uri("")
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .body(BodyInserters.fromObject(jsonStr))
                .exchange()
                .onErrorResume(WebClientRequestException.class, err -> {
                    if(err.getCause() instanceof java.net.ConnectException) {
                        return Mono.just(ClientResponse.create(HttpStatus.GATEWAY_TIMEOUT)
                                .header("pap-retry-code", "NoRetry")
                                .body("PAP: 连接超时").build());
                    } else if (err.getCause() instanceof ReadTimeoutException) {
                        return Mono.just(ClientResponse.create(HttpStatus.REQUEST_TIMEOUT)
                                .header("pap-retry-code", "NoRetry")
                                .body("PAP: 请求超时").build());
                    } else {
                        return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("PAP: 发生其他错误").build());
                    }
                });
        ClientResponse response = mono.block();

        HttpStatusCode statusCode = response.statusCode();
        ClientResponse.Headers headers = response.headers();

        Mono<String> resultMono = response.bodyToMono(String.class);
        String body = resultMono.block();

        System.out.println("statusCode：" + statusCode);
        System.out.println("headers：" + headers.asHttpHeaders());
        System.out.println("body：" + body);
    }

    /**
     * 将图片转换成Base64编码
     *
     * @param imagePath 待处理图片
     * @return
     */
    private static String getImgStr(String imagePath) throws Exception {
        byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));
        String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
        return base64Image;
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
            additionalHeaders.add("pap-retry-code", "NoRetry");

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
