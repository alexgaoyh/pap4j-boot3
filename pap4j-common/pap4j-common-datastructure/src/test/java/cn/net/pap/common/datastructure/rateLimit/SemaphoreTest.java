package cn.net.pap.common.datastructure.rateLimit;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Semaphore 模拟限流
 */
public class SemaphoreTest {

    private static final Logger log = LoggerFactory.getLogger(SemaphoreTest.class);

    // @Test
    public void testSemaphore() throws Exception {

        // 限流 3
        java.util.concurrent.Semaphore rateLimitSemaphore = new java.util.concurrent.Semaphore(3);

        // 模拟总共 5 个线程
        for(int i=0;i < 5;i++){
            // 模拟一个请求进来的先后顺序
            Thread.sleep(1000);

            new Thread(() -> {
                try {
                    // 获得许可
                    rateLimitSemaphore.acquire();
                    log.info("{} borrow one， left {} limits", Thread.currentThread().getName(), rateLimitSemaphore.availablePermits());
                    // 模拟实际的业务执行
                    Thread.sleep(new Random().nextInt(5000));
                    // 释放许可
                    rateLimitSemaphore.release();
                    log.info("{} return one， left {} limits", Thread.currentThread().getName(), rateLimitSemaphore.availablePermits());
                } catch (InterruptedException e) {
                    log.error("Interrupted", e);
                    Thread.currentThread().interrupt();
                }
            }).start();

        }

        Thread.sleep(10000);
    }

}
