package cn.net.pap.example.spring.ai.tool;

/**
 * 智能体工具标记接口。
 * <p>
 * 凡是实现了该接口的 Spring Bean，均会被自动发现并注册到 Agent 智能体的工具上下文中。
 */
public interface AgentTool {

    /**
     * 响应式上下文中用于实时收集智能体活动轨迹（思考/调用/观察）的 Sink Key。
     */
    String ACTIVITY_SINK_KEY = "AGENT_ACTIVITY_SINK";

    /**
     * 响应式上下文中用于挂载请求级原文证据账本的 Context Key
     */
    String EVIDENCE_LEDGER_KEY = "AGENT_EVIDENCE_LEDGER";

    /**
     * 响应式上下文中用于携带本次会话累计工具调用次数的 Context Key。
     * <p>
     * 纯观测用途：不参与任何限流或熔断，仅用于在轨迹中标记「第几次工具调用」，
     * 便于统计一轮会话究竟调用了多少次工具、哪类工具占大头。
     */
    String TOOL_CALL_COUNTER_KEY = "AGENT_TOOL_CALL_COUNTER";

}
