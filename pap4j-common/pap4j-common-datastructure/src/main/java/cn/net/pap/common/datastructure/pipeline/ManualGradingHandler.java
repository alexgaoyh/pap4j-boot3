package cn.net.pap.common.datastructure.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 人工阅卷处理器。
 * 处理 "manual_grading" 类型的任务。如果缺少人工打分结果，则挂起流程等待外部介入；如果存在打分结果，则继续前进。
 */
public class ManualGradingHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(ManualGradingHandler.class);

    @Override
    public boolean supports(String taskType) {
        return "manual_grading".equals(taskType);
    }

    @Override
    public TaskAction execute(ExamNode node, StudentContext context) {
        String scoreKey = node.nodeId() + "_score";

        // 状态自检
        if (context.data().containsKey(scoreKey)) {
            log.info("[检查通过] 发现人工打分结果 ({}={})，当前人工节点闭环，继续前进。", scoreKey, context.data().get(scoreKey));
            return TaskAction.CONTINUE;
        }

        log.info("[执行等待] 缺少人工打分结果 ({})，准备通知外部介入...", scoreKey);
        return TaskAction.SUSPEND;
    }
}