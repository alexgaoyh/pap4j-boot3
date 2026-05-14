package cn.net.pap.common.datastructure.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 证书颁发处理器。
 * 处理 "issue_certificate" 类型的任务，自动为学生颁发证书。
 */
public class CertificateHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(CertificateHandler.class);

    @Override
    public boolean supports(String taskType) {
        return "issue_certificate".equals(taskType);
    }

    @Override
    public TaskAction execute(ExamNode node, StudentContext context) {
        log.info("[执行] 恭喜！系统已自动为您颁发证书: {}", node.nodeId());
        return TaskAction.CONTINUE;
    }
}