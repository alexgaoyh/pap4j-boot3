package cn.net.pap.task.retry;

import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * 重试 工具类
 */
public class RetryUtil {

    /**
     * 重试 工具类
     *
     * @param maxRetries  最大重试次数·
     * @param delayMillis 延迟时长
     * @param task        任务  () -> performOperation()    任务会抛出异常。
     * @param
     * @return
     * @throws Exception
     */
    public static Boolean retry(int maxRetries, long delayMillis, Callable<Boolean> task) throws Exception {
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                Boolean call = task.call();
                if (call) {
                    return call;
                } else {
                    retryCount++;

                    if (retryCount < maxRetries) {
                        waitBeforeRetry(delayMillis);
                    } else {
                        return false;
                    }
                }
            } catch (Exception e) {
                return false;
            }
        }

        throw new IllegalStateException("Should not reach here");
    }


    /**
     * 泛型重试方法
     *
     * @param maxRetries  最大重试次数
     * @param delayMillis 每次重试前的延迟（毫秒）
     * @param task        任务，返回 T 类型
     * @param validator   验证返回值是否正确，如果返回 true 则任务完成，不再重试
     * @param <T>         返回值类型
     * @return 任务最终返回值
     * @throws Exception 如果重试完毕仍失败，将抛出最后一次异常
     */
    public static <T> T retryT(int maxRetries, long delayMillis, Callable<T> task, Predicate<T> validator) throws Exception {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < maxRetries) {
            try {
                T result = task.call();
                if (validator == null || validator.test(result)) {
                    return result;
                } else {
                    retryCount++;
                    if (retryCount < maxRetries) {
                        waitBeforeRetry(delayMillis);
                    }
                }
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                if (retryCount < maxRetries) {
                    waitBeforeRetry(delayMillis);
                }
            }
        }

        // 如果重试完毕仍然失败，抛出最后一次异常
        if (lastException != null) {
            throw lastException;
        }

        // 如果没有异常但是验证一直未通过，抛出异常
        throw new IllegalStateException("Retry failed after " + maxRetries + " attempts.");
    }


    private static void waitBeforeRetry(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
