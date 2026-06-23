package cn.net.pap.example.apitester.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/proxy")
@Tag(name = "代理转发测试接口", description = "提供非阻塞式的通用请求转发代理接口，支持各种 Content-Type 格式和文件上传代理。")
public class ProxyController {

    private final WebClient webClient = WebClient.builder().build();

    /**
     * 通用代理接口，支持 JSON / x-www-form-urlencoded / raw text
     */
    @Operation(summary = "通用请求代理转发", description = "接收目标 URL、HTTP 方法、Headers 和 Body 并通过 WebClient 执行非阻塞的代理转发请求。")
    @PostMapping
    public Mono<ResponseEntity<String>> proxy(@RequestBody Map<String, Object> request) {
        String url = (String) request.get("url");
        String methodStr = ((String) request.getOrDefault("method", "GET")).toUpperCase();
        HttpMethod method = HttpMethod.valueOf(methodStr);

        Map<String, String> headersMap = (Map<String, String>) request.getOrDefault("headers", Map.of());
        HttpHeaders headers = new HttpHeaders();
        headersMap.forEach(headers::add);

        Object bodyObj = request.get("body");

        WebClient.RequestBodySpec reqSpec = webClient.method(method).uri(url).headers(h -> h.addAll(headers));

        if (bodyObj != null) {
            String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
            String bodyStr = bodyObj.toString();

            if (contentType != null) {
                if (contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
                    reqSpec.body(BodyInserters.fromValue(bodyStr));
                } else if (contentType.contains(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
                    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
                    for (String pair : bodyStr.split("&")) {
                        String[] kv = pair.split("=", 2);
                        if (kv.length == 2) formData.add(kv[0], kv[1]);
                    }
                    reqSpec.body(BodyInserters.fromFormData(formData));
                } else {
                    reqSpec.body(BodyInserters.fromValue(bodyStr));
                }
            } else {
                reqSpec.body(BodyInserters.fromValue(bodyStr));
            }
        } else {
            reqSpec.body(BodyInserters.empty());
        }

        return reqSpec
            .retrieve()
            .toEntity(String.class)
            .onErrorResume(e -> Mono.just(ResponseEntity.badRequest()
                    .body("Error: " + e.getMessage())));
    }

    @Operation(summary = "表单参数格式请求代理转发")
    @PostMapping("/form")
    public Mono<ResponseEntity<String>> proxyForm(ServerWebExchange exchange) {
        return exchange.getMultipartData().flatMap(parts -> {

            // 🔹 解析 form 字段
            String targetUrl = null;
            String method = "POST";

            MultiValueMap<String, String> bodyMap = new org.springframework.util.LinkedMultiValueMap<>();

            for (String key : parts.keySet()) {
                for (var part : parts.get(key)) {
                    if (part instanceof FormFieldPart f) {
                        String value = f.value();
                        if (key.equalsIgnoreCase("url")) targetUrl = value;
                        else if (key.equalsIgnoreCase("method")) method = value.toUpperCase();
                        else bodyMap.add(key, value);
                    }
                }
            }

            if (targetUrl == null || targetUrl.isBlank()) {
                return Mono.just(ResponseEntity.badRequest().body("Missing target url in form-data"));
            }

            HttpMethod httpMethod = HttpMethod.valueOf(method);
            if (httpMethod == null) httpMethod = HttpMethod.POST;

            return webClient.method(httpMethod)
                    .uri(targetUrl)
                    .contentType(MediaType.valueOf(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                    .body(BodyInserters.fromFormData(bodyMap))
                    .retrieve()
                    .toEntity(String.class);
        });
    }


    /**
     * multipart/form-data 文件上传代理
     */
    @Operation(summary = "多部分表单 (Multipart) 文件上传代理转发")
    @PostMapping("/multipart")
    public Mono<ResponseEntity<String>> proxyMultipart(ServerWebExchange exchange) {
        return exchange.getMultipartData().flatMap(parts -> {

            // 🔹 解析 form 字段
            String targetUrl = null;
            String method = "POST";

            MultiValueMap<String, Object> bodyMap = new org.springframework.util.LinkedMultiValueMap<>();

            for (String key : parts.keySet()) {
                for (var part : parts.get(key)) {
                    if (part instanceof FormFieldPart f) {
                        String value = f.value();
                        if (key.equalsIgnoreCase("url")) targetUrl = value;
                        else if (key.equalsIgnoreCase("method")) method = value.toUpperCase();
                        else bodyMap.add(key, value);
                    } else if (part instanceof FilePart filePart) {
                        bodyMap.add("files", filePart);
                    }
                }
            }

            if (targetUrl == null || targetUrl.isBlank()) {
                return Mono.just(ResponseEntity.badRequest().body("Missing target url in form-data"));
            }

            HttpMethod httpMethod = HttpMethod.valueOf(method);
            if (httpMethod == null) httpMethod = HttpMethod.POST;

            return webClient.method(httpMethod)
                    .uri(targetUrl)
                    .body(BodyInserters.fromMultipartData(bodyMap))
                    .retrieve()
                    .toEntity(String.class);
        });
    }


}
