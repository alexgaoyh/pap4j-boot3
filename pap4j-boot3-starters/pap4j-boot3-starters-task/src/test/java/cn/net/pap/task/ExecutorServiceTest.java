package cn.net.pap.task;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 如果线程池创建工具中使用了 Executors.newFixedThreadPool(n) 或 Executors.newCachedThreadPool()，它们底层也是无界队列或无限线程，非常危险。
 * <p>
 * newFixedThreadPool和newSingleThreadExecutor，内部使用无界的LinkedBlockingQueue，允许请求队列无限增长，可能导致OOM（内存溢出）。
 * <p>
 * newCachedThreadPool和newScheduledThreadPool，允许创建的线程数量为Integer.MAX_VALUE，可能会创建大量线程，导致系统资源耗尽。
 */
public class ExecutorServiceTest {

    private ExecutorService executorService;

    @AfterEach
    void tearDown() {
        if (executorService != null && !executorService.isShutdown()) {
            // 测试结束后强制关闭线程池，清理资源
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("验证 newFixedThreadPool: 线程数固定，但任务队列是无界的 (引发 OOM: Java heap space)")
    void testFixedThreadPool_UnboundedQueueDanger() throws InterruptedException {
        // 创建一个只有 1 个线程的固定线程池
        executorService = Executors.newFixedThreadPool(1);
        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executorService;

        // 验证其底层队列剩余容量确实是 Integer.MAX_VALUE
        assertEquals(Integer.MAX_VALUE, threadPool.getQueue().remainingCapacity(), "队列容量应该是 Integer.MAX_VALUE，这就是无界队列的危险所在");

        // 1. 提交一个永远阻塞的任务，把唯一的那个核心线程占满
        CountDownLatch blockLatch = new CountDownLatch(1);
        executorService.execute(() -> {
            try {
                blockLatch.await(); // 模拟任务执行极其缓慢或卡死
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 2. 疯狂提交 100 万个任务
        int taskCount = 1_000_000;
        for (int i = 0; i < taskCount; i++) {
            // 这些任务因为没有空闲线程，会全部堆积到队列中
            // 如果任务对象很大，这里已经发生 OOM 了
            executorService.execute(() -> {
            });
        }

        // 3. 验证危险结果：队列中积压了整整 100 万个任务，且还能继续放
        assertEquals(taskCount, threadPool.getQueue().size(), "所有后续提交的任务都堆积在了内存队列中");
        assertTrue(threadPool.getQueue().remainingCapacity() > 0, "即使堆了 100万 个任务，队列还没满，内存迟早爆炸");

        // 释放阻塞的线程
        blockLatch.countDown();
    }

    @Test
    @DisplayName("验证 newCachedThreadPool: 队列无容量，但线程数无限暴涨 (引发 OOM: unable to create new native thread)")
    void testCachedThreadPool_InfiniteThreadsDanger() throws InterruptedException {
        // 创建缓存线程池
        executorService = Executors.newCachedThreadPool();
        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executorService;

        // 验证其最大线程数确实是 Integer.MAX_VALUE
        assertEquals(Integer.MAX_VALUE, threadPool.getMaximumPoolSize(), "最大线程数是 Integer.MAX_VALUE，这就是导致系统瘫痪的元凶");

        // 注意：这里我们不敢直接提交太多，否则测试机可能会因为瞬间创建几千个线程而卡死
        // 我们只提交 500 个任务来验证它确实会无节制地创建 500 个线程
        int dangerousTaskCount = 500;
        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch startLatch = new CountDownLatch(dangerousTaskCount);

        for (int i = 0; i < dangerousTaskCount; i++) {
            executorService.execute(() -> {
                startLatch.countDown(); // 记录线程已启动
                try {
                    blockLatch.await(); // 模拟 IO 阻塞（比如查数据库慢、调第三方API慢）
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // 等待这 500 个任务对应的线程全部启动
        startLatch.await(5, TimeUnit.SECONDS);

        // 3. 验证危险结果：虽然只有几百个任务，但它真的一口气创建了对应数量的真实系统线程！
        assertEquals(dangerousTaskCount, threadPool.getPoolSize(), "任务没有被排队，而是直接暴戾地创建了大量的系统线程");

        // 释放阻塞的线程
        blockLatch.countDown();
    }

}
