package cn.net.pap.task;

import cn.net.pap.task.dto.RetryWebClientResponseDTO;
import cn.net.pap.task.retry.RetryCircuitBreaker;
import cn.net.pap.task.retry.exception.RetryCircuitBreakerException;
import cn.net.pap.task.retry.exception.enums.PapRetryErrorEnum;
import cn.net.pap.task.webclient.WebClientUtil;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

public class RetryWebClientTest {

    /**
     * 外部定义 重试滑动窗口断路器
     */
    private static final RetryCircuitBreaker retryCircuitBreaker = new RetryCircuitBreaker(3,1000, 5, 60000, 10000);

    /**
     * 针对网络请求进行重试逻辑，并且根据网络请求的响应头信息，来进行不同的处理处理。
     *
     * 当前方法可以测试出来：
     * 1、基于滑动窗口的重试机制
     * 2、网络请求如果返回了不再重试的响应头信息，则直接进行返回
     */
    // @Test
    public void test() {

        try {
            AtomicReference<HttpHeaders> headersTmp = new AtomicReference<>();

            String result = retryCircuitBreaker.executeWithRetry(() -> {
                // 每次网络请求都会携带上一个网络请求的响应头信息，比如当前可以测试出来 pap-trace-id 信息，进而链路分析.
                RetryWebClientResponseDTO retryWebClientResponseDTO = httpPost(headersTmp.get());
                headersTmp.set(retryWebClientResponseDTO.getHeaders());
                if(retryWebClientResponseDTO.getHeaders() != null
                        && !retryWebClientResponseDTO.getHeaders().isEmpty()
                        && retryWebClientResponseDTO.getHeaders().get("pap-retry-code") != null
                        && retryWebClientResponseDTO.getHeaders().getFirst("pap-retry-code").toString().equals("NoRetry")) {
                    // 这里说明响应头里面返回了不再重试，则直接将信息进行返回.
                    return retryWebClientResponseDTO.getMessage();
                } else {
                    if (retryWebClientResponseDTO.getCode() == 200) {
                        return retryWebClientResponseDTO.getMessage();
                    } else {
                        throw new RetryCircuitBreakerException(PapRetryErrorEnum.RETRY_FINAL_FAILURE);
                    }
                }
            });

            System.out.println(result);
        } catch (Exception e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    /**
     * 实际的网络请求
     * @param headersTmp
     * @return
     */
    private RetryWebClientResponseDTO httpPost(HttpHeaders headersTmp) {
        ClientResponse response = WebClientUtil.postBody("http://127.0.0.1:30000/timeout", "{}", headersTmp);

        HttpStatusCode statusCode = response.statusCode();
        ClientResponse.Headers headers = response.headers();

        Mono<String> resultMono = response.bodyToMono(String.class);
        String body = resultMono.block();

        RetryWebClientResponseDTO retryWebClientResponseDTO = new RetryWebClientResponseDTO();
        retryWebClientResponseDTO.setCode(statusCode.value());
        retryWebClientResponseDTO.setRetryCode("Retry");
        if(headers.asHttpHeaders() == null && !headers.asHttpHeaders().isEmpty() && headers.asHttpHeaders().get("pap-retry-code") != null) {
            retryWebClientResponseDTO.setRetryCode(headers.asHttpHeaders().get("pap-retry-code").toString());
        }
        retryWebClientResponseDTO.setMessage(body);
        retryWebClientResponseDTO.setHeaders(headers.asHttpHeaders());

        return retryWebClientResponseDTO;
    }
}
