package cn.net.pap.example.nginx.support;

import cn.net.pap.example.nginx.config.NginxProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class NginxProxyWebFilterTest {

    @Test
    void preflightReturnsCorsHeaders() {
        WebTestClient client = client(proxy("http://127.0.0.1:1"));
        client.options().uri("/api/x")
                .header("Origin", "http://127.0.0.1:39001")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://127.0.0.1:39001")
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");
    }

    @Test
    void upstreamDownReturnsBadGateway() {
        WebTestClient client = client(proxy("http://127.0.0.1:1"));
        client.get().uri("/api/x").exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void oversizedBodyReturnsPayloadTooLarge() {
        NginxProperties.Proxy proxy = proxy("http://127.0.0.1:1");
        proxy.setMaxRequestSize(DataSize.ofBytes(4));
        WebTestClient client = client(proxy);
        client.post().uri("/api/save").bodyValue("1234567890").exchange()
                .expectStatus().isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void matchesLongestPrefixFirstRegardlessOfInputOrder(@TempDir Path tempDir) throws IOException {
        Path shortErr = tempDir.resolve("short.html");
        Path longErr = tempDir.resolve("long.html");
        Files.writeString(shortErr, "SHORT-ERROR");
        Files.writeString(longErr, "LONG-ERROR");

        NginxProperties.Proxy shorter = proxy("http://127.0.0.1:1");
        shorter.setPath("/api");
        shorter.setErrorPage(shortErr.toString());

        NginxProperties.Proxy longer = proxy("http://127.0.0.1:2");
        longer.setPath("/api/v2");
        longer.setErrorPage(longErr.toString());

        NginxProxyRoute routeShort = new NginxProxyRoute(shorter, WebClient.builder().build());
        NginxProxyRoute routeLong = new NginxProxyRoute(longer, WebClient.builder().build());
        NginxProxyWebFilter filter = new NginxProxyWebFilter(List.of(routeShort, routeLong));

        WebTestClient client = WebTestClient.bindToWebHandler(exchange -> exchange.getResponse().setComplete())
                .webFilter(filter)
                .build();

        client.get().uri("/api/v2/items").exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectBody(String.class).isEqualTo("LONG-ERROR");
    }

    @Test
    void forwardsNonPreflightOptionsRequestToUpstream() {
        WebTestClient client = client(proxy("http://127.0.0.1:1"));
        client.options().uri("/api/x")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void errorResponseIncludesCorsHeadersForAllowedOrigin() {
        WebTestClient client = client(proxy("http://127.0.0.1:1"));
        client.get().uri("/api/x")
                .header("Origin", "http://127.0.0.1:39001")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://127.0.0.1:39001");
    }

    @Test
    void payloadTooLargeIncludesCorsHeadersForAllowedOrigin() {
        NginxProperties.Proxy proxy = proxy("http://127.0.0.1:1");
        proxy.setMaxRequestSize(DataSize.ofBytes(4));
        WebTestClient client = client(proxy);
        client.post().uri("/api/save")
                .header("Origin", "http://127.0.0.1:39001")
                .bodyValue("1234567890")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://127.0.0.1:39001");
    }


    @Test
    void upstreamDownWithMissingErrorPageReturnsEmptyBody() {
        NginxProperties.Proxy proxy = proxy("http://127.0.0.1:1");
        proxy.setErrorPage("/non/existent/file.html");
        WebTestClient client = client(proxy);
        client.get().uri("/api/x").exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectBody().isEmpty();
    }


    private static WebTestClient client(NginxProperties.Proxy proxy) {
        NginxProxyRoute route = new NginxProxyRoute(proxy, WebClient.builder().build());
        NginxProxyWebFilter filter = new NginxProxyWebFilter(List.of(route));
        return WebTestClient.bindToWebHandler(exchange -> exchange.getResponse().setComplete())
                .webFilter(filter)
                .build();
    }

    private static NginxProperties.Proxy proxy(String target) {
        NginxProperties.Proxy proxy = new NginxProperties.Proxy();
        proxy.setPath("/api");
        proxy.setTarget(target);
        NginxProperties.Cors cors = new NginxProperties.Cors();
        cors.setEnabled(true);
        cors.setAllowedOrigins(List.of("http://127.0.0.1:39001"));
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type"));
        proxy.setCors(cors);
        return proxy;
    }

}
