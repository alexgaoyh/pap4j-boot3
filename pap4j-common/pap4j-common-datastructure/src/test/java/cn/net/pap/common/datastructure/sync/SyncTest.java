package cn.net.pap.common.datastructure.sync;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SyncTest {

    private static final Logger log = LoggerFactory.getLogger(SyncTest.class);

    public static String get_sync(String seqName, int length) {
        synchronized (seqName) {
            return String.valueOf(length);
        }
    }

    /**
     * 验证 synchronized(String) 在多线程环境下的同步行为，以及多个线程是否能按顺序安全访问同一个锁对象。
     * 在多线程高并发环境下，使用同一个 String 作为锁对象可以实现线程互斥，从而保证访问共享资源的安全性，同时也暴露了 String 锁在不同对象引用情况下可能失效的风险。
     * @throws InterruptedException
     * @throws ExecutionException
     */
    @Test
    public void testSynchronizedBehavior() throws InterruptedException, ExecutionException {
        int numThreads = 10000;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                numThreads,
                numThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(100),
                r -> new Thread(r, "dynamic-task-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        try {
            String seqName = "testSync";
            CountDownLatch latch = new CountDownLatch(numThreads);
            List<Future<String>> futures1 = new ArrayList<>();
            for (int i = 0; i < numThreads; i++) {
                int finalI = i;
                CountDownLatch finalLatch = latch;
                futures1.add(executor.submit(() -> {
                    try {
                        return get_sync(seqName, finalI);
                    } finally {
                        finalLatch.countDown();
                    }
                }));
            }
            latch.await();
            for (int i = 0; i < numThreads; i++) {
                assertEquals(String.valueOf(i), futures1.get(i).get());
            }
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
