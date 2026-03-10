package cn.net.pap.example.proguard;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CountDownLanchTest {

    private static final Logger log = LoggerFactory.getLogger(CountDownLanchTest.class);

    //@Test
    public void service() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://127.0.0.1:30000/batch";

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                10,
                10,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                r -> new Thread(r, "countdownlatch-test-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        CountDownLatch latch = new CountDownLatch(10);

        try {
            for (int i = 1; i <= 10; i++) {
                executor.execute(() -> {
                    try {
                        String result = restTemplate.getForObject(url, String.class);
                        System.out.println("result = " + result);
                    } catch (Exception e) {
                        System.err.println("HTTP 请求异常: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
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
