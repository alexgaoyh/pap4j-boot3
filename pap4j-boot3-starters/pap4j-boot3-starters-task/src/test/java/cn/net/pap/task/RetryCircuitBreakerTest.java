package cn.net.pap.task;

import cn.net.pap.task.retry.RetryCircuitBreaker;

import cn.net.pap.task.retry.exception.RetryCircuitBreakerException;
import cn.net.pap.task.retry.exception.enums.PapRetryErrorEnum;
import org.junit.Test;

/**
 * 重试滑动窗口断路器
 */
public class RetryCircuitBreakerTest {

    /**
     * 外部定义 重试滑动窗口断路器
     */
    private static final RetryCircuitBreaker retryCircuitBreaker = new RetryCircuitBreaker(3,1000, 5, 60000, 10000);

    @Test
    public void test() {

        try {
            String result = retryCircuitBreaker.executeWithRetry(() -> {
                boolean success = someExternalService();
                if (success) {
                    return "Success";
                } else {
                    throw new RetryCircuitBreakerException(PapRetryErrorEnum.RETRY_FINAL_FAILURE);
                }
            });

            System.out.println(result);
        } catch (Exception e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    public static boolean someExternalService() {
        return Math.random() > 0.8;
    }
}
