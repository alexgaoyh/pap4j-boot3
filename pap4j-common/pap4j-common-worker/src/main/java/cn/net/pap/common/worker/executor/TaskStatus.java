package cn.net.pap.common.worker.executor;

/**
 * 任务状态枚举
 */
public enum TaskStatus {

    /**
     * 等待执行
     */
    WAITING,

    /**
     * 正在执行
     */
    RUNNING,

    /**
     * 执行成功
     */
    SUCCESS,

    /**
     * 执行失败
     */
    FAILED

}