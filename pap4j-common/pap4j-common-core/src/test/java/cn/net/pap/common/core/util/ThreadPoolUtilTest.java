package cn.net.pap.common.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadPoolUtilTest {

    @Test
    @DisplayName("测试优雅停机 - 正常完成所有任务")
    void testNormalShutdown() throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean executed = new AtomicBoolean(false);

        executor.submit(() -> {
            executed.set(true);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        boolean terminated = ThreadPoolUtil.shutdownAndAwaitTermination(executor, 2, TimeUnit.SECONDS);

        assertTrue(terminated);
        assertTrue(executor.isTerminated());
        assertTrue(executed.get());
    }

    @Test
    @DisplayName("测试优雅停机 - 针对 null 线程池安全返回 true")
    void testNullPool() {
        assertTrue(ThreadPoolUtil.shutdownAndAwaitTermination(null));
        assertTrue(ThreadPoolUtil.shutdownAndAwaitTermination(null, 1, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("测试超时停机 - 超时后强制 shutdownNow 唤醒并终止")
    void testTimeoutShutdown() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                // 响应中断退出
                Thread.currentThread().interrupt();
            }
        });

        // 设定极短超时 100ms 触发两阶段强制终止
        boolean terminated = ThreadPoolUtil.shutdownAndAwaitTermination(executor, 100, TimeUnit.MILLISECONDS);
        assertTrue(terminated);
        assertTrue(executor.isTerminated());
    }

    @Test
    @DisplayName("测试默认超时重载方法")
    void testDefaultTimeout() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            // 短任务
        });

        boolean terminated = ThreadPoolUtil.shutdownAndAwaitTermination(executor);
        assertTrue(terminated);
        assertTrue(executor.isTerminated());
    }

}
