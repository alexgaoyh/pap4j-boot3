package cn.net.pap.common.datastructure.sync;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SyncTest {

    public static String get_sync(String seqName, int length) {
        synchronized (seqName) {
            return String.valueOf(length);
        }
    }

    @Test
    public void testSynchronizedBehavior() throws InterruptedException, ExecutionException {
        int numThreads = 10000;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        String seqName = "testSync";
        CountDownLatch latch = new CountDownLatch(numThreads);
        List<Future<String>> futures1 = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            int finalI = i;
            CountDownLatch finalLatch = latch;
            futures1.add(executorService.submit(() -> {
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
        executorService.shutdown();
    }

}
