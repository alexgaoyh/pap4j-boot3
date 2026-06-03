package cn.net.pap.example.webflux;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestConstructor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PdfWebFluxControllerLoadTest {

    private static final Logger log = LoggerFactory.getLogger(PdfWebFluxControllerLoadTest.class);
    private static final String TEST_FILE_NAME = "test-pressure.pdf";
    private static final int CONCURRENCY = 1000; // 并发请求数量

    private final int port;

    public PdfWebFluxControllerLoadTest(@LocalServerPort int port) {
        this.port = port;
    }

    @BeforeAll
    static void setupDummyFile() throws IOException {
        // 在 D 盘根目录生成一个临时测试文件，模拟真实环境
        Path basePath = Path.of(System.getProperty("java.io.tmpdir")).normalize();
        Path testFile = basePath.resolve(TEST_FILE_NAME);
        if (!Files.exists(testFile)) {
            // 写入 1MB 的无意义数据模拟 PDF
            Files.write(testFile, new byte[1024 * 1024]);
        }
    }

    @AfterAll
    static void cleanupDummyFile() throws IOException {
        Path basePath = Path.of(System.getProperty("java.io.tmpdir")).normalize();
        Path testFile = basePath.resolve(TEST_FILE_NAME);
        Files.deleteIfExists(testFile);
        log.info("=== 测试结束，已清理临时测试文件: {} ===", testFile);
    }

    @Test
    void comparePerformance() {
        WebClient client = WebClient.builder()
                .baseUrl("http://localhost:" + port)
                // 增大客户端连接池，避免客户端成为瓶颈
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        log.info("=== 开始压测: view (同步阻塞 I/O) ===");
        runLoadTest(client, "/pdf/view/" + TEST_FILE_NAME);

        // 睡眠一段时间，让 GC 运行，Netty 线程释放
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
        }

        log.info("=== 开始压测: view2 (异步隔离 I/O) ===");
        runLoadTest(client, "/pdf/view2/" + TEST_FILE_NAME);
    }

    private void runLoadTest(WebClient client, String uri) {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        Instant start = Instant.now();

        // 构造 1000 个并发请求流
        Flux.range(1, CONCURRENCY)
                .flatMap(i -> client.get()
                        .uri(uri)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .doOnSuccess(bytes -> successCount.incrementAndGet())
                        .onErrorResume(e -> {
                            errorCount.incrementAndGet();
                            return Mono.empty();
                        })
                )
                // 等待所有请求完成
                .blockLast(Duration.ofMinutes(1));

        Instant finish = Instant.now();
        long timeElapsed = Duration.between(start, finish).toMillis();

        log.info("测试接口: {}", uri);
        log.info("总请求数: {}", CONCURRENCY);
        log.info("成功次数: {}", successCount.get());
        log.info("失败次数: {}", errorCount.get());
        log.info("总耗时: {} ms", timeElapsed);
        log.info("吞吐量: {} req/sec\n", (CONCURRENCY * 1000L) / Math.max(1, timeElapsed));
    }
}