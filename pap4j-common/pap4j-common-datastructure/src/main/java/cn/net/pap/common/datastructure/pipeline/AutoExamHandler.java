package cn.net.pap.common.datastructure.pipeline;

/**
 * 自动监考处理器。
 * 处理以 "auto_exam" 前缀开头的任务，模拟机器自动监考和打分过程。
 */
public class AutoExamHandler implements TaskHandler {

    @Override
    public boolean supports(String taskType) {
        // 这里演示多级前缀匹配：既能处理 "auto_exam"，也能处理未来的 "auto_exam:math"
        return taskType != null && taskType.startsWith("auto_exam");
    }

    @Override
    public TaskAction execute(ExamNode node, StudentContext context) {
        System.out.println("[执行] 机器自动监考中... 科目: " + node.nodeId());
        context.data().put(node.nodeId() + "_score", 85); // 模拟打分
        return TaskAction.CONTINUE;
    }
}