package cn.net.pap.common.datastructure.pipeline;

/**
 * 考试流水线节点定义。
 *
 * @param nodeId        节点标识符
 * @param taskType      任务类型，用于路由到对应的处理器
 * @param skipCondition 跳过条件，如果满足则引擎会跳过此节点
 */
public record ExamNode(String nodeId, String taskType, String skipCondition) {
}