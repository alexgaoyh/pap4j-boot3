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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
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
                .flatMap(response -> {
                    // 显式处理 HTTP 非 2xx 状态
                    if (!response.statusCode().is2xxSuccessful()) {
                        return Mono.error(new RuntimeException("HTTP错误: " + response.statusCode()));
                    }
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("") // 处理空响应体
                            .map(b -> "Status: " + response.statusCode() + ", Body: " + b);
                })
                .doOnError(e -> {
                    System.err.println("异常: " + e.getMessage());
                })
                .doFinally(s -> {
                    System.out.println("异步流程全部结束: " + s);
                    latch.countDown();
                })
                .subscribe(
                        result -> {
                            System.out.println("成功: " + result);
                        },
                        error -> {
                            // 显式错误处理
                            System.err.println("订阅时发生错误: " + error.getMessage());
                        }
                );
        System.out.println("主线程执行中!");
        boolean await = latch.await(10, TimeUnit.SECONDS);
        if (!await) {
            System.err.println("等待超时，异步请求可能未完成!");
        }
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
                                if ((finalI + 1) % 100 == 0) {
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

    /**
     *     @GetMapping("/first")
     *     public String first(HttpServletResponse resp) throws IOException {
     *         Cookie userCookie = new Cookie("username", "alexgaoyh");
     *         userCookie.setMaxAge(24 * 60 * 60); // 1天
     *         userCookie.setPath("/");
     *         Cookie tokenCookie = new Cookie("token", "pap.net.cn");
     *         tokenCookie.setHttpOnly(true);
     *         tokenCookie.setMaxAge(30 * 60); // 30分钟
     *         tokenCookie.setPath("/");
     *         resp.addCookie(userCookie);
     *         resp.addCookie(tokenCookie);
     *         return "success";
     *     }
     *
     *     @GetMapping("/second")
     *     public String second(HttpServletRequest request) {
     *         String resultStr = "";
     *         Cookie[] cookies = request.getCookies();
     *         if(cookies != null) {
     *             for(Cookie cookie : cookies) {
     *                 resultStr = resultStr + cookie.getName().toString() + " : " + cookie.getValue().toString() + " ; ";
     *             }
     *         }
     *         return resultStr;
     *     }
     * @throws Exception
     */
    @Test
    public void httpClientCookieTest() throws Exception {
        // CookieManager负责存储与匹配Cookie,它是Java提供的标准实现,类似于浏览器的“Cookie 存储”.当HttpClient收到响应时,它会查看是否有Set-Cookie.如果有就调用 CookieManager的put()方法把它保存。
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);

        // 构建 HttpClient 并绑定 Cookie 管理器
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookieManager).build();

        // 第一次请求：写入 cookie
        HttpRequest request1 = HttpRequest.newBuilder().uri(URI.create("http://localhost:30000/first")).GET().build();
        HttpResponse<String> response1 = client.send(request1, HttpResponse.BodyHandlers.ofString());
        System.out.println("Response 1: " + response1.body());

        // 第二次请求：自动带上 cookie
        HttpRequest request2 = HttpRequest.newBuilder().uri(URI.create("http://localhost:30000/second")).GET().build();
        HttpResponse<String> response2 = client.send(request2, HttpResponse.BodyHandlers.ofString());
        System.out.println("Response 2: " + response2.body());
    }

    /**
     * 高频异步接口调用单元测试
     *
     * 说明：
     * 1. 使用 Reactor 的 Flux 实现异步、高频调用。
     * 2. 每个请求使用 WebClientUtil.postMono 封装的静态方法。
     * 3. 并行数由 parallelism 控制，防止线程过载。
     * 4. delayElements(intervalMillis) 控制每个请求的发送间隔，实现持续高频请求。
     * 5. 成功与失败请求分别计数，并将每个请求的返回结果存入线程安全列表 responseList。
     * 6. blockLast() 阻塞等待所有请求完成，用于单元测试环境同步输出统计结果。
     */
    @Test
    public void highFrequencyAsyncTest() {
        String url = "http://127.0.0.1:30000/longtime";
        int requestCount = 500;       // 总请求数
        int parallelism = 20;         // 最大并发数
        long intervalMillis = 50;     // 每个请求间隔 50ms

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        List<WebClientBodyDTO> responseList = new CopyOnWriteArrayList<>();

        long startTime = System.currentTimeMillis();

        // 生成动态参数的 Flux
        Flux.range(0, requestCount)
                .delayElements(Duration.ofMillis(intervalMillis)) // 高频请求间隔
                .parallel(parallelism)
                .runOn(Schedulers.boundedElastic())
                .flatMap(i -> {
                    String requestBody = "{\"param\":\"value" + i + "\"}"; // 动态请求体
                    return WebClientUtil.postMono(url, requestBody, null)
                            .flatMap(response -> handleResponse(response))
                            .onErrorResume(e -> {
                                errorCount.incrementAndGet();
                                WebClientBodyDTO dto = new WebClientBodyDTO(null, e.getMessage(), null);
                                responseList.add(dto);
                                return Mono.empty();
                            });
                })
                .sequential()
                .doOnNext(dto -> {
                    responseList.add(dto);
                    if (!dto.getCode().isError()) {
                        successCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                })
                .blockLast(); // 阻塞等待所有请求完成

        long endTime = System.currentTimeMillis();

        System.out.println("===================================");
        System.out.println("总请求数: " + requestCount);
        System.out.println("成功: " + successCount.get());
        System.out.println("失败: " + errorCount.get());
        System.out.println("耗时(ms): " + (endTime - startTime));
        System.out.println("===================================");

        for (int i = 0; i < responseList.size(); i++) {
            WebClientBodyDTO dto = responseList.get(i);
            System.out.println("请求 #" + (i + 1) + " -> Code: " + dto.getCode() + ", Msg: " + dto.getMsg());
        }
    }

    @Test
    public void highFrequencyLoopTest() throws Exception {
        String url = "http://127.0.0.1:30000/longtime";
        int requestCount = 500; // 请求次数
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<WebClientBodyDTO> responseList = new CopyOnWriteArrayList<>();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < requestCount; i++) {
            String requestBody = "{\"param\":\"value" + i + "\"}";

            // 异步调用
            WebClientUtil.postMono(url, requestBody, null)
                    .flatMap(response -> handleResponse(response))
                    .doOnNext(dto -> {
                        responseList.add(dto);
                        if (!dto.getCode().isError()) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }
                    })
                    .onErrorResume(e -> {
                        errorCount.incrementAndGet();
                        responseList.add(new WebClientBodyDTO(null, e.getMessage(), null));
                        return Mono.empty();
                    })
                    .block(); // 如果希望同步等待每次请求完成，可以保留 block()
        }

        long endTime = System.currentTimeMillis();

        System.out.println("===================================");
        System.out.println("总请求数: " + requestCount);
        System.out.println("成功: " + successCount.get());
        System.out.println("失败: " + errorCount.get());
        System.out.println("耗时(ms): " + (endTime - startTime));
        System.out.println("===================================");

        // 输出请求结果
        for (int i = 0; i < responseList.size(); i++) {
            WebClientBodyDTO dto = responseList.get(i);
            System.out.println("请求 #" + (i + 1) + " -> Code: " + dto.getCode() + ", Msg: " + dto.getMsg());
        }
    }


    private Mono<WebClientBodyDTO> handleResponse(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new WebClientBodyDTO(
                        org.springframework.http.HttpStatus.valueOf(response.statusCode().value()), body, null
                ))
                .onErrorResume(e -> Mono.just(new WebClientBodyDTO(
                        org.springframework.http.HttpStatus.valueOf(response.statusCode().value()), e.getMessage(), null
                )));
    }

}
