package cn.net.pap.common.datastructure.executor;

import cn.net.pap.common.datastructure.cpu.CpuInfoUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.stream.IntStream;

/**
 * 两个单元测试虽然任务提交方式不同（parallelStream vs for循环），但由于最终都把任务提交到同一个固定大小的线程池执行，因此实际并发模型和执行效率几乎完全一致。
 */
public class CompletableFutureTest {

    private static final Logger log = LoggerFactory.getLogger(CompletableFutureTest.class);

    private static final ExecutorService executor = new ThreadPoolExecutor(
                6,
                6,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(10),
        r -> new Thread(r, "completable-future-executor"),
                new ThreadPoolExecutor.AbortPolicy()
    );

    @AfterAll
    public static void shutdown() {
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

    public static String doWork(String input) {
        try {
            return doWorkInner(input).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static CompletableFuture<String> doWorkInner(String input) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> originalDoWork(input), executor);
        return future;
    }

    private static String originalDoWork(String input) {
        try {
            int randomNumber = (int) (1000 + Math.random() * 1001);
            Thread.sleep(randomNumber);
            return input.toUpperCase() + " : " + randomNumber + " : " + CpuInfoUtil.getCurrentCpuCore();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // @Test
    public void test() {
        long start = System.currentTimeMillis();
        IntStream.rangeClosed(1, 10).parallel().forEach(value -> {
            System.out.println(doWork(String.valueOf(value)));
        });
        long end = System.currentTimeMillis();
        System.out.println("-----------------------------------");
        System.out.println(end - start);
    }

    public static <T> void executeTask(Callable<T> task, TaskCallback<T> callback, CountDownLatch latch) {
        // CompletableFuture 的并发执行能力取决于其绑定的 Executor 线程池大小，而不是任务提交方式（如 parallelStream 或循环提交），因此在并发设计中应重点关注线程池配置。
        CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor).thenAccept(result -> {
            callback.onComplete(result);
        }).thenRun(latch::countDown);
    }

    // @Test
    public void test2() throws InterruptedException {
        long start = System.currentTimeMillis();

        CountDownLatch latch = new CountDownLatch(10);
        for (int i = 0; i < 10; i++) {
            int finalI = i;
            executeTask(() -> {
                int randomNumber = (int) (1000 + Math.random() * 1001);
                Thread.sleep(randomNumber);
                return (finalI + "").toUpperCase() + " : " + randomNumber + " : " + CpuInfoUtil.getCurrentCpuCore();
            }, result -> {
                System.out.println(result);
            }, latch);
        }
        latch.await();

        long end = System.currentTimeMillis();
        System.out.println("-----------------------------------");
        System.out.println(end - start);
    }

}
