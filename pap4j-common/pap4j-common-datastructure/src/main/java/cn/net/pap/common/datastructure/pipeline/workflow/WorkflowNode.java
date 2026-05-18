package cn.net.pap.common.datastructure.pipeline.workflow;

/**
 * 工作流执行节点接口
 */
public interface WorkflowNode {

    /**
     * @return 节点名称，用于日志打印和异常定位
     */
    String name();

    /**
     * @return 节点执行的超时时间（毫秒）。
     *         默认返回 -1 表示不限制超时，节点将在当前引擎主线程中同步执行。
     *         若大于 0，引擎将把该节点投递到独立的线程池中异步执行，并强制进行超时熔断。
     *         【注意】开启超时控制后，节点逻辑会在异步线程中运行，如果有依赖 ThreadLocal 的操作（如获取当前用户、MDC 日志等）需要自行透传！
     */
    default long timeoutMillis() {
        return -1L;
    }

    /**
     * 节点执行逻辑
     * <p>
     * 【架构警告：幂等性黑洞】业务方必须自行保证节点内部执行的幂等性！
     * 工作流引擎的“断点续传”只能做到节点级别的跳过（仅在节点完全成功时记录）。
     * 若节点内部包含多个步骤（如：1.调用外部RPC扣减 2.写本地日志），在RPC成功但写日志抛出异常时，
     * 引擎会认为该节点执行失败（FAILED），不会计入已执行集合。
     * 在下一次重试时，该节点将被重新完整执行，极易导致外部 RPC 被重复调用（重复扣减）。
     * 因此，业务逻辑中必须结合全局业务流水号做前置防重判断，引擎无法代替业务解决内部“半成功”问题。
     * </p>
     *
     * @param context 当前工作流上下文
     * @throws Exception 允许抛出任何受检异常（如 SQLException, IOException），由引擎统一处理
     */
    void execute(WorkflowContext context) throws Exception;

}
