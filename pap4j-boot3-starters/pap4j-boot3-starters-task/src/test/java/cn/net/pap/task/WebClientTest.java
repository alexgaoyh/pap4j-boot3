package cn.net.pap.task;

import cn.net.pap.task.webclient.WebClientUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

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
