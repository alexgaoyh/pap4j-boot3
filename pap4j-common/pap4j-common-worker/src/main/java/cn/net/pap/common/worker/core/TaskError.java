package cn.net.pap.common.worker.core;

import cn.net.pap.common.core.stackTrace.StackTraceUtils;

/**
 * 轻量异常快照，替代订单板长期持有原始 {@link Throwable}。
 *
 * <p>只保留异常类型、消息与栈顶若干帧（含有限层级的 cause 链），既保留排障所需信息，
 * 又避免在记录保留期内把整条堆栈与捕获现场的引用链一起滞留，造成内存放大。</p>
 *
 * @param type       异常类型全限定名
 * @param message    异常消息
 * @param stackTrace 已渲染的异常摘要（类型 + 消息 + 栈顶帧 + cause 链）
 */
public record TaskError(String type, String message, String stackTrace) {

    /**
     * 构建异常快照。
     *
     * @param t 原始异常，允许为 null
     * @return 异常快照（t 为 null 时返回空快照）
     */
    public static TaskError of(Throwable t) {
        if (t == null) {
            return new TaskError("", "", "");
        }
        return new TaskError(t.getClass().getName(), t.getMessage(), StackTraceUtils.getFullStackTrace(t, 16, 4));
    }

}
