package cn.net.pap.common.datastructure.pipeline.workflow;

/**
 * 工作流执行状态
 */
public enum WorkflowStatus {

    /**
     * 运行中
     */
    RUNNING,

    /**
     * 成功完成
     */
    SUCCESS,

    /**
     * 业务主动中断（如风控未通过）
     */
    INTERRUPTED,

    /**
     * 系统异常失败
     */
    FAILED

}
