package cn.net.pap.common.core.retry;

import cn.net.pap.common.core.retry.exception.RetryCircuitBreakerException;
import cn.net.pap.common.core.retry.exception.enums.PapRetryErrorEnum;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 重试滑动窗口断路器测试
 */
public class RetryCircuitBreakerTest {

    private static final Logger log = LoggerFactory.getLogger(RetryCircuitBreakerTest.class);

    /**
     * 外部定义 重试滑动窗口断路器
     */
    private static final RetryCircuitBreaker retryCircuitBreaker = new RetryCircuitBreaker(3, 1000, 5, 60000, 10000);

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

            log.info("{}", result);
        } catch (Exception e) {
            log.error("Failed: ", e);
        }
    }

    public static boolean someExternalService() {
        return Math.random() > 0.8;
    }
}
