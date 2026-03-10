package cn.net.pap.example.proguard;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ConcurrentLockControllerTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentLockControllerTest.class);

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    // @Test
    public void testConcurrentLock() throws Exception {
        final String orderId = "test_order_123";
        final int threadCount = 50;
        final int requestPerThread = 10;

        ExecutorService executor = new ThreadPoolExecutor(
                50,
                50,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(500),
                r -> new Thread(r, "countdownlatch-test-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        // 创建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = "{\"proguardName\":\"alexgaoyh\"}";
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            // 提交并发任务
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    latch.countDown();
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    for (int j = 0; j < requestPerThread; j++) {
                        try {
                            // 对应的接口可以增加 Thread.sleep(50);， 从而来验证锁的情况。
                            ResponseEntity<String> response = restTemplate.postForEntity(
                                    "http://localhost:" + port + "/concurrentLock/test1/" + orderId,
                                    entity,
                                    String.class
                            );

                            if (response.getStatusCode().is2xxSuccessful()) {
                                successCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }));
            }

            // 等待所有任务完成
            for (Future<?> future : futures) {
                future.get();
            }

            // 验证结果
            ResponseEntity<String> result = restTemplate.postForEntity(
                    "http://localhost:" + port + "/concurrentLock/test1/" + orderId,
                    entity,
                    String.class
            );

            System.out.println("最终结果: " + result.getBody());
            System.out.println("成功请求次数: " + successCount.get());
        } finally {
            executor.shutdown();
            try {
                // 等待 2 秒让未完成的任务结束
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    // 超时后强制关闭，这会向所有池中线程发送 Interrupt 信号
                    log.warn("部分线程池任务未在 2 秒内结束，强制关闭");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭线程池时被中断", e);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

    }
}
