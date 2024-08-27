package cn.net.pap.task.retry;

import cn.net.pap.task.retry.exception.RetryCircuitBreakerException;
import cn.net.pap.task.retry.exception.enums.PapRetryErrorEnum;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Callable;

/**
 * 重试滑动窗口断路器
 */
public class RetryCircuitBreaker {

    /**
     * 如果任务失败，重试执行任务的最大次数
     */
    private final int maxRetries;

    /**
     * 连续重试尝试之间的延迟（以毫秒为单位）。
     */
    private final long retryDelayMillis;

    /**
     * 在指定时间窗口内触发断路器打开的故障次数。
     * 如果failureThreshold设置为5，则如果在windowsSizeMillis定义的时间窗口内发生5次故障，断路器将打开。
     */
    private final int failureThreshold;

    /**
     * 用于跟踪故障的滑动时间窗口的大小（以毫秒为单位）。这定义了计算故障的时间段，以确定是否已达到故障阈值。
     */
    private final long windowSizeMillis;

    /**
     * 断路器在转换到允许有限请求的半断开状态之前保持断开的持续时间（以毫秒为单位）。
     */
    private final long openStateDelayMillis;

    /**
     * 失败的时间戳队列
     */
    private final Queue<Long> failureTimestamps;

    /**
     * 状态
     */
    private CircuitState state;

    /**
     * 时间戳
     */
    private long lastStateChangeTime;

    public RetryCircuitBreaker(int maxRetries, long retryDelayMillis, int failureThreshold, long windowSizeMillis, long openStateDelayMillis) {
        this.maxRetries = maxRetries;
        this.retryDelayMillis = retryDelayMillis;
        this.failureThreshold = failureThreshold;
        this.windowSizeMillis = windowSizeMillis;
        this.openStateDelayMillis = openStateDelayMillis;
        this.failureTimestamps = new LinkedList<>();
        this.state = CircuitState.CLOSED;
        this.lastStateChangeTime = System.currentTimeMillis();
    }

    public <T> T executeWithRetry(Callable<T> task) throws Exception {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            System.out.println(this.toString());
            if (allowRequest()) {
                try {
                    T result = task.call();
                    recordSuccess();
                    return result;
                } catch (Exception e) {
                    lastException = e;
                    recordFailure();
                    attempt++;
                    if (attempt < maxRetries) {
                        Thread.sleep(retryDelayMillis);
                    }
                }
            } else {
                throw new RetryCircuitBreakerException(PapRetryErrorEnum.RETRY_CIRCUIT_OPEN);
            }
        }
        throw lastException;
    }

    private synchronized boolean allowRequest() {
        long currentTime = System.currentTimeMillis();

        if (state == CircuitState.OPEN) {
            if (currentTime - lastStateChangeTime > openStateDelayMillis) {
                state = CircuitState.HALF_OPEN;
                lastStateChangeTime = currentTime;
            } else {
                return false;
            }
        }

        return true;
    }

    private synchronized void recordFailure() {
        long currentTime = System.currentTimeMillis();
        failureTimestamps.add(currentTime);

        while (!failureTimestamps.isEmpty() && currentTime - failureTimestamps.peek() > windowSizeMillis) {
            failureTimestamps.poll();
        }

        if (failureTimestamps.size() >= failureThreshold) {
            state = CircuitState.OPEN;
            lastStateChangeTime = currentTime;
        }
    }

    private synchronized void recordSuccess() {
        if (state == CircuitState.HALF_OPEN) {
            state = CircuitState.CLOSED;
        }
    }

    private enum CircuitState {
        CLOSED, HALF_OPEN, OPEN
    }

    @Override
    public String toString() {
        return "RetryCircuitBreaker{" +
                "maxRetries=" + maxRetries +
                ", retryDelayMillis=" + retryDelayMillis +
                ", failureThreshold=" + failureThreshold +
                ", windowSizeMillis=" + windowSizeMillis +
                ", openStateDelayMillis=" + openStateDelayMillis +
                ", failureTimestamps=" + failureTimestamps +
                ", state=" + state +
                ", lastStateChangeTime=" + lastStateChangeTime +
                '}';
    }
}

