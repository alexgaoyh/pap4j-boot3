package cn.net.pap.common.datastructure.cpu;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CpuInfoUtilTest {

    // @Test
    public void cpu() throws InterruptedException {
        int poolSize = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        ConcurrentHashMap<Integer, Integer> cpuCoreMap = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(poolSize);

        for (int i = 0; i < poolSize; i++) {
            executor.submit(() -> {
                try {
                    int cpuCore = CpuInfoUtil.getCurrentCpuCore();
                    cpuCoreMap.put(Thread.currentThread().hashCode(), cpuCore);

                    Thread.sleep(100);

                    int newCpuCore = CpuInfoUtil.getCurrentCpuCore();
                    if (cpuCore >= 0 && newCpuCore >= 0) {
                        System.out.printf("线程 %s 初始在CPU %d, 后来在CPU %d%n", Thread.currentThread().getName(), cpuCore, newCpuCore);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(2, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("\nCPU核心分配情况:");
        cpuCoreMap.forEach((threadHash, cpuCore) -> {
            System.out.printf("线程 %d 运行在CPU核心: %d%n", threadHash, cpuCore);
        });

        assertTrue(cpuCoreMap.size() > 0, "应该有线程获取到CPU核心信息");
    }

}
