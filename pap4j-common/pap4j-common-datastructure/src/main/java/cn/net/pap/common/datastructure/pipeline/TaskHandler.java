package cn.net.pap.common.datastructure.pipeline;

/**
 * 任务处理器策略接口
 */
public interface TaskHandler {

    /**
     * 判断当前处理器是否支持处理该类型的任务
     * 支持多级路由匹配，例如 return taskType.startsWith("exam:auto");
     *
     * @param taskType 任务类型标识
     * @return true 表示支持接管
     */
    boolean supports(String taskType);

    /**
     * 执行具体的任务逻辑
     *
     * @param node    当前节点定义
     * @param context 流程上下文
     * @return 告诉引擎下一步的动作 (继续或挂起)
     */
    TaskAction execute(ExamNode node, StudentContext context);
}