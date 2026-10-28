package cn.net.pap.common.worker.core;

/**
 * 订单板事实不可变记录类。
 * 作为任务状态的唯一真相，通过版本号（version）与原子方法生成新快照，彻底杜绝状态篡改。
 *
 * @param id          任务全局唯一标识
 * @param state       当前任务状态
 * @param attempt     当前尝试执行的序号（从 1 开始）
 * @param nextRetryAt 下次重试时间戳（毫秒），非等待重试状态下为 0
 * @param version     版本号，每次状态变更严格自增
 * @param leaseOwner  持有租约的厨师线程名称
 * @param leaseUntil  租约有效期截止时间戳（毫秒）
 * @param result      业务执行成功返回的结果数据（若有）
 * @param error       最近一次执行失败的异常对象（若有）
 */
public record TaskRecord(
        String id,
        TaskState state,
        int attempt,
        long nextRetryAt,
        long version,
        String leaseOwner,
        long leaseUntil,
        Object result,
        Throwable error
) {

    /**
     * 创建预占位（RESERVED）初始记录。
     *
     * @param id 任务 ID
     * @return 预占位记录
     */
    public static TaskRecord reserved(String id) {
        return new TaskRecord(id, TaskState.RESERVED, 1, 0L, 1L, null, 0L, null, null);
    }

    /**
     * 转换至入队状态（QUEUED）。
     *
     * @return 新记录快照
     */
    public TaskRecord toQueued() {
        return new TaskRecord(id, TaskState.QUEUED, attempt, 0L, version + 1, null, 0L, null, null);
    }

    /**
     * 厨师盖章认领租约，进入执行状态（RUNNING）。
     *
     * @param workerName 认领该任务的厨师线程名
     * @param until      租约到期时间戳
     * @return 新记录快照
     */
    public TaskRecord withLease(String workerName, long until) {
        return new TaskRecord(id, TaskState.RUNNING, attempt, 0L, version + 1, workerName, until, null, null);
    }

    /**
     * 转换至等待重试状态（WAITING_RETRY）。
     *
     * @param nextAttempt 下一次尝试序号
     * @param retryAt     计划重新入队执行的时间戳
     * @param t           本次失败的异常对象
     * @return 新记录快照
     */
    public TaskRecord toWaitingRetry(int nextAttempt, long retryAt, Throwable t) {
        return new TaskRecord(id, TaskState.WAITING_RETRY, nextAttempt, retryAt, version + 1, null, 0L, null, t);
    }

    /**
     * 转换至执行成功终态（SUCCEEDED），并记录返回结果。
     *
     * @param result 业务执行返回值
     * @return 新记录快照
     */
    public TaskRecord toSucceeded(Object result) {
        return new TaskRecord(id, TaskState.SUCCEEDED, attempt, 0L, version + 1, leaseOwner, 0L, result, null);
    }

    /**
     * 转换至彻底失败终态（FAILED）。
     *
     * @param t 失败异常
     * @return 新记录快照
     */
    public TaskRecord toFailed(Throwable t) {
        return new TaskRecord(id, TaskState.FAILED, attempt, 0L, version + 1, leaseOwner, 0L, null, t);
    }

}
