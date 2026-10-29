package cn.net.pap.common.worker.core;

/**
 * 任务生命周期状态枚举。
 *
 * <p>状态机：{@code QUEUED → RUNNING → SUCCEEDED / FAILED}，
 * 其中 {@code QUEUED} 可在未被执行的情况下直接转入 {@code FAILED}（排队区已满回滚、打烊放弃）。</p>
 */
public enum TaskState {

    /**
     * 已收单，等待厨师认领执行。
     */
    QUEUED,

    /**
     * 已被厨师认领，正在执行。
     */
    RUNNING,

    /**
     * 执行成功（终态）。
     */
    SUCCEEDED,

    /**
     * 执行失败或因故被放弃（终态）。
     */
    FAILED

}
