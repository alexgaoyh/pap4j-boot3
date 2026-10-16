package cn.net.pap.example.nginx.support;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

class NginxVaryWebFilterTest {

    @Test
    void addsVaryAcceptEncodingWhenCompressionEnabled() {
        WebTestClient client = WebTestClient.bindToWebHandler(exchange -> exchange.getResponse().setComplete())
                .webFilter(new NginxVaryWebFilter(true))
                .build();
        client.get().uri("/x").exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches(HttpHeaders.VARY, ".*Accept-Encoding.*");
    }

    @Test
    void doesNotAddVaryWhenCompressionDisabled() {
        WebTestClient client = WebTestClient.bindToWebHandler(exchange -> exchange.getResponse().setComplete())
                .webFilter(new NginxVaryWebFilter(false))
                .build();
        client.get().uri("/x").exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist(HttpHeaders.VARY);
    }

}
