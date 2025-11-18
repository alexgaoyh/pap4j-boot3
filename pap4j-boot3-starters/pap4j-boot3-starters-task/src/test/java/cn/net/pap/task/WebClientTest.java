package cn.net.pap.task;

import cn.net.pap.task.dto.WebClientBodyDTO;
import cn.net.pap.task.util.RateLimitedUtil;
import cn.net.pap.task.webclient.WebClientUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

        ClientResponse response = WebClientUtil.postBody("http://127.0.0.1:30000/timeout", "{}", null);

        HttpStatusCode statusCode = response.statusCode();
        ClientResponse.Headers headers = response.headers();

        Mono<String> resultMono = response.bodyToMono(String.class);
        String body = resultMono.block();

        System.out.println("statusCode：" + statusCode);
        System.out.println("headers：" + headers.asHttpHeaders());
        System.out.println("body：" + body);

    }

    @Test
    public void postMonoTest1() throws Exception {
        String url = "http://127.0.0.1:30000/longtime";
        String body = "{}";

        CountDownLatch latch = new CountDownLatch(1);

        WebClientUtil.postMono(url, body, null)
                .flatMap(response ->
                        response.bodyToMono(String.class)
                                .map(b -> "Status: " + response.statusCode() + ", Body: " + b)
                )
                .doOnError(e -> {
                    System.err.println("异常: " + e.getMessage());
                })
                .doFinally(s -> {
                    System.out.println("异步流程全部结束: " + s);
                    latch.countDown();
                })
                .subscribe(result -> {
                    System.out.println("成功: " + result);
                });
        System.out.println("主线程执行中!");
        latch.await(10, TimeUnit.SECONDS);
    }

    @Test
    public void postMonoTest2() throws Exception {
        String url = "http://127.0.0.1:30000/longtime";
        String requestBody = "{}";

        int requestCount = 1000;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        long start = System.currentTimeMillis();

        for (int i = 0; i < requestCount; i++) {
            int finalI = i;
            CompletableFuture<Void> future =
                    WebClientUtil.postMono(url, requestBody, null)
                            .flatMap(response ->
                                    response.bodyToMono(String.class)
                                            .map(body -> new WebClientBodyDTO(HttpStatus.valueOf(response.statusCode().value()), "200", body))
                                            .onErrorResume(e -> Mono.just(new WebClientBodyDTO(HttpStatus.valueOf(response.statusCode().value()), e.getMessage(), null)))
                            )
                            .toFuture()
                            .thenAccept(dto -> {
                                if (!dto.getCode().isError()) {
                                    successCount.incrementAndGet();
                                } else {
                                    errorCount.incrementAndGet();
                                }
                                if((finalI+1) % 100 == 0) {
                                    System.out.println(finalI + "次已执行");
                                }
                            })
                            .exceptionally(e -> {
                                errorCount.incrementAndGet();
                                return null;
                            });

            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(30, TimeUnit.SECONDS)
                .join();

        long end = System.currentTimeMillis();

        System.out.println("===================================");
        System.out.println("总请求数: " + requestCount);
        System.out.println("成功: " + successCount.get());
        System.out.println("失败: " + errorCount.get());
        System.out.println("耗时(ms): " + (end - start));
        System.out.println("===================================");
    }

    @Test
    public void postMonoTest3() throws Exception {
        System.out.println(RateLimitedUtil.getRateLimitStatus());

        String url = "http://127.0.0.1:30000/longtime";
        String requestBody = "{}";

        int requestCount = 11;
        long start = System.currentTimeMillis();

        CountDownLatch latch = new CountDownLatch(requestCount);

        for (int i = 0; i < requestCount; i++) {
            RateLimitedUtil.executeWithRateLimit(() ->
                    WebClientUtil.postMono(url, requestBody, null)
            ).flatMap(response -> {
                if (response instanceof ClientResponse) {
                    ClientResponse clientResponse = (ClientResponse) response;
                    return clientResponse.bodyToMono(String.class)
                            .map(body -> new WebClientBodyDTO(HttpStatus.valueOf(response.statusCode().value()), "200", body));
                }
                return Mono.just(response.toString());
            }).subscribe(result -> {
                System.out.println("请求返回值: " + result);
                latch.countDown();
            }, error -> {
                latch.countDown();
            });
        }

        // 等待所有请求完成
        latch.await();

        long end = System.currentTimeMillis();

        System.out.println("===================================");
        System.out.println("总请求数: " + requestCount);
        System.out.println("耗时(ms): " + (end - start));
        System.out.println("当前限流状态: " + RateLimitedUtil.getRateLimitStatus());
        System.out.println("===================================");
    }


    /**
     * 类似转发操作
     *
     * @throws Exception
     */
    public void postBodyObjectSimpleTest() throws Exception {
        // spring.codec.max-in-memory-size = 10485760

        //    @PostMapping(value = "forward", produces="application/json;charset=UTF-8")
        //    @ResponseBody
        //    public String forward(@RequestBody Object obj) {
        //        return WebClientUtil.postBodyObjectSimple("forward-url", obj, null, 10*1024*1024);
        //    }


        System.out.println("postBodyObjectSimpleTest");
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


}
