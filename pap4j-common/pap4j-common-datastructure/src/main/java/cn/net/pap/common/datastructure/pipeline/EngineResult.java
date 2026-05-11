package cn.net.pap.common.datastructure.pipeline;

/**
 * 引擎执行结果。
 * 记录引擎运行的最终状态，包括是否全部完成、挂起时的游标位置以及相关信息。
 *
 * @param isCompleted   是否全部完成
 * @param stoppedCursor 挂起时的游标位置
 * @param message       结果描述信息
 */
public record EngineResult(boolean isCompleted, int stoppedCursor, String message) {
    public static EngineResult completed() {
        return new EngineResult(true, -1, "全部流程执行完毕");
    }

    public static EngineResult suspended(int cursor) {
        return new EngineResult(false, cursor, "流程已挂起，等待人工介入");
    }
}