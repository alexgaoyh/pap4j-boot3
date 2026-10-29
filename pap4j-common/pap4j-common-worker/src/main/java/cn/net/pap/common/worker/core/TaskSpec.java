package cn.net.pap.common.worker.core;

import java.util.concurrent.Callable;

/**
 * 任务规格描述（不可变单据）。
 *
 * <p>{@code id} 用作<b>在途去重键</b>：同一 id 处于在途状态（{@link TaskState#QUEUED} / {@link TaskState#RUNNING}）时，
 * 重复提交不会被再次执行；一旦该 id 进入终态或记录被容量清理，再次提交会作为新任务重新执行。
 * 因此 {@code id} <b>不是持久幂等键</b>，如需“永不重复执行”请由业务侧用外部唯一键保证。</p>
 *
 * @param <T>    任务返回值类型
 * @param id     任务全局唯一标识（建议使用业务唯一键，如订单号、消息 ID）
 * @param action 待执行的业务逻辑闭包（可返回值、可抛异常）
 */
public record TaskSpec<T>(String id, Callable<T> action) {

    /**
     * 创建任务规格。
     *
     * @param <T>    返回值类型
     * @param id     任务全局唯一标识
     * @param action 业务逻辑闭包
     * @return 任务规格
     */
    public static <T> TaskSpec<T> of(String id, Callable<T> action) {
        return new TaskSpec<>(id, action);
    }

    /**
     * 兼容无返回值的任务（返回值类型固定为 {@link Void}）。
     *
     * @param id       任务全局唯一标识
     * @param runnable 无返回值的业务逻辑
     * @return 任务规格，其返回值为 Void
     */
    public static TaskSpec<Void> of(String id, Runnable runnable) {
        return new TaskSpec<>(id, () -> {
            runnable.run();
            return null;
        });
    }

}
