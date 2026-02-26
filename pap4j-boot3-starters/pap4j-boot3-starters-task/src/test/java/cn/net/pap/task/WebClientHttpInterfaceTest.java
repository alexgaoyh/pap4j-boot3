package cn.net.pap.task;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;

@SpringBootTest(classes = WebClientHttpInterfaceTest.Config.class)
public class WebClientHttpInterfaceTest {

    @Autowired
    private JsonPlaceHolderApi api;

    @Test
    public void testWebClientHttpInterface() {
        String result = api.getPost(1).block();
        System.out.println(result);
    }

    /**
     * Http Interface 定义
     */
    @HttpExchange(url = "https://jsonplaceholder.typicode.com")
    interface JsonPlaceHolderApi {

        @GetExchange(value = "/posts/{id}", accept = MediaType.APPLICATION_JSON_VALUE)
        Mono<String> getPost(@PathVariable("id") int id);

    }

    @Configuration
    static class Config {

        @Bean
        public WebClient webClient() {
            return WebClient.builder()
                    .build();
        }

        @Bean
        public JsonPlaceHolderApi jsonPlaceHolderApi(WebClient webClient) {
            HttpServiceProxyFactory factory =
                    HttpServiceProxyFactory.builderFor(WebClientAdapter.create(webClient)).build();

            return factory.createClient(JsonPlaceHolderApi.class);
        }
    }
}
