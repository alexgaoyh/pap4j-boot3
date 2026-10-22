package cn.net.pap.common.core.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 带前缀命名的线程工厂。
 * <p>
 * 基于纯 JDK 实现，用于替换各业务模块中重复出现的匿名 {@link ThreadFactory}。
 * 线程名格式为 {@code 前缀 + 自增序号}，便于日志排查与线程监控（如 jstack 中直接识别线程归属）。
 * 通过 {@link AtomicInteger} 保证并发创建线程时序号唯一。
 * </p>
 *
 * <pre>{@code
 * ExecutorService pool = new ThreadPoolExecutor(
 *         1, 1, 0L, TimeUnit.MILLISECONDS,
 *         new LinkedBlockingQueue<>(1000),
 *         new NamedThreadFactory("task-consumer-"),
 *         new ThreadPoolExecutor.CallerRunsPolicy());
 * }</pre>
 */
public final class NamedThreadFactory implements ThreadFactory {

    private final String prefix;

    private final AtomicInteger counter;

    /**
     * 从 1 开始编号。
     *
     * @param prefix 线程名前缀，通常以 "-" 结尾，例如 {@code "worker-"}
     */
    public NamedThreadFactory(String prefix) {
        this(prefix, 1);
    }

    /**
     * @param prefix  线程名前缀，通常以 "-" 结尾
     * @param startAt 起始序号（包含）
     */
    public NamedThreadFactory(String prefix, int startAt) {
        if (prefix == null) {
            throw new IllegalArgumentException("prefix must not be null");
        }
        this.prefix = prefix;
        this.counter = new AtomicInteger(startAt);
    }

    @Override
    public Thread newThread(Runnable r) {
        return new Thread(r, prefix + counter.getAndIncrement());
    }

}
