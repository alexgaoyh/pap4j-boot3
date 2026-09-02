package cn.net.pap.task;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;

public class ThreadPoolTaskExecutorTest {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolTaskExecutorTest.class);

    private final int corePoolSize = Runtime.getRuntime().availableProcessors();
    private final int maxPoolSize = corePoolSize * 2;
    private final int queueCapacity = 100;
    private final int keepAliveSeconds = 60;

    private volatile boolean isMonitoring = true;

    @Test
    public void multiTest() throws Exception {
        long startTime = System.currentTimeMillis();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        Thread monitorThread = null;
        try {
            executor.setCorePoolSize(corePoolSize);
            executor.setMaxPoolSize(maxPoolSize);
            executor.setQueueCapacity(queueCapacity);
            executor.setKeepAliveSeconds(keepAliveSeconds);
            executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
            executor.initialize();

            List<List<Integer>> tasks = List.of(List.of(1000, 2000, 3000), List.of(2000, 2000, 2000), List.of(3000, 3000, 1000), List.of(1000, 2000, 3000), List.of(2000, 2000, 2000), List.of(3000, 3000, 1000), List.of(1000, 2000, 3000), List.of(2000, 2000, 2000), List.of(3000, 3000, 1000));

            CountDownLatch latch = new CountDownLatch(tasks.size());

            monitorThread = new Thread(() -> {
                while (isMonitoring) {
                    try {
                        printThreadPoolStatsAndLatchStats(executor, latch);
                        java.util.concurrent.TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        log.error("监控线程休眠被中断", e);
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            monitorThread.setDaemon(true);
            monitorThread.start();

            for (List<Integer> delays : tasks) {
                executor.submit(() -> {
                    processTask(delays, latch);
                });
            }

            latch.await();
            // 再打印一次
            printThreadPoolStatsAndLatchStats(executor, latch);
            long duration = System.currentTimeMillis() - startTime;
            log.info("所有任务完成，总耗时: {}ms", duration);
        } finally {
            isMonitoring = false;
            if (monitorThread != null) {
                monitorThread.interrupt();
                monitorThread.join();
            }
            executor.shutdown();
            ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
            if (!threadPoolExecutor.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                threadPoolExecutor.shutdownNow();
            }
        }
    }

    private Boolean processTask(List<Integer> delays, CountDownLatch latch) {
        try {
            for (int delay : delays) {
                Thread.sleep(delay);
            }
            return true;
        } catch (InterruptedException e) {
            log.error("任务执行过程中线程被中断", e);
            Thread.currentThread().interrupt();
            return false;
        } finally {
            latch.countDown();
        }
    }

    private void printThreadPoolStatsAndLatchStats(ThreadPoolTaskExecutor executor, CountDownLatch latch) {
        ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
        log.info("[线程池状态] 核心线程: {}, 活动线程: {}, 最大线程: {}, 队列大小: {}/{}, 完成任务: {}. [CountDownLatch状态] 当前剩余任务: {}", threadPoolExecutor.getCorePoolSize(), threadPoolExecutor.getActiveCount(), threadPoolExecutor.getMaximumPoolSize(), threadPoolExecutor.getQueue().size(), queueCapacity, threadPoolExecutor.getCompletedTaskCount(), latch.getCount());
    }

}
