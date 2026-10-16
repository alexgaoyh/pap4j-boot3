package cn.net.pap.example.nginx.support;

import cn.net.pap.example.nginx.config.NginxProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProxyHeaderSupportTest {

    @Test
    void copiesForwardingHeadersAndDropsHopByHop() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/x")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                .header("X-Forwarded-For", "10.0.0.1")
                .header("Connection", "keep-alive")
                .header("Host", "example.com")
                .build();
        HttpHeaders target = new HttpHeaders();
        ProxyHeaderSupport.copyRequestHeaders(request, target);
        assertEquals("example.com", target.getFirst(HttpHeaders.HOST));
        assertEquals("example.com", target.getFirst("X-Forwarded-Host"));
        assertEquals("80", target.getFirst("X-Forwarded-Port"));
        assertEquals("10.0.0.1, 127.0.0.1", target.getFirst("X-Forwarded-For"));
        assertEquals("127.0.0.1", target.getFirst("X-Real-IP"));
        assertEquals("127.0.0.1", target.getFirst("REMOTE-HOST"));
        assertEquals("http", target.getFirst("X-Forwarded-Proto"));
        assertNull(target.getFirst("Connection"));
    }

    @Test
    void dropsDynamicHopByHopHeadersFromConnectionToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/x")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                .header("Connection", "close, X-Custom-Hop, X-Another-Hop")
                .header("X-Custom-Hop", "secret")
                .header("X-Another-Hop", "discard")
                .header("X-Keep", "remain")
                .build();
        HttpHeaders target = new HttpHeaders();
        ProxyHeaderSupport.copyRequestHeaders(request, target);
        assertNull(target.getFirst("Connection"));
        assertNull(target.getFirst("X-Custom-Hop"));
        assertNull(target.getFirst("X-Another-Hop"));
        assertEquals("remain", target.getFirst("X-Keep"));
    }

    @Test
    void sanitizesForwardedProtoAndExtractsPort() {
        MockServerHttpRequest request = MockServerHttpRequest.get("https://example.com:8443/api/x")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                .header("X-Forwarded-Proto", "invalid-scheme")
                .build();
        HttpHeaders target = new HttpHeaders();
        ProxyHeaderSupport.copyRequestHeaders(request, target);
        assertEquals("https", target.getFirst("X-Forwarded-Proto"));
        assertEquals("8443", target.getFirst("X-Forwarded-Port"));
    }

    @Test
    void ignoresClientForwardedProtoByDefault() {
        MockServerHttpRequest request = MockServerHttpRequest.get("http://example.com/api/x")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                .header("X-Forwarded-Proto", "https")
                .build();
        HttpHeaders target = new HttpHeaders();
        ProxyHeaderSupport.copyRequestHeaders(request, target);
        assertEquals("http", target.getFirst("X-Forwarded-Proto"));
    }

    @Test
    void trustsClientForwardedProtoWhenConfigured() {
        MockServerHttpRequest request = MockServerHttpRequest.get("http://example.com/api/x")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                .header("X-Forwarded-Proto", "https")
                .build();
        HttpHeaders target = new HttpHeaders();
        ProxyHeaderSupport.copyRequestHeaders(request, target, true);
        assertEquals("https", target.getFirst("X-Forwarded-Proto"));
    }

    @Test
    void appliesCorsOnlyForAllowedOrigin() {
        NginxProperties.Cors cors = cors();
        HttpHeaders allowed = new HttpHeaders();
        ProxyHeaderSupport.applyCors(allowed, cors, "http://ok.example", false);
        assertEquals("http://ok.example", allowed.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("true", allowed.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertEquals("Content-Disposition", allowed.getFirst(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));

        HttpHeaders denied = new HttpHeaders();
        ProxyHeaderSupport.applyCors(denied, cors, "http://evil.example", false);
        assertNull(denied.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void preflightAddsMethodsHeadersAndVaryWithoutDuplication() {
        NginxProperties.Cors cors = cors();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.VARY, HttpHeaders.ORIGIN);
        ProxyHeaderSupport.applyCors(headers, cors, "http://ok.example", true);
        assertEquals(1, headers.getOrEmpty(HttpHeaders.VARY).size());
        assertEquals("GET, POST", headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
        assertEquals("Content-Type", headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));
    }

    private static NginxProperties.Cors cors() {
        NginxProperties.Cors cors = new NginxProperties.Cors();
        cors.setEnabled(true);
        cors.setAllowedOrigins(List.of("http://ok.example"));
        cors.setAllowedMethods(List.of("GET", "POST"));
        cors.setAllowedHeaders(List.of("Content-Type"));
        cors.setExposeHeaders(List.of("Content-Disposition"));
        return cors;
    }

}
