package cn.net.pap.task.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * 重试 工具类
 */
public class RetryUtil {

    private static final Logger log = LoggerFactory.getLogger(RetryUtil.class);

    private static final int MAX_SUPPRESSED_EXCEPTIONS = 10;

    /**
     * 辅助方法：安全地往被压制异常列表中添加异常，防止重试次数过多时发生 OOM
     */
    private static void addSuppressedException(List<Exception> list, Exception e) {
        if (list.size() < MAX_SUPPRESSED_EXCEPTIONS) {
            list.add(e);
        } else if (list.size() > 1) {
            // 保留第 1 个异常（通常是最初始的根源错误），移除第 2 个（较旧的中间错误），并在末尾追加最新异常
            list.remove(1);
            list.add(e);
        }
    }

    /**
     * 泛型重试方法
     * <p>
     * 注意：该方法为【同步阻塞式重试】。底层使用 Thread.sleep 进行延迟等待，
     * 严禁在响应式编程环境（如 Spring WebFlux、Project Reactor 的 EventLoop 线程）中使用，
     * 否则会阻塞事件循环线程，导致严重的性能和吞吐量下降。
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
        List<Exception> suppressedExceptions = new ArrayList<>();

        // 在循环条件中增加对线程中断状态的检查，防止死循环
        while (retryCount < maxRetries && !Thread.currentThread().isInterrupted()) {
            try {
                T result = task.call();
                if (validator == null || validator.test(result)) {
                    return result;
                } else {
                    log.warn("Attempt {}/{} failed validation. Result: {}", retryCount + 1, maxRetries, result);
                    IllegalStateException validationException = new IllegalStateException("Validation failed. Result: " + result);
                    addSuppressedException(suppressedExceptions, validationException);
                    lastException = validationException;
                    retryCount++;
                    if (retryCount < maxRetries) {
                        waitBeforeRetry(delayMillis);
                    }
                }
            } catch (Exception e) {
                log.error("Attempt {}/{} failed with exception: {}", retryCount + 1, maxRetries, e.getMessage(), e);
                addSuppressedException(suppressedExceptions, e);
                lastException = e;
                retryCount++;
                if (retryCount < maxRetries) {
                    waitBeforeRetry(delayMillis);
                }
            }
        }

        // 如果是被中断导致退出循环，抛出中断异常
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Retry was interrupted.");
        }

        // 如果重试完毕仍然失败，抛出最后一次异常，并把之前的异常作为 suppressed 异常添加进去。
        // 注意：最后一次异常（第 N 次）作为主异常抛出，前 N-1 次的异常作为被压制（suppressed）异常添加。
        // 自身不能压制自身（Java 会抛出 IllegalArgumentException: Self-suppression not permitted），因此被压制的异常数量为 maxRetries - 1。
        if (lastException != null) {
            for (Exception suppressed : suppressedExceptions) {
                if (suppressed != lastException) {
                    lastException.addSuppressed(suppressed);
                }
            }
            throw lastException;
        }

        // 如果没有异常但是验证一直未通过，抛出异常
        throw new IllegalStateException("Retry failed after " + maxRetries + " attempts.");
    }

    /**
     * 泛型重试方法（支持特定异常的指数退避延迟与随机抖动 Jitter）
     * <p>
     * 注意：该方法为【同步阻塞式重试】。底层使用 Thread.sleep 进行延迟等待，
     * 严禁在响应式编程环境（如 Spring WebFlux、Project Reactor 的 EventLoop 线程）中使用，
     * 否则会严重阻塞事件循环，降低系统吞吐量。
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
    @SafeVarargs // 消除泛型可变参数带来的堆污染编译警告
    public static <T> T retryTWithBackoff(int maxRetries, long delayMillis, Callable<T> task, Predicate<T> validator,
                                          double backoffRatio, Class<? extends Exception>... backoffExceptions) throws Exception {
        int retryCount = 0;
        Exception lastException = null;
        long currentDelay = delayMillis;
        List<Exception> suppressedExceptions = new ArrayList<>();

        // 增加对线程中断状态的检查
        while (retryCount < maxRetries && !Thread.currentThread().isInterrupted()) {
            try {
                T result = task.call();
                if (validator == null || validator.test(result)) {
                    return result;
                } else {
                    log.warn("Attempt {}/{} failed validation. Result: {}", retryCount + 1, maxRetries, result);
                    IllegalStateException validationException = new IllegalStateException("Validation failed. Result: " + result);
                    addSuppressedException(suppressedExceptions, validationException);
                    lastException = validationException;
                    retryCount++;
                    if (retryCount < maxRetries) {
                        waitBeforeRetry(currentDelay);
                        // 对于验证失败的情况，也应用退避策略并引入 ±10% 的随机抖动（Jitter），防止高并发时发生惊群效应
                        double jitter = 0.9 + java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 0.2;
                        currentDelay = Math.max(1L, (long) (currentDelay * backoffRatio * jitter));
                    }
                }
            } catch (Exception e) {
                log.error("Attempt {}/{} failed with exception: {}", retryCount + 1, maxRetries, e.getMessage(), e);
                addSuppressedException(suppressedExceptions, e);
                lastException = e;
                retryCount++;

                if (retryCount < maxRetries) {
                    // 检查是否是需要应用退避延迟的异常类型
                    if (isBackoffException(e, backoffExceptions)) {
                        waitBeforeRetry(currentDelay);
                        // 应用退避策略并引入 ±10% 的随机抖动（Jitter），防止高并发时发生惊群效应
                        double jitter = 0.9 + java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 0.2;
                        currentDelay = Math.max(1L, (long) (currentDelay * backoffRatio * jitter));
                    } else {
                        // 非退避异常，使用基础固定延迟（不引入指数级增长，但可加入微量随机抖动打散并发）
                        double jitter = 0.9 + java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 0.2;
                        long currentBaseDelay = Math.max(1L, (long) (delayMillis * jitter));
                        waitBeforeRetry(currentBaseDelay);
                        currentDelay = delayMillis; // 重置为初始延迟
                    }
                }
            }
        }

        // 处理中断
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Retry was interrupted.");
        }

        // 如果重试完毕仍然失败，抛出最后一次异常，并把之前的异常作为 suppressed 异常添加进去。
        // 注意：最后一次异常（第 N 次）作为主异常抛出，前 N-1 次的异常作为被压制（suppressed）异常添加。
        // 自身不能压制自身（Java 会抛出 IllegalArgumentException: Self-suppression not permitted），因此被压制的异常数量为 maxRetries - 1。
        if (lastException != null) {
            for (Exception suppressed : suppressedExceptions) {
                if (suppressed != lastException) {
                    lastException.addSuppressed(suppressed);
                }
            }
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
     * 等待指定时间（阻塞式 Thread.sleep，注意不要在响应式 EventLoop 线程中调用）
     */
    private static void waitBeforeRetry(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            log.error("Thread interrupted during retry wait", e);
            Thread.currentThread().interrupt();
            // 如果在这里被中断，不应该默默吃掉异常。抛出运行时异常，让外层循环能立刻感知并退出。
            throw new IllegalStateException("Thread was interrupted during retry wait.", e);
        }
    }

}
