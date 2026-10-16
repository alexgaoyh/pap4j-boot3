package cn.net.pap.example.nginx.support;

import cn.net.pap.example.nginx.config.NginxProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NginxProxyRouteTest {

    private static NginxProxyRoute route(String locationPath, String target) {
        NginxProperties.Proxy proxy = new NginxProperties.Proxy();
        proxy.setPath(locationPath);
        proxy.setTarget(target);
        return new NginxProxyRoute(proxy, WebClient.builder().build());
    }

    @Test
    void matchesByPathSegment() {
        NginxProxyRoute route = route("/api", "http://127.0.0.1:8088");
        assertTrue(route.matches("/api"));
        assertTrue(route.matches("/api/orders"));
        assertFalse(route.matches("/apix"));
        assertFalse(route.matches("/other"));
        assertFalse(route.matches(null));
    }

    @Test
    void buildsTargetUriPreservingPathAndQuery() {
        NginxProxyRoute route = route("/api", "http://127.0.0.1:8088");
        URI uri = route.buildTargetUri(MockServerHttpRequest.get("/api/orders?a=1").build()).orElseThrow();
        assertEquals("http://127.0.0.1:8088/api/orders?a=1", uri.toString());
    }

    @Test
    void replacesLocationPrefixWhenTargetHasPath() {
        NginxProxyRoute route = route("/api", "http://127.0.0.1:8088/backend");
        URI uri = route.buildTargetUri(MockServerHttpRequest.get("/api/orders").build()).orElseThrow();
        assertEquals("http://127.0.0.1:8088/backend/orders", uri.toString());
    }

    @Test
    void rejectsPlainTraversal() {
        NginxProxyRoute route = route("/api", "http://127.0.0.1:8088");
        assertTrue(route.buildTargetUri(MockServerHttpRequest.get("/api/../admin").build()).isEmpty());
    }

    @Test
    void rejectsEncodedTraversal() {
        NginxProxyRoute route = route("/api", "http://127.0.0.1:8088");
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, URI.create("/api/%2e%2e/admin")).build();
        assertTrue(route.buildTargetUri(request).isEmpty());
    }

    @Test
    void rejectsNullByteTraversal() {
        NginxProxyRoute route = route("/api", "http://127.0.0.1:8088");
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, URI.create("/api/test%00admin")).build();
        assertTrue(route.buildTargetUri(request).isEmpty());
    }


    @Test
    void testGetters() {
        NginxProxyRoute route = route("/api", "http://127.0.0.1:8088");
        assertEquals("/api", route.getLocationPath());
        assertEquals("http://127.0.0.1:8088", route.getProxy().getTarget());
        org.junit.jupiter.api.Assertions.assertNotNull(route.getWebClient());
    }

}

