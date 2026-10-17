package cn.net.pap.example.nginx.config;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NginxPropertiesTest {

    private static NginxProperties createValidProperties() {
        NginxProperties properties = new NginxProperties();

        NginxProperties.Location location = new NginxProperties.Location();
        location.setPath("/");
        location.setRoot("D:/static");
        location.setEnabled(true);
        location.setIndex(List.of("index.html"));
        location.setSpaFallback("/index.html");
        location.setCacheControl("no-cache");
        location.setCharset("utf-8");
        properties.getResources().setLocations(List.of(location));
        properties.getResources().setEnabled(true);

        NginxProperties.Proxy proxy = new NginxProperties.Proxy();
        proxy.setPath("/api");
        proxy.setTarget("http://127.0.0.1:8088");
        proxy.setConnectTimeout(Duration.ofSeconds(3));
        proxy.setResponseTimeout(Duration.ofSeconds(30));
        proxy.setReadTimeout(Duration.ofSeconds(30));
        proxy.setMaxRequestSize(DataSize.ofMegabytes(1));
        proxy.setErrorPage("/502.html");
        proxy.setTrustForwardedHeaders(false);

        NginxProperties.Cors cors = new NginxProperties.Cors();
        cors.setEnabled(true);
        cors.setAllowedOrigins(List.of("http://localhost:3000"));
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "POST"));
        cors.setAllowedHeaders(List.of("Authorization"));
        cors.setExposeHeaders(List.of("X-Total-Count"));
        cors.setMaxAge(Duration.ofHours(1));
        proxy.setCors(cors);

        properties.setProxies(List.of(proxy));

        NginxProperties.Pool pool = new NginxProperties.Pool();
        pool.setMaxConnections(500);
        pool.setPendingAcquireMaxCount(1000);
        pool.setPendingAcquireTimeout(Duration.ofSeconds(30));
        pool.setMaxIdleTime(Duration.ofSeconds(30));
        properties.setPool(pool);

        return properties;
    }

    @Test
    void validatePassesOnValidProperties() {
        NginxProperties properties = createValidProperties();
        assertDoesNotThrow(properties::validate);
    }

    @Test
    void validateFailsWhenProxyPathEmpty() {
        NginxProperties properties = createValidProperties();
        properties.getProxies().get(0).setPath("");
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void validateFailsWhenProxyTargetEmpty() {
        NginxProperties properties = createValidProperties();
        properties.getProxies().get(0).setTarget(null);
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void validateFailsWhenProxyTimeoutsNonPositive() {
        NginxProperties p1 = createValidProperties();
        p1.getProxies().get(0).setConnectTimeout(Duration.ZERO);
        assertThrows(IllegalStateException.class, p1::validate);

        NginxProperties p2 = createValidProperties();
        p2.getProxies().get(0).setResponseTimeout(Duration.ofSeconds(-1));
        assertThrows(IllegalStateException.class, p2::validate);

        NginxProperties p3 = createValidProperties();
        p3.getProxies().get(0).setReadTimeout(null);
        assertThrows(IllegalStateException.class, p3::validate);
    }

    @Test
    void validateFailsWhenMaxRequestSizeNonPositive() {
        NginxProperties p1 = createValidProperties();
        p1.getProxies().get(0).setMaxRequestSize(null);
        assertThrows(IllegalStateException.class, p1::validate);

        NginxProperties p2 = createValidProperties();
        p2.getProxies().get(0).setMaxRequestSize(DataSize.ofBytes(0));
        assertThrows(IllegalStateException.class, p2::validate);
    }

    @Test
    void validateFailsWhenCorsWildcardWithCredentials() {
        NginxProperties properties = createValidProperties();
        properties.getProxies().get(0).getCors().setAllowedOrigins(List.of("*"));
        properties.getProxies().get(0).getCors().setAllowCredentials(true);
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void validateFailsWhenLocationRootEmpty() {
        NginxProperties properties = createValidProperties();
        properties.getResources().getLocations().get(0).setRoot("");
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void validateFailsWhenPoolParametersNonPositive() {
        NginxProperties p1 = createValidProperties();
        p1.getPool().setMaxConnections(0);
        assertThrows(IllegalStateException.class, p1::validate);

        NginxProperties p2 = createValidProperties();
        p2.getPool().setPendingAcquireMaxCount(-1);
        assertThrows(IllegalStateException.class, p2::validate);

        NginxProperties p3 = createValidProperties();
        p3.getPool().setPendingAcquireTimeout(Duration.ZERO);
        assertThrows(IllegalStateException.class, p3::validate);

        NginxProperties p4 = createValidProperties();
        p4.getPool().setMaxIdleTime(null);
        assertThrows(IllegalStateException.class, p4::validate);
    }

    @Test
    void testGettersAndSetters() {
        NginxProperties properties = createValidProperties();
        assertTrue(properties.getResources().isEnabled());
        assertEquals(1, properties.getResources().getLocations().size());
        assertEquals(1, properties.getProxies().size());
        assertEquals(500, properties.getPool().getMaxConnections());
        assertEquals(1000, properties.getPool().getPendingAcquireMaxCount());
        assertEquals(Duration.ofSeconds(30), properties.getPool().getPendingAcquireTimeout());
        assertEquals(Duration.ofSeconds(30), properties.getPool().getMaxIdleTime());

        NginxProperties.Location loc = properties.getResources().getLocations().get(0);
        assertTrue(loc.isEnabled());
        assertEquals("/", loc.getPath());
        assertEquals("D:/static", loc.getRoot());
        assertEquals(List.of("index.html"), loc.getIndex());
        assertEquals("/index.html", loc.getSpaFallback());
        assertEquals("no-cache", loc.getCacheControl());
        assertEquals("utf-8", loc.getCharset());

        NginxProperties.Proxy proxy = properties.getProxies().get(0);
        assertEquals("/api", proxy.getPath());
        assertEquals("http://127.0.0.1:8088", proxy.getTarget());
        assertEquals(Duration.ofSeconds(3), proxy.getConnectTimeout());
        assertEquals(Duration.ofSeconds(30), proxy.getResponseTimeout());
        assertEquals(Duration.ofSeconds(30), proxy.getReadTimeout());
        assertEquals(DataSize.ofMegabytes(1), proxy.getMaxRequestSize());
        assertEquals("/502.html", proxy.getErrorPage());
        assertFalse(proxy.isTrustForwardedHeaders());

        NginxProperties.Cors cors = proxy.getCors();
        assertTrue(cors.isEnabled());
        assertTrue(cors.isAllowCredentials());
        assertTrue(cors.isAllowedOrigin("http://localhost:3000"));
        assertFalse(cors.isAllowedOrigin("http://evil.com"));
        assertFalse(cors.isAllowedOrigin(null));
        assertEquals(List.of("http://localhost:3000"), cors.getAllowedOrigins());
        assertEquals(List.of("GET", "POST"), cors.getAllowedMethods());
        assertEquals(List.of("Authorization"), cors.getAllowedHeaders());
        assertEquals(List.of("X-Total-Count"), cors.getExposeHeaders());
        assertEquals(Duration.ofHours(1), cors.getMaxAge());

        properties.setResources(new NginxProperties.Resources());
        properties.setProxies(List.of());
        properties.setPool(new NginxProperties.Pool());
    }

}
