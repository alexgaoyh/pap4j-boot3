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

    /**
     * 泛型重试方法（支持特定异常的指数退避延迟）
     *
     * @param maxRetries  最大重试次数
     * @param delayMillis 每次重试前的延迟（毫秒）
     * @param task        任务，返回 T 类型
     * @param validator   验证返回值是否正确，如果返回 true 则任务完成，不再重试
     * @param backoffRatio 退避比例，当遇到特定异常时，延迟时间会按此比例指数增长
     * @param backoffExceptions 需要应用退避延迟的异常类型列表
     * @param <T>         返回值类型
     * @return 任务最终返回值
     * @throws Exception 如果重试完毕仍失败，将抛出最后一次异常
     */
    public static <T> T retryTWithBackoff(int maxRetries, long delayMillis, Callable<T> task, Predicate<T> validator,
                                          double backoffRatio, Class<? extends Exception>... backoffExceptions) throws Exception {
        int retryCount = 0;
        Exception lastException = null;
        long currentDelay = delayMillis;

        while (retryCount < maxRetries) {
            try {
                T result = task.call();
                if (validator == null || validator.test(result)) {
                    return result;
                } else {
                    retryCount++;
                    if (retryCount < maxRetries) {
                        waitBeforeRetry(currentDelay);
                        // 对于验证失败的情况，也应用退避策略
                        currentDelay = (long) (currentDelay * backoffRatio);
                    }
                }
            } catch (Exception e) {
                lastException = e;
                retryCount++;

                if (retryCount < maxRetries) {
                    // 检查是否是需要应用退避延迟的异常类型
                    if (isBackoffException(e, backoffExceptions)) {
                        waitBeforeRetry(currentDelay);
                        // 应用退避策略：延迟时间按比例增长
                        currentDelay = (long) (currentDelay * backoffRatio);
                    } else {
                        // 非退避异常，使用基础延迟
                        waitBeforeRetry(delayMillis);
                        currentDelay = delayMillis; // 重置为初始延迟
                    }
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

    /**
     * 检查异常是否属于需要应用退避延迟的异常类型
     */
    private static boolean isBackoffException(Exception e, Class<? extends Exception>[] backoffExceptions) {
        if (backoffExceptions == null || backoffExceptions.length == 0) {
            return false;
        }

        for (Class<? extends Exception> exceptionClass : backoffExceptions) {
            if (exceptionClass.isInstance(e)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 等待指定时间（可重写此方法以支持中断等特性）
     */
    private static void waitBeforeRetry(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
