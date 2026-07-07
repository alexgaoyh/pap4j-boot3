package cn.net.pap.task;

import cn.net.pap.task.retry.RetryUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RetryUtilTest {

    private static final Logger log = LoggerFactory.getLogger(RetryUtilTest.class);

    @Test
    void testTaskSucceedsFirstTry() throws Exception {
        String result = RetryUtil.retryT(3, 100, () -> "success", r -> "success".equals(r));
        assertEquals("success", result);
    }

    @Test
    void testTaskFailsOnceThenSucceeds() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        String result = RetryUtil.retryT(3, 100, () -> {
            if (count.getAndIncrement() < 1) {
                throw new RuntimeException("fail once");
            }
            return "ok";
        }, r -> "ok".equals(r));
        assertEquals("ok", result);
        assertEquals(2, count.get());
    }

    @Test
    void testTaskAlwaysFailsWithException() {
        AtomicInteger count = new AtomicInteger(0);
        Exception exception = assertThrows(RuntimeException.class, () ->
                RetryUtil.retryT(3, 50, () -> {
                    count.incrementAndGet();
                    throw new RuntimeException("always fail");
                }, r -> true)
        );
        assertEquals("always fail", exception.getMessage());
        assertEquals(3, count.get());

        // Verify Dimension 2: check that intermediate exceptions are added as suppressed exceptions
        // 注意：第 3 次抛出的异常作为主异常抛出，前 2 次被吞掉的异常作为被压制（suppressed）异常。
        // 由于异常不能自我压制，所以被压制的异常中不含自己，总数量是 maxRetries - 1 = 2。
        Throwable[] suppressed = exception.getSuppressed();
        assertEquals(2, suppressed.length);
        assertEquals("always fail", suppressed[0].getMessage());
        assertEquals("always fail", suppressed[1].getMessage());
    }

    @Test
    void testTaskReturnsInvalidThenValid() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        String result = RetryUtil.retryT(5, 50, () -> {
            int attempt = count.getAndIncrement();
            return attempt < 3 ? "invalid" : "valid";
        }, r -> "valid".equals(r));
        assertEquals("valid", result);
        assertEquals(4, count.get());
    }

    @Test
    void testTaskReturnsAlwaysInvalid() {
        AtomicInteger count = new AtomicInteger(0);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                RetryUtil.retryT(3, 50, () -> {
                    count.incrementAndGet();
                    return "bad";
                }, r -> "good".equals(r))
        );
        assertEquals(3, count.get());
        assertTrue(exception.getMessage().contains("Validation failed. Result: bad"));

        // 注意：第 3 次校验失败生成的异常作为主异常抛出，前 2 次校验失败异常作为被压制（suppressed）异常。
        // 由于异常不能自我压制，所以被压制的异常中不含自己，总数量是 maxRetries - 1 = 2。
        Throwable[] suppressed = exception.getSuppressed();
        assertEquals(2, suppressed.length);
        assertTrue(suppressed[0].getMessage().contains("Validation failed. Result: bad"));
        assertTrue(suppressed[1].getMessage().contains("Validation failed. Result: bad"));
    }

    @Test
    void testValidatorIsNull() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        String result = RetryUtil.retryT(3, 50, () -> {
            count.incrementAndGet();
            return "any";
        }, null);
        assertEquals("any", result);
        assertEquals(1, count.get());
    }

    @Test
    void testInterruptedDuringSleep() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                r -> new Thread(r, "retry-interruption-test")
        );

        try {
            java.util.concurrent.Future<?> future = executor.submit(() -> {
                try {
                    RetryUtil.retryT(3, 5000, () -> {
                        count.incrementAndGet();
                        throw new RuntimeException("fail");
                    }, r -> true);
                } catch (Exception e) {
                    log.error("Error in test thread", e);
                }
            });

            Thread.sleep(100);
            future.cancel(true); // This will interrupt the thread
            
            // 等待任务结束
            try {
                future.get(1, TimeUnit.SECONDS);
            } catch (Exception ignored) {}

        } finally {
            executor.shutdownNow();
        }

        // 中断后至少执行了第一次尝试
        assertTrue(count.get() >= 1);
    }

    @Test
    public void testTTaskFailsOnceThenSucceeds() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        String result = RetryUtil.retryTWithBackoff(3, 100, () -> {
            log.info("{}", System.currentTimeMillis());
            if (count.getAndIncrement() < 1) {
                throw new RuntimeException("fail once");
            }
            return "ok";
        }, r -> "ok".equals(r), 1.5, RuntimeException.class);

        assertEquals("ok", result);
        assertEquals(2, count.get());
    }

    @Test
    public void testTTaskAlwaysFailsWithBackoffException() {
        AtomicInteger count = new AtomicInteger(0);
        Exception exception = assertThrows(RuntimeException.class, () ->
                RetryUtil.retryTWithBackoff(5, 50, () -> {
                    log.info("{}", System.currentTimeMillis());
                    count.incrementAndGet();
                    throw new RuntimeException("always fail");
                }, r -> true, 2.0, RuntimeException.class)
        );

        assertEquals("always fail", exception.getMessage());
        assertEquals(5, count.get());

        // Verify Dimension 2: check that intermediate exceptions are added as suppressed exceptions
        Throwable[] suppressed = exception.getSuppressed();
        assertEquals(4, suppressed.length);
        for (int i = 0; i < 4; i++) {
            assertEquals("always fail", suppressed[i].getMessage());
        }
    }

    @Test
    void testTaskAlwaysFailsWithManyRetriesDoesNotOOM() {
        AtomicInteger count = new AtomicInteger(0);
        Exception exception = assertThrows(RuntimeException.class, () ->
                RetryUtil.retryT(50, 1, () -> {
                    int c = count.incrementAndGet();
                    throw new RuntimeException("fail " + c);
                }, r -> true)
        );
        assertEquals("fail 50", exception.getMessage());
        assertEquals(50, count.get());

        // Verify OOM Defense: check that intermediate exceptions are capped at 10 (1 final thrown, 9 suppressed)
        Throwable[] suppressed = exception.getSuppressed();
        assertEquals(9, suppressed.length); // 10 total exceptions retained (1 main + 9 suppressed)
        
        // The first suppressed exception must be "fail 1" (our first root cause)
        assertEquals("fail 1", suppressed[0].getMessage());
        
        // The remaining 8 suppressed exceptions must be the latest failures: fail 42, fail 43, ..., fail 49
        assertEquals("fail 42", suppressed[1].getMessage());
        assertEquals("fail 49", suppressed[8].getMessage());
    }

    private static class RetryUtilCustomException extends Exception {
        public RetryUtilCustomException(String message) {
            super(message);
        }
    }

    @Test
    void testTaskFailsWithCustomCheckedException() {
        AtomicInteger count = new AtomicInteger(0);
        RetryUtilCustomException exception = assertThrows(RetryUtilCustomException.class, () ->
                RetryUtil.retryT(3, 10, () -> {
                    count.incrementAndGet();
                    throw new RetryUtilCustomException("custom error");
                }, r -> true)
        );
        assertEquals("custom error", exception.getMessage());
        assertEquals(3, count.get());
        assertEquals(2, exception.getSuppressed().length);
        assertTrue(exception.getSuppressed()[0] instanceof RetryUtilCustomException);
    }

    @Test
    void testTaskFailsWithCustomExceptionBackoff() {
        AtomicInteger count = new AtomicInteger(0);
        RetryUtilCustomException exception = assertThrows(RetryUtilCustomException.class, () ->
                RetryUtil.retryTWithBackoff(3, 10, () -> {
                    count.incrementAndGet();
                    throw new RetryUtilCustomException("custom backoff error");
                }, r -> true, 2.0, RetryUtilCustomException.class)
        );
        assertEquals("custom backoff error", exception.getMessage());
        assertEquals(3, count.get());
        assertEquals(2, exception.getSuppressed().length);
        assertTrue(exception.getSuppressed()[0] instanceof RetryUtilCustomException);
    }

}
