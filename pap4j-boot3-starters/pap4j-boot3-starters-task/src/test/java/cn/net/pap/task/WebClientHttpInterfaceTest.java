package cn.net.pap.task;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest(classes = WebClientHttpInterfaceTest.Config.class)
@org.springframework.test.context.TestConstructor(autowireMode = org.springframework.test.context.TestConstructor.AutowireMode.ALL)
public class WebClientHttpInterfaceTest {

    private final JsonPlaceHolderApi api;

    public WebClientHttpInterfaceTest(JsonPlaceHolderApi api) {
        this.api = api;
    }

    @Test
    public void testWebClientHttpInterface() {
        try {
            Post result = api.getPost(1).block();
            assertThat(result.userId != 0);
        } catch (Exception e) {

        }
    }

    /**
     * Http Interface 定义
     */
    @HttpExchange(url = "https://jsonplaceholder.typicode.com")
    interface JsonPlaceHolderApi {

        @GetExchange(value = "/posts/{id}", accept = MediaType.APPLICATION_JSON_VALUE)
        Mono<Post> getPost(@PathVariable("id") int id);

    }

    @Configuration
    static class Config {

        @Bean
        public WebClient webClient() {
            return WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().responseTimeout(Duration.ofSeconds(5))))
                    .build();
        }

        @Bean
        public JsonPlaceHolderApi jsonPlaceHolderApi(WebClient webClient) {
            HttpServiceProxyFactory factory =
                    HttpServiceProxyFactory.builderFor(WebClientAdapter.create(webClient)).build();

            return factory.createClient(JsonPlaceHolderApi.class);
        }
    }

    record Post(int userId, int id, String title, String body) {
    }

}
