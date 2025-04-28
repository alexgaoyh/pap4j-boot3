package cn.net.pap.common.datastructure.executor;

import cn.net.pap.common.datastructure.cpu.CpuInfoUtil;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.stream.IntStream;

public class CompletableFutureTest {

    private static final ExecutorService executor = Executors.newFixedThreadPool(6);

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
