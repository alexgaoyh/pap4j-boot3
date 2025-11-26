package cn.net.pap.task;

import cn.net.pap.task.retry.RetryUtil;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RetryUtilTest {

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
        assertTrue(exception.getMessage().contains("Retry failed"));
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
        Thread testThread = new Thread(() -> {
            try {
                RetryUtil.retryT(3, 5000, () -> {
                    count.incrementAndGet();
                    throw new RuntimeException("fail");
                }, r -> true);
            } catch (Exception e) {
                // e.printStackTrace();
                // ignored
            }
        });
        testThread.start();
        Thread.sleep(100);
        testThread.interrupt();
        testThread.join();
        // 中断后至少执行了第一次尝试
        assertTrue(count.get() >= 1);
    }

    @Test
    public void testTTaskFailsOnceThenSucceeds() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        String result = RetryUtil.retryTWithBackoff(3, 1000, () -> {
            System.out.println(System.currentTimeMillis());
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
                RetryUtil.retryTWithBackoff(5, 1000, () -> {
                    System.out.println(System.currentTimeMillis());
                    count.incrementAndGet();
                    throw new RuntimeException("always fail");
                }, r -> true, 2.0, RuntimeException.class)
        );

        assertEquals("always fail", exception.getMessage());
        assertEquals(5, count.get());
    }

}
