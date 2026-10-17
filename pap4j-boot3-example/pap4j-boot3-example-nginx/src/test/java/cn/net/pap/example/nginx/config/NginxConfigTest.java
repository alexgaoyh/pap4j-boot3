package cn.net.pap.example.nginx.config;

import cn.net.pap.example.nginx.support.NginxProxyRoute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.netty.resources.ConnectionProvider;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class NginxConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void proxyConfigBuildsProviderAndRoutes() {
        NginxProxyConfig config = new NginxProxyConfig();
        NginxProperties properties = new NginxProperties();

        NginxProperties.Proxy shorter = new NginxProperties.Proxy();
        shorter.setPath("/api");
        shorter.setTarget("http://127.0.0.1:8088");

        NginxProperties.Proxy longer = new NginxProperties.Proxy();
        longer.setPath("/api/v2");
        longer.setTarget("http://127.0.0.1:8089");

        properties.setProxies(List.of(shorter, longer));

        ConnectionProvider provider = config.proxyConnectionProvider(properties);
        assertNotNull(provider);

        List<NginxProxyRoute> routes = config.nginxProxyRoutes(properties, provider);
        assertEquals(2, routes.size());
        assertEquals("/api/v2", routes.get(0).getLocationPath());
        assertEquals("/api", routes.get(1).getLocationPath());

        provider.dispose();
    }

    @Test
    void staticConfigBuildsRouterFunction() {
        NginxStaticConfig config = new NginxStaticConfig();
        NginxProperties properties = new NginxProperties();

        // 1. Resources disabled (no routes registered throws IllegalStateException)
        properties.getResources().setEnabled(false);
        assertThrows(IllegalStateException.class, () -> config.nginxRouterFunction(properties));


        // 2. Resources enabled with root and subpath
        properties.getResources().setEnabled(true);
        NginxProperties.Location locRoot = new NginxProperties.Location();
        locRoot.setPath("/");
        locRoot.setRoot(tempDir.toString());

        NginxProperties.Location locDocs = new NginxProperties.Location();
        locDocs.setPath("/docs");
        locDocs.setRoot(tempDir.toString());

        NginxProperties.Location locDisabled = new NginxProperties.Location();
        locDisabled.setPath("/disabled");
        locDisabled.setRoot(tempDir.toString());
        locDisabled.setEnabled(false);

        properties.getResources().setLocations(List.of(locRoot, locDocs, locDisabled));
        RouterFunction<ServerResponse> router = config.nginxRouterFunction(properties);

        // Subpath match
        MockServerRequest requestDocs = MockServerRequest.builder().uri(java.net.URI.create("/docs/guide")).build();
        Optional<HandlerFunction<ServerResponse>> handlerDocs = router.route(requestDocs).blockOptional();
        assertTrue(handlerDocs.isPresent());

        // Disabled location should not route specifically
        MockServerRequest requestDisabled = MockServerRequest.builder().uri(java.net.URI.create("/disabled/test")).build();
        Optional<HandlerFunction<ServerResponse>> handlerDisabledLoc = router.route(requestDisabled).blockOptional();
        // Since "/" covers everything, it still routes to the root handler
        assertTrue(handlerDisabledLoc.isPresent());
    }

}
