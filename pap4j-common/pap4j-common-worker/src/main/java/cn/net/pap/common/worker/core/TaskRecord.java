package cn.net.pap.common.worker.core;

/**
 * 订单板事实记录（不可变快照）。
 *
 * <p>作为任务状态的唯一事实源：所有状态流转都通过生成新记录完成，杜绝就地修改。
 * 记录不持有 {@link Throwable} 与结果句柄：异常以 {@link TaskError} 快照化。</p>
 *
 * @param id          任务全局唯一标识
 * @param state       当前任务状态
 * @param result      执行成功时的业务返回值（其余状态为 null）
 * @param error       失败时的异常快照（其余状态为 null）
 * @param completedAt 进入终态的时间戳（毫秒），非终态为 0
 */
public record TaskRecord(
        String id,
        TaskState state,
        Object result,
        TaskError error,
        long completedAt
) {

    /**
     * 创建初始记录（{@link TaskState#QUEUED}）。
     *
     * @param id 任务 ID
     * @return 等待执行的记录
     */
    public static TaskRecord queued(String id) {
        return new TaskRecord(id, TaskState.QUEUED, null, null, 0L);
    }

    /**
     * 是否已处于终态（{@link TaskState#SUCCEEDED} / {@link TaskState#FAILED}）。
     *
     * @return true 表示已终结
     */
    public boolean terminal() {
        return state == TaskState.SUCCEEDED || state == TaskState.FAILED;
    }

    /**
     * 认领执行，转为 {@link TaskState#RUNNING}。
     *
     * @return 新记录快照
     */
    public TaskRecord toRunning() {
        return new TaskRecord(id, TaskState.RUNNING, null, null, 0L);
    }

    /**
     * 执行成功，转为 {@link TaskState#SUCCEEDED} 并记录返回值。
     *
     * @param value 业务返回值
     * @return 新记录快照
     */
    public TaskRecord toSucceeded(Object value) {
        return new TaskRecord(id, TaskState.SUCCEEDED, value, null, System.currentTimeMillis());
    }

    /**
     * 执行失败（或被放弃），转为 {@link TaskState#FAILED}。
     *
     * @param t 失败原因（内部转为轻量快照存储）
     * @return 新记录快照
     */
    public TaskRecord toFailed(Throwable t) {
        return new TaskRecord(id, TaskState.FAILED, null, TaskError.of(t), System.currentTimeMillis());
    }

}
