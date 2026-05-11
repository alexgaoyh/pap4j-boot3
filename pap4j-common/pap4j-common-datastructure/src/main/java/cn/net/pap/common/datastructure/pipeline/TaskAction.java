package cn.net.pap.common.datastructure.pipeline;

/**
 * 任务执行动作。
 * 指示引擎在当前节点执行完毕后的下一步行为。
 */
public enum TaskAction {
    /**
     * 继续执行下一个节点
     */
    CONTINUE,
    /**
     * 挂起当前流程，等待外部触发
     */
    SUSPEND
}