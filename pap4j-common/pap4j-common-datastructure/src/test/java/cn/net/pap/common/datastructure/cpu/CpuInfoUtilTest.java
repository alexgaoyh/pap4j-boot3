package cn.net.pap.common.datastructure.cpu;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CpuInfoUtilTest {

    private static final Logger log = LoggerFactory.getLogger(CpuInfoUtilTest.class);

    // @Test
    public void cpu() throws InterruptedException {
        int poolSize = Runtime.getRuntime().availableProcessors();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("cpu-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();

        ConcurrentHashMap<Integer, Integer> cpuCoreMap = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(poolSize);

        try {
            for (int i = 0; i < poolSize; i++) {
                executor.execute(() -> {
                    try {
                        int cpuCore = CpuInfoUtil.getCurrentCpuCore();
                        cpuCoreMap.put(Thread.currentThread().hashCode(), cpuCore);

                        Thread.sleep(100);

                        int newCpuCore = CpuInfoUtil.getCurrentCpuCore();
                        if (cpuCore >= 0 && newCpuCore >= 0) {
                            log.info(String.format("线程 %s 初始在CPU %d, 后来在CPU %d", Thread.currentThread().getName(), cpuCore, newCpuCore));
                        }
                    } catch (InterruptedException e) {
                        log.error("Thread sleep interrupted", e);
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.error("Error getting CPU info", e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(2, TimeUnit.SECONDS);

            log.info("\nCPU核心分配情况:");
            cpuCoreMap.forEach((threadHash, cpuCore) -> {
                log.info(String.format("线程 %d 运行在CPU核心: %d", threadHash, cpuCore));
            });

            assertTrue(cpuCoreMap.size() > 0, "应该有线程获取到CPU核心信息");
        } finally {
            executor.shutdown();
            try {
                // 2. 等待 2 秒让未完成的任务结束
                // 注意：ThreadPoolTaskExecutor 包装了原生 pool，这里调用其内部的 awaitTermination
                if (!executor.getThreadPoolExecutor().awaitTermination(2, TimeUnit.SECONDS)) {
                    // 3. 超时后强制关闭
                    log.warn("部分线程池任务未在 2 秒内结束，尝试强制关闭");

                    // ThreadPoolTaskExecutor 没有直接的 shutdownNow，需要从底层原生池调用
                    executor.getThreadPoolExecutor().shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭线程池时被中断", e);
                executor.getThreadPoolExecutor().shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

}
