package cn.net.pap.common.datastructure.pipeline.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流上下文
 * 负责在节点间传递数据、记录执行状态和支持断点续传
 */
public class WorkflowContext {

    private static final Logger log = LoggerFactory.getLogger(WorkflowContext.class);

    /**
     * 工作流数据
     * 使用 ConcurrentHashMap 保证多线程环境下的安全性，指定初始容量 16
     */
    private final Map<String, Object> data = new ConcurrentHashMap<>(16);

    /**
     * 工作流执行状态
     * 使用 volatile 保证多线程环境下的状态可见性，默认运行中
     */
    private volatile WorkflowStatus status = WorkflowStatus.RUNNING;

    /**
     * 提示信息
     */
    private volatile String message = "success";

    /**
     * 记录发生中断或异常的节点名称
     */
    private volatile String errorNode = null;

    /**
     * 记录已经成功执行的节点名称，用于断点续传机制
     */
    private final java.util.Set<String> executedNodes = ConcurrentHashMap.newKeySet();

    /**
     * 存入数据（解决 ConcurrentHashMap 无法存入 null value 的问题）
     *
     * @param key   键
     * @param value 值
     */
    public void put(String key, Object value) {
        if (key == null) return;
        if (value == null) {
            data.remove(key); // 业务中传入 null 通常代表清除或无值，平滑处理
        } else {
            data.put(key, value);
        }
    }

    /**
     * 获取数据（无类型推断）
     *
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * 类型安全的获取数据方法，避免业务代码中到处强制类型转换
     *
     * @param key   键
     * @param clazz 目标类型 class
     * @param <T>   目标类型
     * @return 转换后的值，如果不存在或类型不匹配则返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        Object val = data.get(key);
        if (val != null && clazz.isInstance(val)) {
            return (T) val;
        }
        return null;
    }

    /**
     * 核心控制方法：中断工作流并设置提示信息
     *
     * @param nodeName 触发中断的节点名称
     * @param reason   中断原因
     */
    public void interrupt(String nodeName, String reason) {
        this.status = WorkflowStatus.INTERRUPTED;
        this.message = reason;
        this.errorNode = nodeName;
        log.warn("工作流在节点 [{}] 触发主动中断，原因: {}", nodeName, reason);
    }

    /**
     * 标记流程成功完成
     */
    public void markSuccess() {
        this.status = WorkflowStatus.SUCCESS;
        this.message = "success";
    }

    /**
     * 标记流程执行失败（系统异常等）
     *
     * @param nodeName 发生异常的节点名称
     * @param reason   失败原因
     */
    public void markFailed(String nodeName, String reason) {
        this.status = WorkflowStatus.FAILED;
        this.message = reason;
        this.errorNode = nodeName;
    }

    /**
     * 获取工作流执行状态
     *
     * @return 工作流执行状态
     */
    public WorkflowStatus getStatus() {
        return status;
    }

    /**
     * 设置工作流执行状态
     *
     * @param status 工作流执行状态
     */
    public void setStatus(WorkflowStatus status) {
        this.status = status;
    }

    /**
     * 获取提示信息
     *
     * @return 提示信息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置提示信息
     *
     * @param message 提示信息
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 获取发生中断或异常的节点名称
     *
     * @return 节点名称
     */
    public String getErrorNode() {
        return errorNode;
    }

    /**
     * 设置发生中断或异常的节点名称
     *
     * @param errorNode 节点名称
     */
    public void setErrorNode(String errorNode) {
        this.errorNode = errorNode;
    }

    /**
     * 获取已经成功执行的节点名称集合
     *
     * @return 已执行的节点名称集合
     */
    public java.util.Set<String> getExecutedNodes() {
        return executedNodes;
    }

    /**
     * 批量设置已经成功执行的节点名称
     *
     * @param executedNodes 已执行的节点名称集合
     */
    public void setExecutedNodes(java.util.Set<String> executedNodes) {
        if (executedNodes != null) {
            this.executedNodes.addAll(executedNodes);
        }
    }

    /**
     * 获取工作流内部存储的数据 Map
     *
     * @return 数据 Map
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * 批量放入数据
     *
     * @param data 数据 Map
     */
    public void setData(Map<String, Object> data) {
        if (data != null) {
            this.data.putAll(data);
        }
    }

    /**
     * 判断工作流是否可以继续执行
     *
     * @return 如果状态为运行中则返回 true
     */
    public boolean canContinue() {
        return this.status == WorkflowStatus.RUNNING;
    }

    @Override
    public String toString() {
        return "WorkflowContext{status=" + status + ", errorNode='" + errorNode + "', message='" + message + "', data=" + data + '}';
    }

}
