package cn.net.pap.common.datastructure.pipeline;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 学生考试上下文。
 * 存储考试流程中的状态数据，生命周期跨越整个流水线的执行过程。
 *
 * @param instanceId 流程实例 ID
 * @param studentId  学生 ID
 * @param data       流程执行过程中产生的动态数据
 */
public record StudentContext(String instanceId, String studentId, Map<String, Object> data) {
    public StudentContext(String instanceId, String studentId) {
        this(instanceId, studentId, new ConcurrentHashMap<>());
    }
}