package cn.net.pap.example.nginx.handler;

import cn.net.pap.example.nginx.config.NginxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StaticResourceHandlerTest {

    @TempDir
    Path tempDir;

    private WebTestClient client;

    private StaticResourceHandler handler;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(tempDir.resolve("index.html"), "INDEX");
        Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub").resolve("index.html"), "SUB");
        Files.writeString(tempDir.resolve("app.css"), "body{color:red}");
        NginxProperties.Location location = new NginxProperties.Location();
        location.setPath("/");
        location.setRoot(tempDir.toString());
        RouterFunction<ServerResponse> router = RouterFunctions.route(
                RequestPredicates.all(), new StaticResourceHandler(location));
        this.handler = new StaticResourceHandler(location);
        this.client = WebTestClient.bindToRouterFunction(router).build();
    }

    @Test
    void servesIndexWithCharset() {
        client.get().uri("/").exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches(HttpHeaders.CONTENT_TYPE, "text/html.*charset=UTF-8")
                .expectBody(String.class).isEqualTo("INDEX");
    }

    @Test
    void servesDirectoryIndex() {
        client.get().uri("/sub/").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("SUB");
    }

    @Test
    void fallsBackToSpaIndex() {
        client.get().uri("/some/spa/route").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("INDEX");
    }

    @Test
    void servesCssContentType() {
        client.get().uri("/app.css").exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches(HttpHeaders.CONTENT_TYPE, "text/css.*");
    }

    @Test
    void rejectsNonReadableMethod() {
        client.post().uri("/").exchange()
                .expectStatus().isEqualTo(HttpStatus.METHOD_NOT_ALLOWED)
                .expectHeader().valueEquals(HttpHeaders.ALLOW, "GET, HEAD");
    }

    @Test
    void supportsConditionalRequest() {
        String lastModified = client.get().uri("/").exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseHeaders().getFirst(HttpHeaders.LAST_MODIFIED);
        assertNotNull(lastModified);
        client.get().uri("/").header(HttpHeaders.IF_MODIFIED_SINCE, lastModified).exchange()
                .expectStatus().isNotModified();
    }

    @Test
    void supportsSingleRange() {
        client.get().uri("/app.css").header(HttpHeaders.RANGE, "bytes=0-3").exchange()
                .expectStatus().isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 0-3/15")
                .expectBody(String.class).isEqualTo("body");
    }

    @Test
    void supportsWeakEtagConditionalRequest() {
        String etag = client.get().uri("/app.css").exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseHeaders().getETag();
        assertNotNull(etag);
        client.get().uri("/app.css").header(HttpHeaders.IF_NONE_MATCH, "W/" + etag).exchange()
                .expectStatus().isNotModified();
    }

    @Test
    void ignoresRangeWhenIfRangeMismatch() {
        client.get().uri("/app.css")
                .header(HttpHeaders.RANGE, "bytes=0-3")
                .header(HttpHeaders.IF_RANGE, "\"not-matching\"")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("body{color:red}");
    }

    @Test
    void doesNotAddCharsetToSvg() throws IOException {
        Files.writeString(tempDir.resolve("icon.svg"), "<svg/>");
        client.get().uri("/icon.svg").exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches(HttpHeaders.CONTENT_TYPE, "(?i)image/svg\\+xml");
    }

    @Test
    void blocksPathTraversal() {
        MockServerRequest request = MockServerRequest.builder()
                .method(HttpMethod.GET)
                .uri(URI.create("/..%2fapp.css"))
                .build();
        ServerResponse response = handler.handle(request).block();
        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode());
    }

    @Test
    void supportsEtagConditionalRequest() {
        String etag = client.get().uri("/app.css").exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseHeaders().getETag();
        assertNotNull(etag);
        client.get().uri("/app.css").header(HttpHeaders.IF_NONE_MATCH, etag).exchange()
                .expectStatus().isNotModified()
                .expectHeader().valueEquals(HttpHeaders.ETAG, etag);
    }

    @Test
    void rejectsUnsatisfiableRangeWith416() {
        client.get().uri("/app.css").header(HttpHeaders.RANGE, "bytes=100-200").exchange()
                .expectStatus().isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes */15");
    }

    @Test
    void redirectsDirectoryWithoutTrailingSlash() {
        client.get().uri("/sub").exchange()
                .expectStatus().isEqualTo(HttpStatus.MOVED_PERMANENTLY)
                .expectHeader().valueEquals(HttpHeaders.LOCATION, "/sub/");
    }

    @Test
    void servesModernMimeTypes() throws IOException {
        Files.writeString(tempDir.resolve("module.mjs"), "console.log('hi');");
        client.get().uri("/module.mjs").exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches(HttpHeaders.CONTENT_TYPE, "text/javascript.*");
    }

    @Test
    void returns404WhenSpaFallbackIsDisabled() {
        NginxProperties.Location location = new NginxProperties.Location();
        location.setPath("/");
        location.setRoot(tempDir.toString());
        location.setSpaFallback("");
        StaticResourceHandler noSpaHandler = new StaticResourceHandler(location);
        RouterFunction<ServerResponse> router = RouterFunctions.route(RequestPredicates.all(), noSpaHandler);
        WebTestClient noSpaClient = WebTestClient.bindToRouterFunction(router).build();

        noSpaClient.get().uri("/nonexistent-page").exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void supportsIfRangeWithMatchingDate() {
        String lastModified = client.get().uri("/app.css").exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseHeaders().getFirst(HttpHeaders.LAST_MODIFIED);
        assertNotNull(lastModified);
        client.get().uri("/app.css")
                .header(HttpHeaders.RANGE, "bytes=0-3")
                .header(HttpHeaders.IF_RANGE, lastModified)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 0-3/15")
                .expectBody(String.class).isEqualTo("body");
    }

    @Test
    void ignoresRangeWhenIfRangeDateMismatch() {
        client.get().uri("/app.css")
                .header(HttpHeaders.RANGE, "bytes=0-3")
                .header(HttpHeaders.IF_RANGE, "Thu, 01 Jan 2099 00:00:00 GMT")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("body{color:red}");
    }

}
