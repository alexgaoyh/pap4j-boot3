package cn.net.pap.common.datastructure.executor;

import cn.net.pap.common.datastructure.cpu.CpuInfoUtil;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

}
