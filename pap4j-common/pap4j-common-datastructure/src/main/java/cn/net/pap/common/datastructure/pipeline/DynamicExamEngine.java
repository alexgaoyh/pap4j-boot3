package cn.net.pap.common.datastructure.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态考试引擎。
 * 负责驱动整个考试流水线的执行，支持节点的跳过、动态分发和挂起/恢复（断点续传）机制。
 */
public class DynamicExamEngine {

    // 处理器注册表
    private final List<TaskHandler> handlers = new ArrayList<>();

    /**
     * 开放 API：允许外部注册新的任务处理器 (插件化核心)
     */
    public void registerHandler(TaskHandler handler) {
        this.handlers.add(handler);
    }

    /**
     * 引擎的核心驱动循环
     *
     * @param pipeline    流水线节点定义数组
     * @param context     学生上下文
     * @param startCursor 从哪个索引开始执行 (用于断点续传)
     */
    public EngineResult run(List<ExamNode> pipeline, StudentContext context, int startCursor) {

        for (int cursor = startCursor; cursor < pipeline.size(); cursor++) {
            ExamNode currentNode = pipeline.get(cursor);

            // 1. 规则判断：是否需要跳过该节点
            if (shouldSkip(currentNode, context)) {
                System.out.println("[引擎跳过] 节点: " + currentNode.nodeId());
                continue; // 直接将游标推向下一个节点
            }

            // 2. 动态路由分发：遍历注册表寻找能处理该节点的 Handler
            TaskAction action = dispatchTask(currentNode, context);

            // 3. 状态控制：如果任务要求挂起，引擎立即保存游标并退出
            if (action == TaskAction.SUSPEND) {
                System.out.println("[引擎挂起] 在节点: " + currentNode.nodeId() + "，保存游标: " + cursor);
                return EngineResult.suspended(cursor);
            }
        }

        return EngineResult.completed();
    }

    /**
     * 重构后的动态分发器：彻底消除硬编码
     */
    private TaskAction dispatchTask(ExamNode node, StudentContext context) {
        for (TaskHandler handler : handlers) {
            // 谁声明支持接管，就交给谁执行
            if (handler.supports(node.taskType())) {
                return handler.execute(node, context);
            }
        }
        throw new IllegalArgumentException("未找到可用的处理器，未知的节点类型: " + node.taskType());
    }

    // 模拟轻量级的条件求值
    private boolean shouldSkip(ExamNode node, StudentContext context) {
        if ("has_exemption".equals(node.skipCondition())) {
            // 如果上下文里标记了该学生有“免考特权”，则跳过
            return Boolean.TRUE.equals(context.data().get("has_exemption"));
        }
        return false;
    }

}