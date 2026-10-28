package cn.net.pap.common.worker.core;

/**
 * 任务生命周期状态枚举。
 */
public enum TaskState {

    /**
     * 预占位（提交时在订单板原子占位，尚未推入排队区）。
     */
    RESERVED,

    /**
     * 已在排队区排队，等待厨师认领。
     */
    QUEUED,

    /**
     * 正在执行（厨师已成功盖章认领租约）。
     */
    RUNNING,

    /**
     * 等待重试（任务执行失败，已被移入闹钟架等待延迟重投）。
     */
    WAITING_RETRY,

    /**
     * 执行成功完成。
     */
    SUCCEEDED,

    /**
     * 执行彻底失败（达到最大重试上限或非重试异常）。
     */
    FAILED

}
