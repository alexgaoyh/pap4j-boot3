package cn.net.pap.common.worker.core;

import java.util.concurrent.Callable;

/**
 * 任务规格描述记录类（不可变单据）。
 *
 * @param <T>            任务执行返回值的泛型类型
 * @param id             任务全局唯一标识
 * @param action         真正待执行的业务逻辑闭包（Callable，支持泛型返回值与异常抛出）
 * @param maxRetries     最大重试尝试次数（包含首次执行，如 1 代表不重试，3 代表最多重试 2 次）
 * @param initialDelayMs 首次重试的基础退避延迟时间（毫秒）
 */
public record TaskSpec<T>(
        String id,
        Callable<T> action,
        int maxRetries,
        long initialDelayMs
) {

    /**
     * 默认构造辅助方法，默认最多重试 3 次，初始退避延迟 1000 毫秒。
     *
     * @param id     任务全局唯一标识
     * @param action 业务逻辑闭包
     */
    public TaskSpec(String id, Callable<T> action) {
        this(id, action, 3, 1000L);
    }

    /**
     * 兼容无返回值的 Runnable 任务辅助工厂方法。
     *
     * @param id       任务全局唯一标识
     * @param runnable 无返回值的业务逻辑
     * @return TaskSpec 实例，其返回值为 Void
     */
    public static TaskSpec<Void> of(String id, Runnable runnable) {
        return new TaskSpec<>(id, () -> {
            runnable.run();
            return null;
        });
    }

}
